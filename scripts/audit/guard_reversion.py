#!/usr/bin/env python3
"""Guard-reversion harness (ledger L006).

A green suite proves the code does what the tests say. It does not prove a test would
notice if a protection were deleted. This disables one guard at a time, runs the suite,
and records which test -- if any -- failed.

One guard at a time is the whole point: two disabled at once gives an ambiguous result.
The harness enforces that by reverting via `git checkout --` after every case, and by
refusing to start if the target file is already dirty.

Usage:
    scripts/audit/guard_reversion.py cases/money.json            # run a case file
    scripts/audit/guard_reversion.py cases/money.json --only G01  # run one case
    scripts/audit/guard_reversion.py cases/money.json --dry-run   # check patches apply

Case file: a JSON list of objects. Two anchor modes.

  Substring mode -- for guards with a distinctive body:
    id       short stable id, used in the report
    guard    what the protection is, in words
    file     path relative to repo root
    find     exact source substring to replace (must occur exactly once)
    replace  what to put there -- the disabled form

  Line mode -- for guards whose source text repeats, e.g. the same @PreAuthorize
  expression on twenty endpoints. The line number disambiguates; `expect` is a
  substring the line must contain, so a case file does not silently rot into
  mutating the wrong line after an edit above it.
    line     1-indexed line number
    expect   substring that line must contain
    replace  replacement for the whole line (default: comment it out)
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPORTS = os.path.join(REPO, "target", "surefire-reports")


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=REPO, capture_output=True, text=True, **kw)


def git_dirty(path: str) -> bool:
    return bool(run(["git", "status", "--porcelain", "--", path]).stdout.strip())


def revert(path: str) -> None:
    run(["git", "checkout", "--", path])


def apply_patch(case: dict) -> None:
    path = os.path.join(REPO, case["file"])
    if "line" in case:
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        i = case["line"] - 1
        # line_end makes the case a span, for an annotation whose expression wraps across
        # lines. Commenting only its first line leaves the rest as a syntax error, which the
        # harness would report as INCONCLUSIVE rather than as a verdict about the guard.
        end = case.get("line_end", case["line"]) - 1
        if not 0 <= i <= end < len(lines):
            raise SystemExit(
                f"[{case['id']}] {case['file']} has no line range "
                f"{case['line']}..{case.get('line_end', case['line'])}"
            )
        if case["expect"] not in lines[i]:
            raise SystemExit(
                f"[{case['id']}] {case['file']}:{case['line']} does not contain "
                f"{case['expect']!r}; it is {lines[i].rstrip()!r}. The case file is stale."
            )
        if "replace" in case:
            lines[i : end + 1] = [case["replace"]]
        else:
            lines[i : end + 1] = [
                "// L006 disabled: " + line.strip() + "\n" for line in lines[i : end + 1]
            ]
        with open(path, "w", encoding="utf-8") as fh:
            fh.writelines(lines)
        return

    with open(path, encoding="utf-8") as fh:
        src = fh.read()
    n = src.count(case["find"])
    if n != 1:
        raise SystemExit(
            f"[{case['id']}] anchor occurs {n}x in {case['file']} (need exactly 1).\n"
            f"  anchor: {case['find']!r}"
        )
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(src.replace(case["find"], case["replace"]))


def clear_reports() -> None:
    for f in glob.glob(os.path.join(REPORTS, "*.xml")):
        os.remove(f)


def collect_failures() -> tuple[list[str], int, bool]:
    """Returns (failing test ids, total tests, whether any report was produced)."""
    failures: list[str] = []
    total = 0
    files = glob.glob(os.path.join(REPORTS, "*.xml"))
    for p in files:
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests") or 0)
        for tc in root.iter("testcase"):
            bad = tc.find("failure") is not None or tc.find("error") is not None
            if bad:
                cls = (tc.get("classname") or "").split(".")[-1]
                failures.append(f"{cls}.{tc.get('name')}")
    return sorted(set(failures)), total, bool(files)


def suite() -> tuple[str, list[str], int]:
    """Run the suite. Returns (outcome, failures, total).

    outcome is one of: PASS (nothing failed), FAIL (tests failed),
    COMPILE_ERROR (the mutation did not build).
    """
    clear_reports()
    proc = run(["./mvnw", "-o", "-q", "test"])
    failures, total, had_reports = collect_failures()
    if proc.returncode == 0:
        return "PASS", [], total
    if not had_reports or "COMPILATION ERROR" in proc.stdout + proc.stderr:
        return "COMPILE_ERROR", [], total
    if not failures:
        # Non-zero exit with reports but no testcase-level failure: surefire itself
        # choked, or a class failed to initialise. Treat as a build problem, not a
        # guard finding -- a guard finding has to name a test.
        return "COMPILE_ERROR", [], total
    return "FAIL", failures, total


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("cases")
    ap.add_argument("--only", action="append", default=[])
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--out", default=None, help="append JSONL results here")
    args = ap.parse_args()

    with open(args.cases, encoding="utf-8") as fh:
        cases = json.load(fh)
    if args.only:
        cases = [c for c in cases if c["id"] in args.only]
    if not cases:
        raise SystemExit("no cases selected")

    for c in cases:
        if git_dirty(c["file"]):
            raise SystemExit(
                f"{c['file']} is already modified. The harness reverts with "
                f"`git checkout --`, which would destroy that work. Commit or stash first."
            )

    results = []
    for i, c in enumerate(cases, 1):
        print(f"\n=== [{i}/{len(cases)}] {c['id']}: {c['guard']}", flush=True)
        apply_patch(c)
        if args.dry_run:
            print("  patch applies cleanly", flush=True)
            revert(c["file"])
            continue
        t0 = time.time()
        try:
            outcome, failures, total = suite()
        finally:
            revert(c["file"])
        secs = time.time() - t0
        verdict = {
            "FAIL": "COVERED",
            "PASS": "UNCOVERED",
            "COMPILE_ERROR": "INCONCLUSIVE (did not compile)",
        }[outcome]
        print(f"  -> {verdict} in {secs:.0f}s, {len(failures)} failing test(s), {total} run")
        for f in failures[:12]:
            print(f"     {f}")
        if len(failures) > 12:
            print(f"     ... and {len(failures) - 12} more")
        results.append(
            {
                "id": c["id"],
                "guard": c["guard"],
                "file": c["file"],
                "verdict": verdict,
                "failures": failures,
                "total": total,
                "seconds": round(secs),
            }
        )
        if args.out:
            with open(args.out, "a", encoding="utf-8") as fh:
                fh.write(json.dumps(results[-1]) + "\n")

    uncovered = [r for r in results if r["verdict"] == "UNCOVERED"]
    print(f"\n==== {len(results)} guards; {len(uncovered)} UNCOVERED")
    for r in uncovered:
        print(f"  {r['id']}  {r['guard']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
