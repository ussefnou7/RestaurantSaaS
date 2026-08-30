# CONVENTIONS

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155` on 2026-08-30 by
> Claude Code (doc drift audit — [../claude/DOC_DRIFT_AUDIT.md](../claude/DOC_DRIFT_AUDIT.md)).
> This file states **rules**, not system state, so a stale stamp does not make a rule wrong —
> it means the rules have not been re-checked for code that violates them. Two known violations
> stand as of this stamp, and the code is what is wrong in both: `DocumentHistory`'s
> `@PrePersist` + `LocalDateTime.now()` (see Time, below) and the missing `@PreAuthorize` on
> tenant `UomController` and both purchase `post` transitions (see Controllers, below).

> Code-writing rules for both agents, grounded in existing code. When in doubt, mirror the
> nearest existing sibling file. Invariants that must never be broken are in
> [DECISIONS](DECISIONS.md); this doc is style + architecture.

---

## Documentation

### Doc currency
Any prompt or task that lands a module, a new endpoint, or a change in a module's
built/scaffolding state must update the [PROJECT](PROJECT.md) module map **in the same pass**,
and remove the corresponding entry from [ROADMAP](ROADMAP.md). A module is not done until the
map says what it is. Prompt documents that ship a feature end with this step.

- The "Last verified against code" stamp at the top of `PROJECT.md` and `ROADMAP.md` names the
  commit the claims below it were checked against. If you correct a claim, move the stamp; if you
  read a stale stamp, distrust the file and verify against code before acting on it.
- `ROADMAP.md` contains only work that has genuinely not happened. Anything built moves to
  `PROJECT.md`.
- Every state claim in either file traces to code, not to another document. Never carry a claim
  forward because it sounds plausible.

---

## Backend (`restaurant-saas`)

### Package layout — feature-based sub-packages
Inventory is organized by **feature**, not by layer. Each feature package holds its entity,
controller, service(s), and a `dto/` sub-package together:

```
inventory/
  warehouse/  material/  category/  uom/  stock/  purchase/  physicalcount/  transfer/
  waste/  batch/  backfill/
  core/       — the stock engine + shared enums (core/enums/)
  mapper/     — hand-written entity→response mappers (@Component)
  repository/ — Spring Data JPA repositories
  service/setup/ — master-data CRUD services (Material/Category/Supplier/Warehouse)
```

- New table → `@Entity` in the owning feature package.
- New endpoint → controller in that package.
- New request/response → `<feature>/dto/`.
- Cross-cutting engine logic (ledger, balance, batch, UOM conversion, idempotency) lives in
  `inventory/core/`. Do **not** scatter stock mutation across feature services.

### Entities
- Tenant-owned rows extend `common/TenantAwareEntity` (adds non-null `tenantId` + audit).
- Rows that can be **global** extend `common/BaseEntity` and declare their own nullable
  `tenantId` (NULL = global): `Uom`, `MaterialCategory`. `MaterialCatalog` is global with no
  tenant column at all.
- Child/line rows queried only via their parent extend nothing (`PurchaseInvoiceLine`, …).
- IDs: `@GeneratedValue(IDENTITY) Long`. Money/quantity: `BigDecimal(precision=18, scale=6)`;
  percent: `(10,4)`. Bilingual text: `name` + `nameAr` (`name_ar VARCHAR(255)`).
- `@Version` is used on exactly one entity (`StockBalance`) for optimistic locking — don't add
  it elsewhere without cause.

### Money / quantity math
- **Always `BigDecimal`, `scale = 6`, `RoundingMode.HALF_UP`.** The constants `SCALE = 6` /
  `ROUNDING = HALF_UP` are redeclared in each service that does math
  (`InventoryLedgerService`, `StockBalanceService`, `StockBatchService`, `UomConversionService`,
  `PurchaseInvoiceService`, `PurchaseReturnService`). Match them exactly — a different
  scale/rounding will diverge from stored values.
- Percent-or-amount duality (purchase discount/tax): if both supplied, **percent wins**; derive
  the other. See `PurchaseInvoiceService.calculateLine` / `calculateInvoiceTotalsFromLines`.
- **Six stored decimals mean displayed sums need not add up.** A line stored as `90.625` displays
  as `90.63`; three of them read as `271.89` while the document total reads `271.88`. The record
  is always the server's value and the UI only rounds for presentation — but when a screen shows
  both line values and a total, decide deliberately whether the total is the sum of rounded lines
  or the rounded sum, and say which.

### The stock engine — who may write what
Move stock **only** by building a `LedgerCommand` and calling
`InventoryLedgerService.record(cmd)`. See [DECISIONS](DECISIONS.md) D3–D5, D10, D11.

```java
LedgerCommand cmd = LedgerCommand.builder()
        .tenantId(tenantId).warehouseId(warehouseId).materialId(materialId)
        .transactionType(InventoryTransactionType.PURCHASE)
        .direction(InventoryTransactionDirection.IN)
        .enteredQuantity(line.getQuantity())     // raw, per the entered/line UOM
        .enteredUomId(line.getUom().getId())
        .enteredUnitCost(line.getUnitCost())     // raw, per the entered UOM — do NOT pre-convert
        .referenceType("PURCHASE_INVOICE").referenceId(invoice.getId())
        .sourceInvoiceLineId(line.getId())       // set whenever available (returns need it)
        .movementDate(invoice.getReceiptDate().atStartOfDay(zone).toLocalDateTime())
        .createdBy(userId)
        .build();
        ledgerService.record(cmd);
