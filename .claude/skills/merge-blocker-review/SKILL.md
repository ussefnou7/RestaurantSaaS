---
name: merge-blocker-review
description: Block-level review against hard invariants. Use to gate a change before merge — "review", "can this merge", "review the diff".
allowed-tools: Read, Grep, Bash(git diff:*)
---

Gate the diff against the hard invariants. Read-only.

## Steps
1. Read `docs/REVIEW.md` (Hard invariants) and `docs/DECISIONS.md` (DECIDED).
2. Get the diff (`git diff`) and check it against **every** hard invariant. Any violation =
   **BLOCK**, cited with `file:line` + invariant id/name.

## Verify specifically
- `StockBalance.quantity` is the signed **ledger delta**, not batch-derived; may go negative (D1).
- Average cost from **open batches only**; no running incremental formula (D2).
- Entered→stock **UOM conversion only in `InventoryLedgerService.record()`**; no caller
  pre-conversion (D3).
- Sole-writer services respected: `inventory_transaction`→ledger, `stock_balance`
  qty/avg→`StockBalanceService`, `stock_batch`→`StockBatchService` (D4, D5).
- **Dual-check delete**: DRAFT **and** no ledger transactions (D6).
- **Waste has no unpost/reversal** (D7).
- Purchase Invoice unpost **guard order**: return-existence **then** batch-consumption (D8);
  Purchase Return unpost has **no** batch-consumption guard (D9).
- Batch ordering by creation order/id, not `movementDate` (D10); shortfall at pre-movement
  average (D11).
- Exceptions emit `errorCode` + `params` only; no legacy `ApiException`/`BusinessException(String)`
  in new code (D12).

## Output
Grouped **BLOCK / WARN / NIT**, each with `file:line`. Don't re-flag the known pre-existing
violations documented in `docs/DECISIONS.md` unless the diff touches them.
