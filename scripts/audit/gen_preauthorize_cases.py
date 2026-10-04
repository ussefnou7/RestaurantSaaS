#!/usr/bin/env python3
"""Generate guard_reversion cases for every @PreAuthorize on a controller.

Each case comments the annotation out, which drops the endpoint's authorization
entirely -- the strongest available reversion. The method the annotation guards is
recorded in the case so the report can name the endpoint, not just a line number.

    scripts/audit/gen_preauthorize_cases.py > scripts/audit/cases/authz.json
    scripts/audit/gen_preauthorize_cases.py --module hr > scripts/audit/cases/authz_hr.json
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(REPO, "src", "main", "java")

MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping")
SIGNATURE = re.compile(r"\b(public|protected)\s[\w<>,?\[\]\s]+\s(\w+)\s*\(")


def base_path(lines: list[str]) -> str:
    """The controller's class-level @RequestMapping path, if it declares one."""
    for line in lines:
        if "@RequestMapping" in line and (m := re.search(r'"([^"]*)"', line)):
            return m.group(1)
        if " class " in line:
            break
    return ""


def endpoint_of(lines: list[str], i: int, base: str) -> str:
    """The HTTP verb+path and method name the annotation at line i guards.

    The mapping annotation sits ABOVE @PreAuthorize in this codebase and the method
    signature below it, so this walks both ways. A class-level @PreAuthorize has a
    `class` declaration below instead of a signature; it guards every endpoint in
    the file, which the report needs to say explicitly.
    """
    verb = path = ""
    # Mapping annotation: upwards, stopping at the previous method's closing brace.
    for j in range(i - 1, max(i - 8, -1), -1):
        line = lines[j]
        if line.strip() in ("}", ");") or line.strip().startswith("return "):
            break
        if m := MAPPING.search(line):
            verb = m.group(1).upper()
            if p := re.search(r'"([^"]*)"', line):
                path = p.group(1)
            break
    # Method signature: downwards, past any other annotations.
    for j in range(i + 1, min(i + 12, len(lines))):
        line = lines[j]
        if " class " in line:
            return "CLASS-LEVEL (guards every endpoint in this controller)"
        if m := SIGNATURE.search(line):
            return f"{verb or 'ANY'} {base}{path} -> {m.group(2)}()"
    return "CLASS-LEVEL (guards every endpoint in this controller)"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", action="append", default=[],
                    help="restrict to these top-level modules, e.g. --module hr")
    args = ap.parse_args()

    cases = []
    files = sorted(glob.glob(os.path.join(SRC, "**", "*Controller.java"), recursive=True))
    for path in files:
        rel = os.path.relpath(path, REPO)
        module = rel.split("restaurant_saas/")[1].split("/")[0]
        if args.module and module not in args.module:
            continue
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        base = base_path(lines)
        for i, line in enumerate(lines):
            if "@PreAuthorize" not in line:
                continue
            expr = line.strip()
            cases.append({
                "id": f"A{len(cases) + 1:03d}",
                "guard": f"@PreAuthorize on {os.path.basename(path)[:-5]} "
                         f"{endpoint_of(lines, i, base)} :: {expr}",
                "file": rel,
                "line": i + 1,
                "expect": "@PreAuthorize",
                "module": module,
            })
    json.dump(cases, sys.stdout, indent=2)
    print()
    print(f"// {len(cases)} cases", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
