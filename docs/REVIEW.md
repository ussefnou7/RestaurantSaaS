# REVIEW CHECKLIST

> The reviewer’s checklist. Group findings as **block** / **warn** / **nit**. Anything under
> “Hard invariants” is a **block** on violation. Full statements + code anchors are in
> [DECISIONS](DECISIONS.md); this is the fast pass. Cite `file:line` and the invariant id.

## Hard invariants — violation = block

- [ ] **D1** `StockBalance.quantity` moved only by the ledger’s signed delta; not re-derived
  from batches. Negative is allowed on shortfall — flag any code that clamps it to zero.
- [ ] **D2** Average cost derived from OPEN batches only (`remainingQuantity > 0`). No running
  `(oldQty·oldAvg + Δ·cost)/newQty` formula; no “cost-bearing type” branching.
- [ ] **D3** A `LedgerCommand` carries **raw entered** quantity/UOM/unit-cost. No caller
  pre-converts the entered qty/cost before `InventoryLedgerService.record(...)`. (Downstream
  display-UOM conversions inside the engine are fine.)
- [ ] **D4** `inventory_transaction` written only inside `InventoryLedgerService`. No other
  `transactionRepo.save(...)`. Ledger rows are append-only (reverse, never mutate/delete).
- [ ] **D5** `stock_balance.quantity` / `averageCost` written only by `StockBalanceService`.
  Operation services may set only `lastPurchase*` / `lastCount*`. Flag any operation service
  that sets `quantity`/`averageCost` directly.
- [ ] **D6** Delete requires **DRAFT status AND no ledger transactions** (dual-check). Status
  alone is not enough.
- [ ] **D7** Waste has **no** unpost and **no** reversal. Flag any reverse/unpost path added to
  waste.
- [ ] **D8** Purchase Invoice unpost: **return-existence guard before batch-consumption guard**
  (order matters for the message).
- [ ] **D9** Purchase Return unpost: **no** batch-consumption guard (it’s additive); restore is
  capped at the batch’s original quantity.
- [ ] **D10** Batch ordering is `movementDate ASC, id ASC` — receipt date leads, id breaks ties.
  Flag any `ORDER BY id` on batch **selection** paths (the invoice-unpost guard query is the one
  intentional exception — it orders guard processing, not FIFO). Flag any code assuming open
  batches form a recent suffix of the batch list: under this rule a newer batch may be partly
  consumed while an older, later-registered one is untouched.
- [ ] **D11** FIFO shortfall priced at **current (pre-movement) average cost**; no retroactive
  COGS correction.
- [ ] **D12** Backend throws structured `errorCode` + `params`; `message` is logs-only. New/edited
  throw sites must **not** use legacy `ApiException` or `BusinessException(String)`.
- [ ] **D13** No premature abstraction: block new abstractions (interfaces, generic frameworks,
  mapper libs, conversion tables) introduced without ≥2 real callers.

### Physical Count (D89, D90) — apply on any change to the count lifecycle

- [ ] **D89** A count writes `COUNT_ADJUSTMENT` only, with direction carrying the sign. Flag any
  waste document, waste-typed row, or per-line action choice reintroduced into the count path.
- [ ] **D89** Freeze settles PENDING consumption before the snapshot and refuses on CONFLICT.
  Flag any freeze path that skips the guard, and any attempt to block or queue order intake
  during a count.
- [ ] **D30/D89** Any check for "outstanding consumption" treats `PARTIAL` and `CONFLICT` alike —
  both hold unposted consumption and both need human action. Flag any branch that tests for
  `CONFLICT` alone, or any status classification with a default/fallthrough bucket.
- [ ] **D89** Reconcile is terminal — no unpost/reverse/reopen. Delete/edit allowed only before
  reconcile, under D6's dual check.
- [ ] **D89** Reports over count movements filter by `reference_type = 'PHYSICAL_COUNT'`, never
  by transaction type alone — opening balance writes adjustment-class rows with a null
  `referenceType` and would be swallowed as a shortage.
- [ ] **D90** Variance is `counted − (frozen expected + net movements from frozenAt to that
  line's countedAt)`. Flag any comparison against the raw frozen quantity.
- [ ] **D90** The netting window closes at `countedAt`, not at reconcile. Flag any window
  extending to the posting time — it double-counts in the opposite direction.
- [ ] **D90** `movementDate` on a count adjustment is the line's `countedAt`. Flag `now()` and
  flag `frozenAt`.
