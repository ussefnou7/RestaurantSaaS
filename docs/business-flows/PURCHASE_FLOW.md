# Purchase Invoice & Purchase Return — Business Flow

> **Last verified against code:** backend `63ff8e7e` on 2026-08-30 by Claude Code
> (doc drift audit — [../../claude/DOC_DRIFT_AUDIT.md](../../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as that commit.

## Overview
The purchasing flow covers receiving goods from suppliers and recording
the cost of materials entering the warehouse.

## Purchase Invoice Flow

### Statuses
DRAFT → COMPLETE → POSTED
COMPLETE → DRAFT (uncomplete) · POSTED → COMPLETE (unpost)
DRAFT or COMPLETE → CANCELLED

### DRAFT
- Created by warehouse staff when goods arrive
- Can be edited freely (lines can be added, removed, changed)
- No inventory impact

### COMPLETE
- Reviewed and approved by manager
- No further editing allowed
- No inventory impact yet
- Reversible with `POST /{id}/uncomplete` back to DRAFT

### POSTED
- Stock In triggered for all lines
- A `StockBatch` is opened per line at the entered unit cost
- StockBalance updated:
    - quantity moves by the ledger's signed delta (`StockBalanceService` is the only writer)
    - averageCost **re-derived from the OPEN batches** (D2), not accumulated
    - lastPurchasePrice and lastPurchaseDate updated by the operation service
- **Reversible** with `POST /{id}/unpost`, subject to two guards in this order (D8):
  the return-existence guard runs **before** the batch-consumption guard. A posted invoice
  whose batches have been partly consumed, or which has returns against it, cannot be unposted.
- Accounting document can be created by accountant after posting

### CANCELLED
- Only from DRAFT or COMPLETE
- No inventory impact

## Purchase Return Flow

### When to use
- Supplier delivered damaged goods
- Wrong quantity received
- Wrong specification

### Rules
- Can only be created against a POSTED invoice
- Each return line references the original invoice line
- Return quantity per line ≤ original quantity - already returned quantity
- Unit cost is always copied from the original line (cannot be changed)
- Multiple returns against the same invoice are allowed
  as long as total returned ≤ original quantity per line

### POSTED Return Impact
- Stock Out for returned quantities at the source batch's original cost
- lastPurchasePrice restored to previous valid purchase
- **Reversible** with `POST /{id}/unpost` — and unlike the invoice there is **no**
  batch-consumption guard, because restoring a return is additive (D9). The restore is capped
  at the source batch's original quantity.
- `POST /{id}/uncomplete` returns a COMPLETE return to DRAFT
- Accounting document can be created after posting

## Cost Logic

Stated correctly here because an earlier revision of this file described a running weighted
average, which is the formula D2 explicitly rejects.

- All ledger quantities are stored in the material's **stockUom** (base unit); `StockBalance`,
  `StockBatch` and count lines are **displayUom** (D87). Do not mix the two layers in one sum.
- `averageCost` is **derived from OPEN batches only** (`remainingQuantity > 0`):
  `Σ(batch remaining × batch unit cost) ÷ Σ(batch remaining)`, recomputed after each movement
  by `StockBalanceService`. There is no `(oldQty·oldAvg + Δ·cost)/newQty` accumulation anywhere,
  and no cost-bearing-type branching. When there is no open stock the last known average is
  carried forward untouched.
- OUT transactions (return, consumption, waste) **FIFO-deplete open batches** ordered by
  `movementDate ASC, id ASC` (D10) and are valued at those batches' costs — not at a single
  balance-level average. Only the **shortfall** (demand exceeding all open batches) is priced
  at the current pre-movement average, with no retroactive COGS correction (D11).
- lastPurchasePrice = most recent posted purchase price per (warehouse, material)
- lastPurchasePrice is NOT updated by returns — restored to previous valid purchase

## API Reference
Swagger UI at /swagger-ui.html
Tags: "Inventory - Purchase Invoice", "Inventory - Purchase Return"

## Known defect (not intended behaviour)
`POST /{id}/post` on **both** `PurchaseInvoiceController` and `PurchaseReturnController` is
missing the `@PreAuthorize` permission annotation that every adjacent transition carries.
Global authentication still applies, but the documented permission gate does not. Tracked as a
code defect — see [PROJECT](../PROJECT.md) → Known defects.
