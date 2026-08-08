# CONVENTIONS

> Code-writing rules for both agents, grounded in existing code. When in doubt, mirror the
> nearest existing sibling file. Invariants that must never be broken are in
> [DECISIONS](DECISIONS.md); this doc is style + architecture.

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
- `@Version` is used on exactly one entity (`StockBalance`) for optimistic locking — don’t add
  it elsewhere without cause.

### Money / quantity math
- **Always `BigDecimal`, `scale = 6`, `RoundingMode.HALF_UP`.** The constants `SCALE = 6` /
  `ROUNDING = HALF_UP` are redeclared in each service that does math
  (`InventoryLedgerService`, `StockBalanceService`, `StockBatchService`, `UomConversionService`,
  `PurchaseInvoiceService`, `PurchaseReturnService`). Match them exactly — a different
  scale/rounding will diverge from stored values.
- Percent-or-amount duality (purchase discount/tax): if both supplied, **percent wins**; derive
  the other. See `PurchaseInvoiceService.calculateLine` / `calculateInvoiceTotalsFromLines`.

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
        .movementDate(invoice.getReceiptDate().atStartOfDay())
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

### Exception handling — the current convention
Every thrown exception must extend one of the **six** `common` base classes, which all extend
the abstract `AppException`:
`BusinessException`, `ResourceNotFoundException`, `ValidationException`,
`AuthenticationException`, `AuthorizationException`, `ExternalServiceException`.
- Each module defines its **own** `{Module}ErrorCode implements ErrorCode` (`getCode()` +
  `getDefaultStatus()`). Inventory uses `InventoryErrorCode`. Never reuse another module’s enum;
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
  `src/types/`.
- Functional components + hooks. Data loads live in `useCallback` + `useEffect`; local UI state
  in `useState`.

### i18n — mandatory
- No hardcoded user-facing strings. Use `const { t, locale } = useTranslation()` and a key from
  the feature dictionary in `src/i18n/locales/{en,ar}/<feature>.ts` — **every key exists in both
  `en` and `ar`.**
- Backend errors → user text **only** through `translateApiError` (`src/utils/errors.ts`), which
  reads `errorCode` + `params`. Never render the server `message`.
- Enum values get their own translation keys (e.g. `inventory.warehouses.types.CENTRAL`,
  `inventory.warehouses.stocks.batches.status.open`) — don’t print raw enum names.

### Styling
- Plain CSS, **BEM** class names: `block`, `block__element`, `block--modifier`
  (e.g. `ready-material-cell__primary`). No CSS-in-JS, no utility-class framework.
- Colors/spacing via `--color-*` custom properties only (`var(--color-primary)`,
  `var(--color-text-muted)`, `var(--color-border)`). No hardcoded hex.
- Icons from `lucide-react`, outline set: `import { Plus, Search, Pencil } from 'lucide-react'`.

### RTL / bilingual
- Layout must work under `dir="rtl"`. Use logical CSS (start/end) rather than hardcoded
  left/right; don’t assume LTR.
- Display names honor English/Arabic via the existing helpers
  (`getInventoryLocalizedName`, `displayArabicName`) keyed off `locale`.
