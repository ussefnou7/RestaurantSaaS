# ROADMAP

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155`, POS `03b0e81`
> on 2026-08-30 by Claude Code (doc drift audit — [claude/DOC_DRIFT_AUDIT.md](../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as those commits.

> Forward-looking work only. Anything already built lives in [PROJECT](PROJECT.md), not here.
> Items here are **planned**, not decided ground truth — design details for anything under
> "Orders" and "Aggregators" are OPEN (see [DECISIONS](DECISIONS.md) → OPEN). Hard invariants
> that already hold live in [DECISIONS](DECISIONS.md) → DECIDED.

## 1. Orders — remaining work

The unified `Order` entity, the consumption staging documents and their aggregation scheduler,
the sales reports, the single-screen POS with dine-in/takeaway/delivery modes, and POS offline
durability are **built** — see [PROJECT](PROJECT.md) and [modules/ORDERS.md](modules/ORDERS.md).
What is left:

- **ESC/POS thermal printing** for receipts and kitchen tickets. Today "print/reprint" only opens
  a visual receipt preview in the POS; no printer adapter exists.
- **Test coverage for order intake.** `IncomingOrderRequest` (entity, service, and the
  `RECEIVED → SENT_TO_POS → LINKED` endpoints) has zero test references, so it does not meet the
  "built" bar even though it is wired.
- **Automatic order → request linking.** Linking is currently an explicit permission-protected
  `PATCH /api/order-requests/{id}/link` carrying an order id. There is no POS echo of the request
  id in `POST /api/orders`, which is the mechanism D24 assumed. Reconcile the two before building
  on either.

## 2. Aggregator integrations

A generic, permission-protected intake API and an admin list/detail UI already exist. What is
missing is connector-specific:

- **Branded connectors** — Talabat, Otlob, Noon Food, Fawry: no webhook controller or per-brand
  API client exists for any of them.
- **Manual-entry creation UI.** Staff can view and link intake requests in the admin app, but
  there is no manual create surface for keying an aggregator order in.
- Per-connector auth, dedup, and payload mapping remain OPEN (O2, O7, O8).

## 3. Configurable approval workflows

- A configurable `ApprovalWorkflow` entity so document state transitions (e.g. post/unpost,
  large-variance reconcile) can require approval per tenant/config, replacing hardcoded rules.
  Nothing of this exists today; `DocumentHistory` is isolated, unwired scaffolding.
- Config surface (per document type? per threshold? per role?) is OPEN.

## 4. Exception-handling migration (finish the started work)

Migrate remaining throw sites onto the structured hierarchy (`AppException` subclasses +
per-module `ErrorCode` + `params`), then delete the legacy `ApiException` and the deprecated
`BusinessException(String)` constructor. Files still on legacy exceptions:

| Area | Files | Which |
|---|---|---|
| Auth | **1** | `CurrentUserService` |
| Inventory services | **0** | migrated |
| RBAC | **0** | migrated |
| Tenant | **3** | `CurrentTenantProvider`, `TenantCodeService`, `TenantService` |

> Counts audited 2026-08-30 against `63ff8e7e`. Every remaining call is `ApiException`; no
> deprecated `BusinessException(String)` call survives in these four areas.

## 5. Isolated test datasource (Testcontainers)

Unit, controller-security/contract, and real integration tests already exist across the
inventory, order, and config packages. What is missing is **isolation**: there is no
Testcontainers dependency and no test datasource, so `@SpringBootTest` runs against the
configured PostgreSQL database. Add a Testcontainers/Flyway-provisioned datasource so
integration tests get a fresh database, then extend the suite to full document lifecycles —
post→unpost→re-post cycles, FIFO consumption across batches, purchase-return source-batch
depletion, physical-count freeze/reconcile — asserting the ledger, batches, and balance stay
coherent end to end.

## 6. Frontend i18n cleanup

- **Enum-value translations.** Now that the backend emits `errorCode` + `params` (and enum
  fields like statuses/types/reason codes), give every enum value a translation key so the FE
  renders localized labels instead of raw enum names. Partial coverage exists
  (`inventory.warehouses.types.*`, `inventory.catalog.skippedReason.*`,
  `inventory.warehouses.stocks.batches.status.*`); systematize it. Approach is OPEN (O4).
  *Coverage has not been measured* — a canonical enum-to-key inventory is the first step, not a
  claim to restate.
- **Dead key cleanup — `leaveAssign.errors.*`.** Of the six defined keys
  (`src/i18n/locales/{en,ar}/leaveAssign.ts`), only `leaveAssign.errors.forbidden` is
  referenced in code. Remove the unused `load`, `generate`, `update`, `negativeRemaining`,
  `noActiveLeaveTypes` (or wire them up) — in both `en` and `ar`.

## 7. Fixed Assets — accounting follow-ons

The Fixed Assets module is **built, backend and frontend** (D46–D52, schema in
[modules/ASSETS.md](modules/ASSETS.md)) — see [PROJECT](PROJECT.md). It sits deliberately *below*
the future P&L/accounting module, so what remains is exactly what that module unblocks:

- **Depreciation.** No depreciation schedule, method, or calculation exists. "Current value" today
  is remaining quantity × original unit cost.
- **Profit / ROI / payback reporting.** No cost-coverage or return-on-investment calculation
  exists anywhere in the assets code, backend or frontend. Blocked on the P&L module (O10).

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
- **Multi-menu support**: `Menu` entity (e.g. breakfast vs. dinner
  menus) if/when a tenant needs more than one active menu.

> Recipe versioning/history and product variants/add-ons were previously listed here and are
> now **built** — immutable recipe versions with history reads, and parent/variant products with
> add-on suggestions projected into the cashier menu. Add-ons are independent order lines; a
> generic modifier-group/option engine still does not exist and has not been scoped.

## Device module — follow-ups (not blocking)

- No DB constraint yet enforcing "one warehouse per branch" (`uk_warehouse_branch_id`) —
  currently a convention, not enforced. Add when multi-warehouse-per-branch becomes real, or
  drop the item if one-warehouse-per-branch is no longer the intended invariant.
- `X-Branch-Id` is trusted as a plain header post-login, not cryptographically bound to the
  device secret per request (see DECISIONS D33). The POS sends its cached device branch as a
  plain header on JWT-authenticated requests and `OrderController` accepts it directly. Upgrade
  path: signed device JWT with a `branchId` claim, verified per request like user JWTs already are.
- Devices admin page nav placement/naming was fixed manually post-Codex-run. Today `/branches`
  and `/devices` are adjacent top-level routes while warehouses sit under inventory. Whether that
  matches the intended information architecture is a product judgment, not settleable from code —
  confirm with the product owner or close the item.