- [ ] **D90** A RECONCILED count returns persisted values. **Flag any recomputation on read** —
  it makes a finalized audit record display moving numbers and no existing test would catch it.
- [ ] **D90** Read and write derive variance from the same shared computation. Flag any second
  implementation, in the service or in the frontend.
- [ ] **D90** `countedAt` is refreshed on every counted-quantity update and cleared with it.

### UOM layers (D87, D88)

- [ ] **D87** No arithmetic mixes ledger (stock UOM) and operational (display UOM) figures.
  `StockBalance`, `StockBatch`, and count-line quantities are display UOM; `inventory_transaction`
  is stock UOM. Same-layer only.
- [ ] **D87** Folds happen in one layer and convert **once**; flag per-row conversion followed by
  summation (rounding drift at scale 6).
- [ ] **D88** Any endpoint returning ledger-sourced quantities converts to the display layer
  **and** carries an explicit UOM field. A bare number is a defect even when correct.
- [ ] **D88** Conversion targets a document's **frozen** UOM where one exists, not the material's
  current `displayUom`.
- [ ] **D88** No conversion path → structured failure. Flag any fallback to the unconverted
  value or to the frozen quantity.

> **Reviewer note.** D1's "signed ledger delta only" and D3's "converted once" wording each
> described one side of the two-layer split without naming it, and this checklist repeated that
> wording as a hard invariant for months. A reviewer reading it learned an incomplete model of
> the engine — and code written against that model would have passed review. D87 is the
> corrected statement; treat the older phrasing in D1/D3 as refined by it, not as an
> alternative reading.

> Known pre-existing violations to expect (don’t re-litigate as new, but don’t let new code
> imitate them): D12 in the 7 un-migrated inventory services; D5 wording (denormalized
> `saveAll`s in `PurchaseInvoiceService:250`, `PurchaseReturnService:364`,
> `PhysicalCountService:387`). See [DECISIONS](DECISIONS.md).

### Fixed Assets (not yet built — apply once implemented)

- [ ] **D46** `AssetDisposal`/`AssetMaintenance` target an explicit `assetLineId` chosen by the
  caller — flag any oldest/cheapest/average auto-selection logic (no FIFO here, unlike D10).
- [ ] **D48** `AssetDisposal.quantityDisposed` capped at the target line's current
  `remainingQuantity`; never allowed to go negative.
- [ ] **D50** No delete on `Asset`/`AssetLine` once any `AssetDisposal`/`AssetMaintenance`
  record exists against it (dual-check spirit of D6).
- [ ] **D50** No cost-coverage/ROI-against-profit reporting anywhere in this module — that's
  explicitly deferred (O10). Flag any such calculation as premature.
- [ ] **D51** Disposal/maintenance requests must carry both `assetId` and `assetLineId`; service
  validates `AssetLine.assetId == request.assetId` — flag any code trusting `assetLineId`
  alone.

## Style & architecture

### Backend
- [ ] File placed in the correct **feature package**; entity/controller/service/dto co-located.
  Engine logic in `inventory/core/`, not scattered.
- [ ] Correct base class (`TenantAwareEntity` vs `BaseEntity` vs none). New enums in
  `core/enums/` persisted `EnumType.STRING`.
- [ ] All money/qty math `BigDecimal`, `scale=6`, `HALF_UP`. Percent-or-amount: percent wins.
- [ ] Exceptions extend one of the six bases with a per-module `ErrorCode` + `ErrorParams`
  carrying every dynamic value; debug message is English/logs-only.
- [ ] Controller: `X-Tenant-Id` required, `X-User-Id` optional for audit; `@PreAuthorize`
  permission gate; URL/verb conventions; state transitions as POST sub-resources.
- [ ] Flyway: next integer version, idempotent DDL, `uk_/idx_/chk_` naming; no reliance on
  Hibernate DDL; child table over JSON.
- [ ] Test coverage for the touched invariant/lifecycle.

### Frontend
- [ ] **BEM** class names; **`--color-*`** variables only (no hardcoded colors).
- [ ] Icons from **`lucide-react`** outline set.
- [ ] **RTL**-safe layout (logical start/end, not hardcoded left/right).
- [ ] **`useTranslation()`** for all user-facing text; keys present in **both** `en` and `ar`;
  no dead keys left behind.
- [ ] Backend errors rendered via **`translateApiError`** (`errorCode` + `params`); server
  `message` never shown. Enum values rendered via translation keys, not raw names.
