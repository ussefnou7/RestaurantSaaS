---
name: implementation-planner
description: Plan a change before coding. Use when starting a feature/module or a task spanning multiple files, or when asked "plan" or "how should we build" this. Produces a tactical file-by-file plan, not architecture.
---

Plan before writing code. Do not make architectural decisions.

## Steps
1. Read `docs/PROJECT.md` (current state), `docs/DECISIONS.md` (DECIDED = hard constraints,
   never reopen; OPEN = undecided), and `docs/ROADMAP.md` if this is a roadmap item.
2. Produce a **tactical** plan only:
   - Target feature sub-package (`inventory/{warehouse,material,category,uom,stock,purchase,physicalcount,transfer,core}` or the relevant FE `src/pages/<feature>`).
   - Files to add/change, in implementation order.
   - Which DECIDED invariants (D#) apply to this change.
3. Make **no** architectural decisions. If the task needs a decision not already in
   `docs/DECISIONS.md`, **STOP** and list it as an open question for the human — do not guess.

## Output
- **File-by-file plan** (path + one line each, in order).
- **Invariants in play** (cite D# from `docs/DECISIONS.md`).
- **Open questions** (anything needing a human decision).

No code. No new abstractions.