```
- `inventory_transaction` writer: `InventoryLedgerService` only. It is append-only — never
  update/delete a row; correct via a `reverse(...)` row or a new transaction.
- `stock_balance.quantity` / `averageCost` writer: `StockBalanceService` only. Denormalized
  `lastPurchase*` / `lastCount*` fields are set by the owning operation service after posting.
- `stock_batch` writer: `StockBatchService` only (open on inbound, FIFO-deplete on outbound,
  source-batch deplete/restore for returns).
- Idempotency: unique `(tenant_id, idempotency_key)`; `IdempotencyService.findExistingId(...)`
  fast-path + `DataIntegrityViolationException` catch as the real guard.

### Time — every clock is a tenant's, never the server's (D101)
Timestamps are stored as **tenant-local wall clock** in `LocalDateTime` / `TIMESTAMP` columns.
Which wall clock is a property of the tenant, resolved by
`TenantTimeZoneService.zoneFor(tenantId)` — or `zoneFor(tenantId, branchId)` where a branch is in
scope, since a branch may override its tenant's zone.

- **`LocalDateTime.now()` is forbidden.** It reads the JVM's zone, so the value written depends on
  where the server happens to sit. Write `LocalDateTime.now(zone)`.
- **Write `date.atStartOfDay(zone).toLocalDateTime()`, not bare `atStartOfDay()`** — but know why.
  Bare `atStartOfDay()` does **not** read the JVM zone (it is `LocalDateTime.of(date, MIDNIGHT)`),
  so for a `LocalDateTime` column the two forms produce an identical value. The explicit form is
  required because it states which day boundary is meant, and because it is the form that stays
  correct if the value is ever converted to an `Instant`. Do not describe the bare form as a
  timezone bug — it is not one. `LocalDateTime.now()` is the real defect.
- **Audit columns are not your problem.** `createdAt` / `updatedAt` are stamped by
  `TenantTimestampListener` from the `tenantId` on the row being saved. Do not set them by hand and
  do not add `@PrePersist` hooks that do.
- **Business *dates* stay `LocalDate`** and are never converted. Only their conversion *to* a
  timestamp takes a zone.
- A missing tenant zone **throws**. There is no fallback, by design: a silent default writes a
  plausible-looking wrong row, which is the failure mode this rule exists to prevent.

### Exception handling — the current convention
Every thrown exception must extend one of the **six** `common` base classes, which all extend
the abstract `AppException`:
`BusinessException`, `ResourceNotFoundException`, `ValidationException`,
`AuthenticationException`, `AuthorizationException`, `ExternalServiceException`.
- Each module defines its **own** `{Module}ErrorCode implements ErrorCode` (`getCode()` +
  `getDefaultStatus()`). Inventory uses `InventoryErrorCode`. Never reuse another module's enum;
  never throw a raw `RuntimeException` or the bare `AppException`.
- Pass `errorCode` + a **debug** message (English, logs-only) + `ErrorParams.of(...)` carrying
  every dynamic value the FE needs to build the user message:

```java
throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
    "Only DRAFT invoices can be completed",                    // logs only
                            ErrorParams.of(
                                    "entityType", "PurchaseInvoice",
                                    "currentStatus", invoice.getStatus().name(),
        "requiredStatus", "DRAFT",
                "action", "complete"));
```
- **Do not** introduce new uses of the legacy `ApiException(HttpStatus, message)` or the
  deprecated `BusinessException(String)` — both exist only so un-migrated files compile
  (see [ROADMAP](ROADMAP.md) §4). New code uses the structured form above.

### Controllers
- Auth headers: `@RequestHeader("X-Tenant-Id") Long tenantId` (required); optional
  `@RequestHeader(value="X-User-Id", required=false) Long userId` for audit.
- Permissions:
  `@PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_PURCHASE_MANAGE')")`.
