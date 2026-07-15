# ROADMAP

> Forward-looking work. Items here are **planned**, not decided ground truth — design
> details for anything under "Orders" and "Aggregators" are OPEN (see
> [DECISIONS](DECISIONS.md) → OPEN). Hard invariants that already hold live in
> [DECISIONS](DECISIONS.md) → DECIDED.

## 1. Orders module (largest effort)

The next major module. Expanded design lives in [modules/ORDERS.md](modules/ORDERS.md).

- **Unified `Order` entity** with `orderType` and `orderSource` discriminators instead of one
  entity per channel. One table, one lifecycle, filtered/behaviour-branched by those fields.
- **Hybrid Ledger** for stock consumption: each order line’s material usage is written to
  `OrderConsumptionEvent` (the existing, not-yet-wired staging table — unique on
  `tenant_id + idempotency_key`, `IdempotencyScope.ORDER_CONSUMPTION_EVENT`). A **scheduled
  job** aggregates events and posts summarized `CONSUMPTION_SUMMARY` transactions through
  `InventoryLedgerService.record(...)` — orders never write the ledger per-line in the hot path.
- **Confirmation step** required for **online / aggregator** orders only; dine-in / POS walk-in
  orders skip it.
- **Single POS screen, 3 modes** (e.g. dine-in / takeaway / delivery) rather than three screens.
- **Offline capability** + **ESC/POS thermal printing** for receipts/kitchen tickets.

## 2. Aggregator integrations

- Talabat, Otlob, Noon Food, Fawry.
- **Manual entry first** (staff key the order in) before any API/webhook automation.
- API/webhook ingestion design is OPEN.

## 3. Configurable approval workflows

- A configurable `ApprovalWorkflow` entity so document state transitions (e.g. post/unpost,
  large-variance reconcile) can require approval per tenant/config, replacing hardcoded rules.
- Config surface (per document type? per threshold? per role?) is OPEN.

## 4. Exception-handling migration (finish the started work)

Migrate remaining throw sites onto the structured hierarchy (`AppException` subclasses +
per-module `ErrorCode` + `params`), then delete the legacy `ApiException` and the deprecated
`BusinessException(String)` constructor. Counts of files still on legacy exceptions:

| Area | Files | Which |
|---|---|---|
| Auth | 3 | (per module audit) |
| **Inventory services** | **7** | `WasteService`, `UomService`, `InvoiceSequenceService`, `MaterialService`, `MaterialCategoryService`, `SupplierService`, `WarehouseService` |
| RBAC | 4 | (per module audit) |
| Tenant | 3 | (per module audit) |

> The inventory 7 are confirmed against the code (they still `throw new ApiException(...)` /
> `new BusinessException("...")`). The Auth/RBAC/Tenant counts are the migration targets to
> audit as those modules are migrated.

## 5. Inventory integration test suite

Unit + controller-security/contract tests already exist per core service. Add an
**integration test suite** exercising full document lifecycles against a real
(Testcontainers/Flyway) Postgres — post→unpost→re-post cycles, FIFO consumption across
batches, purchase-return source-batch depletion, physical-count freeze/reconcile — asserting
the ledger, batches, and balance stay coherent end to end.

## 6. Frontend i18n cleanup

- **Enum-value translations.** Now that the backend emits `errorCode` + `params` (and enum
  fields like statuses/types/reason codes), give every enum value a translation key so the FE
  renders localized labels instead of raw enum names. Partial coverage exists
  (`inventory.warehouses.types.*`, `inventory.catalog.skippedReason.*`,
  `inventory.warehouses.stocks.batches.status.*`); systematize it. Approach is OPEN.
- **Dead key cleanup — `leaveAssign.errors.*`.** Of the six defined keys
  (`src/i18n/locales/{en,ar}/leaveAssign.ts`), only `leaveAssign.errors.forbidden` is
  referenced in code. Remove the unused `load`, `generate`, `update`, `negativeRemaining`,
  `noActiveLeaveTypes` (or wire them up) — in both `en` and `ar`.
-
## 7. Fixed Assets module (backend built, frontend pending)

Decision-complete (D46–D52 in [DECISIONS](DECISIONS.md)), full schema in
[modules/ASSETS.md](modules/ASSETS.md). Backend landed in `V16__assets.sql` (28 unit/security
tests passing). Frontend not started — prompt ready at
[PROMPT_CODEX_ASSETS_FRONTEND.md](PROMPT_CODEX_ASSETS_FRONTEND.md). Deferred to sit *below* the
future P&L/accounting module, so V1 deliberately excludes any profit/ROI reporting (O10).

- **`Asset` (header) → `AssetLine` (per-purchase-batch)**: same reasoning as `Material` →
  `StockBatch` — the same asset type is bought at different prices/times and that needs to
  stay distinguishable. Also covers large single-unit equipment (one oven = one line under
  an "Oven" asset header), giving free aggregate totals per asset type.
- **`AssetDisposal`**: write-off events, manually targeted at a specific `AssetLine` — no
  FIFO/auto-selection, unlike the Inventory ledger. Capped at the line's remaining quantity.
- **`AssetMaintenance`**: cost-only records against a line, never touches quantity. Expected
  mostly on large single-unit equipment in practice.
- **V1 report scope**: total asset value + disposal history (date/reason/value) only. No
  cost-coverage/ROI percentage against profit — blocked on the P&L module (O10).
- New permission `ASSETS_MANAGE`; new `AssetErrorCode`; feature-based `assets/` package
  mirroring `inventory/`'s layout.

## Menu Module — Backlog (Post-V1)

- **Multi-branch menu customization**: Per-branch product availability
  (hide/show specific products per branch), potentially per-branch
  pricing overrides. Real, confirmed future need — not speculative.
  Design direction: additive availability table, no changes to core
  `Product`/`MenuCategory` entities.
- **Per-channel product visibility**: `isPOS`, `isDelivery`, and similar
  flags on `Product` to control which sales channels can sell a given
  item. Confirmed future need. Design direction: boolean columns on
  `Product`, default `true`.
- **Recipe versioning/history**: track recipe changes over time
  (currently full-replace, no history retained).
- **Product Modifiers/Variants**: size options, add-ons — deferred from
  V1 foundation.
- **Multi-menu support**: `Menu` entity (e.g. breakfast vs. dinner
  menus) if/when a tenant needs more than one active menu.
-
## Device module — follow-ups (not blocking)
- No DB constraint yet enforcing "one warehouse per branch" (`uk_warehouse_branch_id`) —
  currently a convention, not enforced. Add when multi-warehouse-per-branch becomes real.
- `X-Branch-Id` is trusted as a plain header post-login, not cryptographically bound to the
  device secret per request (see DECISIONS D33). Upgrade path: signed device JWT with
  `branchId` claim, verified per request like user JWTs already are.
- Devices admin page nav placement/naming was fixed manually post-Codex-run (was scoped as
  generic "Admin hub" — role model has no "Admin" role, should sit alongside Owner-facing
  tenant settings like Warehouses/Branches). Verify final placement matches.