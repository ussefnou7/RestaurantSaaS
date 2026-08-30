# PROJECT — Restaurant SaaS

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155`, POS `03b0e81`
> on 2026-08-30 by Claude Code (doc drift audit — [claude/DOC_DRIFT_AUDIT.md](../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as those commits.

> Ground-truth overview for both collaborating agents. Grounded in the real code as of
> this pass. When this file and older notes disagree, the code wins — flag the drift.
> Companion docs: [ROADMAP](ROADMAP.md), [DECISIONS](DECISIONS.md),
> [CONVENTIONS](CONVENTIONS.md), [REVIEW](REVIEW.md), [LOSS_PREVENTION](LOSS_PREVENTION.md),
> [modules/](modules/), [business-flows/](business-flows/),
> [purchase-return-api-contract](purchase-return-api-contract.md).
> Historical, do not act on: [PROJECT_SKILL](PROJECT_SKILL.md).

## Vision

A multi-tenant SaaS platform for restaurants: one backend serving many tenants, each with
its own warehouses, materials, staff, and orders. Every tenant-owned row carries a
`tenant_id`; some reference data (UOMs, material categories, the material catalog) is global
and shared read-only across tenants. The UI is bilingual (English + Arabic) and RTL-aware.

## Repositories

| Repo | Role | Stack |
|---|---|---|
| `restaurant-saas` (this repo) | Backend API | Spring Boot / Java 21 / PostgreSQL / Flyway / JPA / Lombok / Spring Security + JWT / Spring Retry; packaged as a **war**, port `2020` |
| `restaurant-saas-web` | Admin/tenant web app | React 19 / TS / React Router v7 / plain CSS + BEM / `--color-*` vars / Lucide (outline) / axios / `useTranslation()` / RTL |
| `restaurant-pos` | Cashier POS (standalone) | React / TS with its **own OPFS-backed SQLite database** (worker client), an order outbox for offline durability, and a backend API client that posts to `POST /api/orders` |
| `restaurant-saas-panel` | SysAdmin panel (global catalog, UOMs) | React |
| `restaurant-saas-client-web`, `restaurant_saas_mobile` | Customer-facing surfaces | — |

This doc set lives in the backend repo because every hard invariant is a backend inventory
rule and the frontend conventions are captured here too, so a single reviewer checklist
covers both sides.

## Backend module map

Root package: `com.smart.restaurant_saas`

| Module | State | Notes |
|---|---|---|
| `inventory/` | **Built** (the mature module) | Warehouses, materials, categories, UOM, stock balances, purchase invoices/returns, physical counts, waste, **order-consumption documents**, batch-based FIFO costing, an append-only ledger, and six report surfaces (low stock, valuation, shrinkage, waste analysis, purchase-price drift, loss comparison). Feature-based sub-packages; 52 test files. |
| `order/` | **Built** | Unified `Order` entity (one table) with `orderType` / `orderSource` / final-state `status`. Permission-protected `/api/orders`, `/api/orders/reports` (sales over time / hour / product / payment method), and `/api/order-requests` intake. Seven test files cover the core service, security, persistence, and reports — **intake is wired but untested**. See [modules/ORDERS.md](modules/ORDERS.md). |
| `assets/` | **Built** (backend + frontend) | `V16__assets.sql`. Five controllers: assets, asset lines, disposals, maintenance, reports; eight backend test files. Asset lines are **create/delete only** — no update endpoint, by design (D110). See [modules/ASSETS.md](modules/ASSETS.md). |
| `menu/` | **Built** | Menu, menu categories, products (including parent/variant products), immutable recipe versions, and product add-ons. 5 controllers, 10 test files. Add-ons are independent order lines — there is no generic modifier-group engine. |
| `table/` | **Built** | Tables, sections, and layout. 2 controllers, 4 test files. |
| `pos/` | **Built** | Cashier shifts. 1 controller, 1 test file. Distinct from the separate `restaurant-pos` app. |
| `device/` | **Built** | Device administration and device login. 1 controller, 2 test files. Device login is deliberately public; the management endpoints are permission-protected. |
| `loyalty/` | **Built** | Customers. 1 controller, 2 test files. |
| `auth/` | Built | JWT auth (`JwtAuthenticationFilter`, stateless). Has a minimal `AuthErrorCode`. `POST /api/auth/login` is deliberately `permitAll`; `GET /api/auth/me` is covered by the global authenticated rule, so neither carries `@PreAuthorize`. |
| `rbac/` | Built | Roles/permissions, seeded via migrations; `@securityService.hasPermission(...)`. Surfaces: `/sys-admin/rbac`, `/api` permission lookup, and tenant read-only `/api/rbac`. |
| `tenant/` | Built | `TenantHeaders.X_TENANT_ID` etc. Permission-protected `/api/admin/tenants`; includes tenant-timezone coverage. |
| `user/` | Built | App users. System-admin `/api/admin/tenants/{tenantId}/users` and tenant `/api/users`. |
| `branch/` | Built | Repository, service, permission-protected CRUD/status controller at `/api/branches`, and tests. `Branch` is referenced by `Warehouse`. |
| `hr/` | Built | Employees, leave types, leave balances, leave requests, effective-dated salary records, and addition/deduction records. **No payroll engine** — see below. Jobs live in `job/`, not here. |
| `job/` | Built | **HR employment positions**, exposed at permission-protected `/api/jobs`. This is *not* background/scheduled job infrastructure — scheduling lives in `config/SchedulingConfig` plus feature schedulers such as `OrderConsumptionBatchingScheduler`. Its tests live in `HrServiceTest`, not under a `job/` test directory. |
| `common/` | Built | Base entities + the structured exception hierarchy. Infrastructure — no feature controller by design; `GlobalExceptionHandler` is its HTTP surface. |
| `config/` | Built | `SecurityConfig`, `CorsConfig`, `OpenApiConfig`, `SchedulingConfig`. |

### HR is deliberately thin

HR is manager-entered administration: employees and their jobs, leave types/balances/requests,
effective-dated salaries, and additions/deductions. There is **no** payroll calculation, pay run,
payslip, attendance or biometric capture, hours, overtime, or HR shift engine. All leave-request
routes are class-gated to owner / branch manager and create against an explicit employee id;
requests default to `APPROVED` and there is no employee self-service submission path. `Employee`
carries an optional `userId` link to an app user, but that link does not create a self-service
role or flow.

`SalaryService` and `SalaryAdjustmentService` are wired but have no test references.

## Inventory: built vs stubbed

**Built and wired (service + controller + tests):**
- Master data: `WarehouseService`, `MaterialService`, `MaterialCategoryService`,
  `MaterialCatalogService`, `SupplierService`, `UomService` (+ SysAdmin `PanelUomController`).
- Stock engine (`inventory/core/`): `InventoryLedgerService` (sole writer to
  `inventory_transaction`), `StockBalanceService` (sole writer to `stock_balance` qty/avg),
  `StockBatchService` (sole writer to `stock_batch`, FIFO), `UomConversionService`,
  `IdempotencyService`, `OpeningBalanceService`.
- Documents: `PurchaseInvoiceService`, `PurchaseReturnService`, `PhysicalCountService`,
  `WasteService` — each DRAFT → COMPLETE → POSTED with cancel/uncomplete, and (invoice/return)
  **unpost**.
- **Order consumption** (`inventory/orderconsumption/`): `OrderConsumption` documents with
  order-line and material rows are the staging model. Saving a `COMPLETE` order synchronously
  calls `recordCompletedOrder`; a `CANCELLED` order creates no consumption.
  `OrderConsumptionService` claims, processes, aggregates, and posts per-material
  `CONSUMPTION_SUMMARY` ledger commands. Document states are `PENDING`, `IN_PROGRESS`,
  `PARTIAL`, `POSTED`, `CONFLICT`. Covered by unit, integration, and security tests.
- **Consumption batching scheduler** (`OrderConsumptionBatchingScheduler`): fires when the
  pending line count reaches 50 **or** the oldest pending line is at least 8 hours old, polled
  every 60 seconds. Count, age, poll interval, and enablement are **application properties**
  (`application.yml`), not per-tenant settings — there is no tenant threshold entity.
- **Availability** = posted balance minus outstanding consumption. The outstanding quantity is
  computed by `OrderConsumptionAvailabilityService` (recipes for `PENDING`; unconsumed material
  rows for `PARTIAL` / `CONFLICT`; none for `POSTED`; `IN_PROGRESS` excluded) and consumed only
  by `StockBalanceService`.
- Batch-based average costing: average cost is derived from OPEN batches only; a physical-count
  surplus opens a batch at the current average; FIFO shortfall is valued at the current average.
- Test suite exists: unit tests for every core service + controller security/contract tests
  (`src/test/java/.../inventory/**`).

**Present but NOT wired (scaffolding — verify before building on):**
- `InventoryTransfer` / `InventoryTransferLine` — entities + `TransferStatus` enum only. No
  repository, service, controller, or tests. The admin web app *does* have routed transfer pages
  and a service, but that service calls `/api/inventory/transfers` routes that **do not exist**
  — a non-functional shell, not a built feature (see Known defects).
- `DocumentHistory` — entity + enum only; nothing references it. Also violates the timestamp
  convention (see Known defects) and must be fixed before wiring.

## Frontend state (`restaurant-saas-web`)

- Feature pages under `src/pages/<feature>/`; shared UI in `src/components/ui/`; axios wrappers
  in `src/services/`; endpoint maps in `src/api/`; types in `src/types/`; line schemas in
  `src/schemas/`.
- **Orders** are routed with real services (`src/services/orderService.ts`), covering orders and
  order requests.
- **Assets frontend is built and routed:** asset list / detail / new registration, a disposal
  form and a disposals list, a maintenance form and a maintenance list, and reports — all calling
  the real `assetService`.
- i18n: per-feature dictionaries under `src/i18n/locales/{en,ar}/`, consumed via
  `useTranslation()` → `{ t, locale }`. Backend errors are turned into user text by
  `src/utils/errors.ts` (`translateApiError`) from `errorCode` + `params` — the server
  `message` is explicitly logs-only and never rendered. `useTranslation` has **no**
  `defaultValue` support; a missing key renders raw.
- Styling: plain CSS with BEM class names (`block__element--modifier`) and `--color-*`
  custom properties; icons from `lucide-react` (outline set).
- **Layout width (D109):** page containers are uncapped; width protection sits on the content
  element that fails when stretched. See [CONVENTIONS](CONVENTIONS.md) → Layout and width.

### Document lines — schema-driven

Purchase invoices, purchase returns, waste documents and fixed assets all render their lines
from a single `LineSchema` (`src/schemas/`) through one controller hook,
`useDocumentLines`. Two views read the same config and the same state:

- **Grid view** — the inline table, for fast sequential entry.
- **Form view** — one line at a time as a full field form, reached from the Grid or from the
  `?view=form&line=<id>` query. This is where fields that cannot fit a grid row live
  (`showIn: ['form']`), e.g. purchase-return line notes.

Line tables scroll horizontally with a pinned actions column that works in both directions.

**Deliberately outside this abstraction:** stock balances (not document lines at all) and
physical counts (counted vs. system quantity and variance — different semantics). Per D13, do
not stretch the schema to cover them.

## POS boundary (`restaurant-pos`)

The cashier POS is a separate system with its own OPFS-backed SQLite worker database. It sends
`POST /api/orders` with tenant / branch / user headers and a JWT. The payload carries
`orderType`, a fixed `orderSource` of `POS`, a **final** status, cancellation details, payment
method, dine-in `tableId`, a client-local `orderDate`, lines (`productId`, quantity, unit price),
customer phone/name, an idempotency key, and a display order number. The backend does **not**
accept a POS-local `customerId` — it resolves the customer by phone/name.

- **Offline durability is built.** One POS screen maps dine-in / takeaway / delivery onto the
  unified payload, and both completion and cancellation generate an idempotency key before the
  fast attempt and fall back to an idempotent SQLite outbox on network failure.
- **Printing is not built.** "Print/reprint" opens a visual receipt preview only; there is no
  ESC/POS printer adapter. Tracked in [ROADMAP](ROADMAP.md) §1.
- **Cancellations** arrive as a final `CANCELLED` order on the same endpoint with a stage, an
  enumerated reason, and a note (required for `OTHER`). The backend requires and persists stage
  and reason. There is no dedicated `cancelledAt` — a freshly generated client-local `orderDate`
  is the only event time received (see Known defects). Cancelled orders produce **no** waste and
  **no** consumption; only `COMPLETE` triggers consumption.

## Known drift / caveats

- The older `docs/PROJECT_SKILL.md` documents an `inventory/service/{core,operation,setup}`
  + `inventory/entity` layout and a **running weighted-average** cost formula. The code has
  since moved to **feature-based sub-packages** and to **batch-derived** average cost. Treat
  `PROJECT_SKILL.md` as historical; [CONVENTIONS](CONVENTIONS.md) and [DECISIONS](DECISIONS.md)
  reflect the current code.
- **Exception-handling migration is nearly finished.** Inventory and RBAC are fully migrated
  (0 files each); Auth has 1 file (`CurrentUserService`) and Tenant has 3 (`CurrentTenantProvider`,
  `TenantCodeService`, `TenantService`). All remaining calls are `ApiException` — no deprecated
  `BusinessException(String)` call survives in those four areas. See [ROADMAP](ROADMAP.md) §4.
- **Per-line discount is backend-only; per-line tax does not exist.** The backend persists and
  calculates purchase-invoice line discount percent/amount, but the admin UI's line model and
  form expose neither discount nor tax. Per-line **tax** is absent from both sides. Invoice-level
  discount and tax are built. Tracked in `restaurant-saas-web/docs/DEFERRED_BACKEND_GAPS.md`.
- **Displayed sums need not add up.** Money is stored at six decimals and displayed at two, so a
  column of rounded line values can differ from the rounded document total by a piastre. The
  calculation permits it. Whether live data currently contains such a row is a data observation,
  not a source-verifiable claim — do not assert either way without a dated database check.
  Tracked in the same file.
- A **purchase return uses its original line's UOM** in the UI (D108), but the backend still
  accepts and converts a different `uomId` — the lock is a UI convention, not an enforced
  invariant. Another client could still submit one.
- **D20 is recorded as DECIDED but is not implemented.** The decision maps cancellation stages to
  waste/consumption consequences; the code performs no waste mapping for any cancelled order.
  It has been moved to [DECISIONS](DECISIONS.md) → OPEN.

### Known defects (current behaviour, *not* intended behaviour)

Documented here so nobody reads them as design. They are code bugs and are tracked separately as
their own tasks — do not normalise them into any convention or module description.

- Tenant `UomController` has **no** `@PreAuthorize`, and both purchase `POST /{id}/post`
  transitions omit the annotation their adjacent transitions carry. Global authentication still
  applies, but that is not the documented permission gate ([CONVENTIONS](CONVENTIONS.md) →
  Controllers stands as written).
- The admin transfers UI calls `/api/inventory/transfers` routes that do not exist on the backend.
- The availability subtraction is duplicated across the list and single-balance mapping paths in
  `StockBalanceService`, so it is not literally computed in one shared method.
- Orders have no `cancelledAt`; the cancellation time is a client-generated `orderDate`.
- `DocumentHistory` uses a `@PrePersist` hook calling `LocalDateTime.now()`, violating two
  timestamp rules in [CONVENTIONS](CONVENTIONS.md). It is dormant, so it stamps nothing today.
</content>
