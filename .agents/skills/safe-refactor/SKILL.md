---
name: safe-refactor
description: Behavior-preserving refactor / structural cleanup. Use for "refactor", "clean up", "restructure" — including the saveWithIdempotencyGuard double-save.
---

Restructure without changing behavior.

## Hard rules
- **No behavior change.** On numeric paths (costing/ledger/batch), **no change to any numeric
  output** — same values, same scale/rounding (`scale=6`, `HALF_UP`).
- Write **characterization tests** capturing current outputs **before** editing; they must still
  pass unchanged after.
- **One refactor per PR.** No scope creep, no opportunistic feature/bugfix edits.

## Idempotency target
`InventoryLedgerService.saveWithIdempotencyGuard(InventoryTransaction tx, Long tenantId, String
idempotencyKey)` — `inventory/core/InventoryLedgerService.java:252`. It persists the transaction
then mutates the balance across batch steps. Any refactor must keep **identical numeric output**,
and shortfall pricing must still read the **pre-movement** average cost (D11) — the FIFO
depletion reads the average as it stood before `StockBalanceService.applyMovement` re-derives it.

Do not reopen any DECIDED invariant in `docs/DECISIONS.md`.
