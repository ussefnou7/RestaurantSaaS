# Physical Count (Inventory Count) — Business Flow

> **Last verified against code:** backend `63ff8e7e` on 2026-08-30 by Claude Code
> (doc drift audit — [../../claude/DOC_DRIFT_AUDIT.md](../../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as that commit.

## Overview
Physical counts allow the branch manager to verify and correct
actual stock quantities against system quantities.

## Flow
DRAFT → IN_PROGRESS → RECONCILED
IN_PROGRESS → DRAFT (revert-to-draft, before reconcile)
DRAFT or IN_PROGRESS → CANCELLED

## Statuses

### DRAFT
- Count created with selected materials
- Expected quantities not yet captured
- Materials can be added and removed freely

### IN_PROGRESS
- frozenAt timestamp recorded
- expectedQuantity and unitCostAtFreeze snapshot from StockBalance
  (`quantity` and `averageCost` at freeze time)
- Team enters counted quantities; `countedAt` is refreshed on every counted-quantity update
  and cleared with it
- Inventory continues to operate normally during counting
- Reversible with `POST /{id}/revert-to-draft`

### RECONCILED
- All variances posted to inventory
- stock_balance updated with lastCountDate and lastCountQuantity
- Large variance flag calculated
- **Terminal** — no unpost, reverse, or reopen. Delete/edit are allowed only before reconcile,
  under D6's dual check (DRAFT status **and** no ledger transactions)

## Freeze: settle, then snapshot

Starting a count first **settles the warehouse's outstanding order consumption**, then takes the
snapshot — back to back, so the frozen expected quantity is not chasing unposted consumption
(D89). If the settlement ends in `CONFLICT` the freeze **refuses** with `FREEZE_CONFLICT`; if it
is still in progress the freeze asks for a retry. Order intake is never blocked or queued for a
count.

    expectedQuantity = stock_balance.quantity at frozenAt
    unitCostAtFreeze = stock_balance.averageCost at frozenAt

## Variance

Per line, the netting window runs from `frozenAt` to **that line's `countedAt`** — not to
reconcile time, which would double-count in the opposite direction (D90):

    adjustedExpected = expectedQuantity
                     + Σ IN transactions in (frozenAt, countedAt]
                     - Σ OUT transactions in (frozenAt, countedAt]
    variance = countedQuantity - adjustedExpected

This ensures the variance reflects only real physical discrepancies, not legitimate transactions
that happened during the counting process. Read and write derive variance from the same shared
computation, and a RECONCILED count returns **persisted** values — it is never recomputed on read.

## Variance Actions

Every non-zero variance posts a **`COUNT_ADJUSTMENT`** transaction, with the ledger direction
carrying the sign. `movementDate` on that transaction is the line's `countedAt` — not `now()` and
not `frozenAt`.

`CountLineAction` values: `PENDING` (not yet reconciled), `NO_DIFFERENCE` (variance = 0),
`ADJUSTMENT` (the only outcome a variance can produce).

> **A count no longer offers a waste option.** The `WASTE` enum constant still exists but is
> **legacy only and never written** — retained solely so lines reconciled before `V35`
> deserialize. Per D89, do not reintroduce a waste document, a waste-typed row, or a per-line
> action choice into the count path. Earlier revisions of this file documented WASTE as a live
> variance action; that was wrong.

Reports over count movements must filter by `reference_type = 'PHYSICAL_COUNT'`, never by
transaction type alone — opening balance writes adjustment-class rows with a null `referenceType`
and would otherwise be swallowed as a shortage (D89).

## Large Variance
- Threshold: 500 EGP — a hardcoded `LARGE_VARIANCE_THRESHOLD` constant in `PhysicalCountService`,
  not tenant configuration
- `large_variance_value = Σ (variance × unitCostAtFreeze)` — a **signed** sum across lines
- `has_large_variance = |large_variance_value| > 500`
- Visible in dashboard for owner review

> **Note the signed sum.** Because the line values are added with their signs before the absolute
> value is taken, offsetting errors cancel: a count that is 600 EGP short on one material and
> 600 EGP over on another nets to zero and is **not** flagged. Whether that is intended is an
> open question, not a documented decision — do not build on it either way without settling it.

## API Reference
Swagger UI at /swagger-ui.html
Tag: "Inventory - Physical Count"
Endpoints: `POST /api/inventory/physical-counts`, `/{id}/add-materials`, `/{id}/remove-materials`,
`/{id}/start`, `/{id}/revert-to-draft`, `/{id}/reconcile`, `/{id}/cancel`.
