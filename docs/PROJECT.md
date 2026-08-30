# PROJECT — Restaurant SaaS

> Ground-truth overview for both collaborating agents. Grounded in the real code as of
> this pass. When this file and older notes disagree, the code wins — flag the drift.
> Companion docs: [ROADMAP](ROADMAP.md), [DECISIONS](DECISIONS.md),
> [CONVENTIONS](CONVENTIONS.md), [REVIEW](REVIEW.md), [modules/](modules/),
> [docs/reviews/](docs/reviews/).

## Vision

A multi-tenant SaaS platform for restaurants: one backend serving many tenants, each with
its own warehouses, materials, staff, and (soon) orders. Every tenant-owned row carries a
`tenant_id`; some reference data (UOMs, material categories, the material catalog) is global
and shared read-only across tenants. The UI is bilingual (English + Arabic) and RTL-aware.

## Repositories

| Repo | Role | Stack |
|---|---|---|
| `restaurant-saas` (this repo) | Backend API | Spring Boot / Java 21 / PostgreSQL / Flyway / JPA / Lombok / Spring Security + JWT / Spring Retry; packaged as a **war**, port `2020` |
| `restaurant-saas-web` | Admin/tenant web app | React 19 / TS / React Router v7 / plain CSS + BEM / `--color-*` vars / Lucide (outline) / axios / `useTranslation()` / RTL |
| `restaurant-saas-panel` | SysAdmin panel (global catalog, UOMs) | React |
| `restaurant-saas-client-web`, `restaurant_saas_mobile` | Customer-facing surfaces | — |

This doc set lives in the backend repo because every hard invariant is a backend inventory
rule and the frontend conventions are captured here too, so a single reviewer checklist
covers both sides.

## Backend module map

Root package: `com.smart.restaurant_saas`

| Module | State | Notes |
|---|---|---|
| `inventory/` | **Built** (the mature module) | Warehouses, materials, categories, UOM, stock balances, purchase invoices/returns, physical counts, waste, batch-based FIFO costing, an append-only ledger. Feature-based sub-packages. |
| `auth/` | Built | JWT auth (`JwtAuthenticationFilter`, stateless). Has a minimal `AuthErrorCode`. |
| `rbac/` | Built | Roles/permissions, seeded via migrations; `@securityService.hasPermission(...)`. |
| `tenant/` | Built | `TenantHeaders.X_TENANT_ID` etc. |
| `user/` | Built | App users. |
| `branch/` | Built | `Branch` entity (referenced by `Warehouse`). |
| `hr/` | Built | Employees, jobs, leave requests/types, payroll surfaces. |
| `job/` | Built | Background/scheduled job support. |
| `common/` | Built | Base entities + the structured exception hierarchy. |
| `config/` | Built | `SecurityConfig`, `CorsConfig`, `OpenApiConfig`. |
| **Orders** | **Not started** (design only) | See [ROADMAP](ROADMAP.md) and [modules/ORDERS.md](modules/ORDERS.md). |
| **Assets** | **Built** (backend + frontend) | `V16__assets.sql`. Asset lines are **create/delete only** — no update endpoint, by design (D110). See [modules/ASSETS.md](modules/ASSETS.md). |

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
- Batch-based average costing: average cost is derived from OPEN batches only; a physical-count
  surplus opens a batch at the current average; FIFO shortfall is valued at the current average.
- Test suite exists: unit tests for every core service + controller security/contract tests
  (`src/test/java/.../inventory/**`).

**Present but NOT wired (scaffolding — verify before building on):**
- `InventoryTransfer` / `InventoryTransferLine` — entities + `TransferStatus` enum exist, no
  operation service/controller.
- `OrderConsumptionEvent` — entity + repository + `IdempotencyScope.ORDER_CONSUMPTION_EVENT`
  exist; no consumption-posting service. This is the staging table the Orders "Hybrid Ledger"
  will aggregate. See [modules/ORDERS.md](modules/ORDERS.md).
- `DocumentHistory` — entity exists; no repository/service references it currently.

## Frontend state (`restaurant-saas-web`)

- Feature pages under `src/pages/<feature>/`; shared UI in `src/components/ui/`; axios wrappers
  in `src/services/`; endpoint maps in `src/api/`; types in `src/types/`; line schemas in
  `src/schemas/`.
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

## Known drift / caveats

- The older `docs/PROJECT_SKILL.md` documents an `inventory/service/{core,operation,setup}`
  + `inventory/entity` layout and a **running weighted-average** cost formula. The code has
    since moved to **feature-based sub-packages** and to **batch-derived** average cost. Treat
    `PROJECT_SKILL.md` as historical; [CONVENTIONS](CONVENTIONS.md) and [DECISIONS](DECISIONS.md)
    reflect the current code.
- Exception-handling migration is **in progress**: 7 inventory services still use the legacy
  `ApiException` / deprecated `BusinessException(String)`. See [ROADMAP](ROADMAP.md).
- **Line discount and tax** are accepted by the purchase-invoice line UI's data model but
  discarded by the API. The fields were removed from the line form rather than shipped as a
  silent no-op; the backend gap is tracked in
  `restaurant-saas-web/docs/DEFERRED_BACKEND_GAPS.md`.
- **Displayed sums need not add up.** Money is stored at six decimals and displayed at two, so a
  column of rounded line values can differ from the rounded document total by a piastre. Current
  data exhibits no such case; the calculation permits it. Tracked in the same file.
- A **purchase return uses its original line's UOM** in the UI (D108), but the backend still
  accepts and converts a different `uomId` — the lock is a UI convention, not an enforced
  invariant. Another client could still submit one.