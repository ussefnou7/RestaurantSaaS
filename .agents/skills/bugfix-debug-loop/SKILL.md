---
name: bugfix-debug-loop
description: Diagnose and fix a bug. Use when something is broken, throws, or a test is failing — "fix", "debug", "failing", "broken".
---

Fix the cause, not the symptom.

## Loop
1. **Reproduce first** — a failing test or exact repro steps. Never fix blind.
2. **State the root cause in one line** before you edit anything.
3. **Fix minimally.** Change only what the root cause requires; leave unrelated code alone
   (that belongs to `safe-refactor`).
4. **Regression test** — add or keep a test that **fails before** the fix and **passes after**.
5. Confirm the fix does **not** violate any DECIDED invariant in `docs/DECISIONS.md` (especially
   the stock-engine invariants D1–D5, D10, D11).

## Output
Root-cause line, the minimal fix, and the regression test. If the "fix" would require reopening
a DECIDED item, STOP and raise it instead.
