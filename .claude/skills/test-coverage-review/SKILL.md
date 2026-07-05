---
name: test-coverage-review
description: Review test adequacy. Use alongside any code change — "coverage", "are there enough tests".
allowed-tools: Read, Grep, Bash(git diff:*)
---

Judge coverage by **invariant coverage, not line %**. Read-only.

## Checks
- Is there a test that would **FAIL if a DECIDED invariant broke** for the changed behavior?
  Cross-reference `docs/DECISIONS.md`.
- For inventory changes, expect **integration** tests along the chain: average cost (open
  batches only) → batch creation/ordering → FIFO consumption → document lifecycle.
- Every **bug fix** must ship a **regression test** — flag if missing.

## Output
Grouped **BLOCK / WARN / NIT**. Use **WARN (don't block)** when coverage is thin but the
invariants are covered. Block only when a changed invariant has no test that would catch its
regression, or a bug fix has no regression test.
