---
name: architecture-review
description: Structural/style review. Use for new modules, refactors, or migrations — "architecture review", structural changes, package layout, migration review.
allowed-tools: Read, Grep, Bash(git diff:*)
---

Review structure and style. Read-only. **Ground truth is `docs/DECISIONS.md` +
`docs/CONVENTIONS.md` ONLY** — do not reference `docs/PROJECT_SKILL.md` (stale, contradicts
DECISIONS, pending retirement) or `docs/modules/INVENTORY.md` (does not exist).

## Checks (see `docs/REVIEW.md` → Style & arch)
- **Feature-based sub-packages**: code lives in the right `inventory/<feature>` package; engine
  logic in `inventory/core/`, not scattered.
- **Exceptions**: six-branch hierarchy + per-module `ErrorCode` enum; no legacy
  `ApiException`/`BusinessException(String)` in new code.
- **No premature abstraction** (D13 / §1.4): flag speculative generality / abstractions added
  without ≥2 real callers.
- **Frontend**: Hub Page nav pattern, BEM class names, `--color-*` vars, Lucide outline icons,
  RTL-safe layout.

## Migration review (folded in)
- New migration file only — **no edits to an already-applied migration**; next-integer
  `V<n>__snake_case.sql` in `src/main/resources/db/migration/`.
- FK ordering/deferral per convention (FKs last); additive in prod; entity mapping and migration
  in sync.

## Output
Grouped **BLOCK / WARN / NIT** with `file:line`.