- URLs: `/api/inventory/<plural-kebab>`; CRUD via `GET /`, `GET /{id}`, `POST /` (201),
  `PUT /{id}`, `PATCH /{id}/activate|deactivate`. State transitions are POST sub-resources
  (`/{id}/complete`, `/post`, `/unpost`, `/cancel`, `/reconcile`, `/start`). Soft
  activate/deactivate and document cancel instead of hard delete on posted data.
- Swagger: class `@Tag(...)`, method `@Operation(summary, description)`.

### Migrations (Flyway)
- `V<n>__snake_case.sql`; **append the next integer** above the current max (gaps are fine, never
  reuse/insert). Idempotent-friendly (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`).
- `ddl-auto: validate` — Hibernate never alters schema; every column needs a migration first.
- Money/qty `NUMERIC(18,6)`, percent `NUMERIC(10,4)`; `uk_`/`idx_`/`chk_` naming; CHECK
  constraints mirror enums. Prefer a real child table over JSON (the one `jsonb` column,
  `waste_document` warnings, is a deliberate exception, not a precedent).

### Tests
Mirror existing tests: per-service unit tests + controller security/contract tests under
`src/test/java/.../inventory/**`. Cover the invariant you touch.

---

## Frontend (`restaurant-saas-web`)

### Structure
- Pages in `src/pages/<feature>/`; shared presentational components in `src/components/ui/`;
  axios service wrappers in `src/services/`; endpoint path maps in `src/api/`; TS types in
  `src/types/`; line schemas in `src/schemas/`.
- Functional components + hooks. Data loads live in `useCallback` + `useEffect`; local UI state
  in `useState`.

### i18n — mandatory
- No hardcoded user-facing strings. Use `const { t, locale } = useTranslation()` and a key from
  the feature dictionary in `src/i18n/locales/{en,ar}/<feature>.ts` — **every key exists in both
  `en` and `ar`.**
- `useTranslation` does **not** implement `defaultValue`. Passing one does not fall back — it
  renders the raw key on screen. Add the key to both dictionaries instead.
- Backend errors → user text **only** through `translateApiError` (`src/utils/errors.ts`), which
  reads `errorCode` + `params`. Never render the server `message`.
- Enum values get their own translation keys (e.g. `inventory.warehouses.types.CENTRAL`,
  `inventory.warehouses.stocks.batches.status.open`) — don't print raw enum names.
- One owner per error: the axios interceptor renders the translated toast for a failed mutation.
  Pages inspect the returned result to keep the form open; they do not raise a second toast.

### Styling
- Plain CSS, **BEM** class names: `block`, `block__element`, `block--modifier`
  (e.g. `ready-material-cell__primary`). No CSS-in-JS, no utility-class framework.
- Colors/spacing via `--color-*` custom properties only (`var(--color-primary)`,
  `var(--color-text-muted)`, `var(--color-border)`). No hardcoded hex.
- **No fallback inside `var()` either.** `var(--color-surface, #ffffff)` is still a hardcoded hex,
  and it defeats the checks written to catch an unresolved token — a guard that cannot fail is not
  a guard. `rg 'var\(--color-[^)]*,' src` must return nothing.
- Icons from `lucide-react`, outline set: `import { Plus, Search, Pencil } from 'lucide-react'`.
- An icon must describe what the action does. A dispose action that creates a write-off document
  takes `PackageX`, not a trash can — `Trash2` means delete.

### Layout and width (D109)
Page-level wrappers (`.page`, `.list-page`, `.reports-page`, `.module-hub`) carry **no**
`max-width`; they expand to the full usable width of `main.main-content`. Width protection belongs
on the **content element that fails when stretched**, never on the container.

- **Field grids** carry a `px` ceiling — `repeat(auto-fit, minmax(260px, 420px))` with
  `justify-content: start`. A text input past ~420px is unusable, so a wider viewport must add
  columns rather than widen fields.
- **Card and tile grids** with a variable item count that wraps across rows take
  `repeat(auto-fit, minmax(<floor>, 1fr))` and fill the row. Capping them only manufactures dead
  space at the inline end.
- **A small fixed set of tiles keeps a ceiling.** `auto-fit` collapses empty tracks, so four stat
  tiles sized in `1fr` stretch to ~560px each at 2560px (`.mini-stat-grid--compact` stays
  `minmax(200px, 300px)`).
- `.entity-detail-page` keeps its 1200px cap — form-dominant, with no data table to benefit.

Two `minmax` traps, both of which shipped as bugs before they were written down:

- `minmax(0, 1fr)` is **not** a ceiling. The `0` is the minimum and the maximum is uncapped; it is
  the idiom for letting an item shrink below `min-content`, nothing more.
- `minmax(200px, 300px)` **never grows toward its maximum.** A fixed max makes the track size to
  its content, clamped between the two. Only `1fr` absorbs free space.

Choose by item count and failure mode — never by copying the neighbouring rule.

### Document line tables
- Each line table sets its **own** `min-width` floor, tuned to its column count. A
  `table-layout: fixed` table at `width: 100%` never overflows, so `overflow-x` without a floor
  yields no scrollbar and no scrolling — the feature ships dead.
- The scroll container is `.table-wrap` with `overflow-x: auto` and
  `overscroll-behavior-x: contain`. Exactly one per table, never nested.
- The **actions** column is pinned: `position: sticky; inset-inline-end: 0` on the `th`/`td`
  (never the `tr`), with an opaque background **matching the row it sits in** — a transparent
  sticky cell lets content scroll through it, and one global surface token mismatches the header
  row and the hover state.
- The wrapper sets `scroll-padding-inline-end` from the **same variable** as the pinned column's
  width, so tabbing to a field never lands it underneath the pinned column. Define that variable
  on the wrapper: custom properties inherit downward only.
- Column widths come from `<colgroup><col>` — not inline styles, and not `fr` units, which have no
  meaning for table columns.

### Document lines — one schema, one controller
Purchase invoices, purchase returns, waste documents and fixed assets render their lines from a
single `LineSchema` and share `useDocumentLines`. Grid view and Form view read the same config and
the same state; neither owns state of its own.

- The schema is a **factory** — `createXLineSchema(deps)`, not a module-level const. Action
  handlers, option sources and lookup data are all page-scoped.
- `LineField` carries **both `id` and `key`**. `id` is unique within the schema and is what
  renderers use for React keys, error targeting and layout; `key` is the data binding. They are
  not the same thing — three purchase-return fields legitimately bind `quantity`.
- `labelKey`, never a literal string.
- `validate` returns an **errorCode**, not a message — the same contract as backend errors (D12).
- Cascades: the **dependent** field declares `dependsOn: [...]` and carries the handler. A field
  never handles its own change.
- `showIn: ['form']` is where fields that cannot fit a grid row live. That is the point of Form
  view.
- Operations return one exhaustive result type. Do not mix a boolean return with a thrown error —
  a caller then has to remember both, and four callers will not.
- Do **not** extend this to stock balances or physical counts; their line semantics differ (D13).

### RTL / bilingual
- Layout must work under `dir="rtl"`. Use logical CSS (start/end) rather than hardcoded
  left/right; don't assume LTR. Direction comes from the app — never a hardcoded `dir` attribute
  on a component.
- Currency and number formatting go through one shared formatter used by every view. A currency
  literal inside a renderer is the same class of defect as a hardcoded string.
- A directional control has **three** independent concerns, and each takes a different rule:
  - **Logic** — which boundary disables which action — is *direction-independent*. Derive
    `canPrev` / `canNext` from the action a control invokes, never from which side or icon hosts
    it.
  - **Icon** — *mirrors with direction*. Under RTL you advance leftward, so **next** takes
    `ChevronLeft` and **previous** takes `ChevronRight`. Pick from the app's locale, not from
    computed style.
  - **Position** — mirrors on its own with the document flow. If it does not, something is
    overriding it: look for a `direction: ltr` or a `flex-direction: row-reverse` on the container.

  Getting the logic right and leaving the icons LTR produces a navigator that works and reads
  backwards — the labels say next, the arrow points back, and nobody can say why it feels wrong.
- Display names honor English/Arabic via the existing helpers
  (`getInventoryLocalizedName`, `displayArabicName`) keyed off `locale`.

### Verification (D109)
A passing `npm run build` verifies nothing visual or interactive. TypeScript does not check
`max-width`; ESLint does not render a grid, engage a scroll container, or move focus.

Layout and interaction changes are verified by **rendered measurement** — 1280 / 1920 / 2560,
under both `dir="rtl"` + Arabic and `dir="ltr"` + English. If no browser is available, the change
ships marked **unverified**, with a numbered checklist of observable yes/no items. A build is
never cited as proof.

Before deleting behaviour in a refactor, diff it: a rule that exists in an imperative handler and
not in its replacement is gone, and it will pass every automated check on the way out.