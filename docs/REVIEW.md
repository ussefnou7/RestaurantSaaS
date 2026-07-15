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
- [ ] **D10** Batch ordering is by **id (creation order)**, never `movementDate`.
- [ ] **D11** FIFO shortfall priced at **current (pre-movement) average cost**; no retroactive
  COGS correction.
- [ ] **D12** Backend throws structured `errorCode` + `params`; `message` is logs-only. New/edited
  throw sites must **not** use legacy `ApiException` or `BusinessException(String)`.
- [ ] **D13** No premature abstraction: block new abstractions (interfaces, generic frameworks,
  mapper libs, conversion tables) introduced without ≥2 real callers.

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