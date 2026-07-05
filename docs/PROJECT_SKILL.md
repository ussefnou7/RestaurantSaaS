# PROJECT SKILL — Restaurant SaaS (Inventory Module)

> Generated reference for the `com.smart.restaurant_saas` codebase. Every section
> below references actual class/file names. Items that could not be confirmed from
> the files scanned are marked **"unclear — verify"**.
>
> Scope scanned: `inventory/**`, `common/**`, `config/**`, `pom.xml`, all Flyway
> migrations (`V1`–`V26`). The `auth`, `branch`, `hr`, `job`, `rbac`, `tenant`,
> `user` packages were **not** fully read — only their touch-points with inventory.

---

## Stack & Tools

- **Build tool:** Maven (`pom.xml`), packaging `war`.
- **Java version:** 21 (`<java.version>21</java.version>`).
- **Framework:** Spring Boot **4.0.6** (parent `spring-boot-starter-parent`).
- **Group/artifact/version:** `com.smart` / `restaurant-saas` / `0.0.1-SNAPSHOT`.
- **Key dependencies:**
  - `spring-boot-starter-webmvc`, `-data-jpa`, `-security`, `-validation`, `-actuator`
  - `spring-boot-starter-flyway` + `flyway-database-postgresql`
  - `org.postgresql:postgresql` (runtime)
  - `springdoc-openapi-starter-webmvc-ui` **3.0.3** (Swagger UI)
  - `io.jsonwebtoken:jjwt-api/impl/jackson` **0.12.6** (JWT auth)
  - `org.projectlombok:lombok` (optional, annotation processor configured in build)
  - `spring-boot-starter-tomcat` (provided — war deployment)
  - devtools (runtime, optional)
  - Test: `*-test` starters (actuator, data-jpa, flyway, security, validation, webmvc)
- **Runtime config** (`application.yml`):
  - Datasource: `jdbc:postgresql://localhost:5432/restaurant-saas`, user/pass `postgres/postgres` (env-overridable via `SPRING_DATASOURCE_*`).
  - `jpa.hibernate.ddl-auto: validate` — **schema is owned by Flyway, never by Hibernate.**
  - `flyway.validate-on-migrate: false`, `open-in-view: false`.
  - Server port `${SERVER_PORT:2020}` (**not 8080**).
  - JWT secret + `expiration-minutes: 1440` under `app.jwt`.
  - Swagger UI at `/swagger-ui.html`, API docs at `/api-docs`.

---

## Package Structure

Root: `src/main/java/com/smart/restaurant_saas/`

```
RestaurantSaasApplication.java   — Spring Boot entry point
ServletInitializer.java          — war bootstrap
auth/                            — JWT auth (JwtAuthenticationFilter in auth/security) [not fully scanned]
branch/                          — Branch entity + BranchRepository (referenced by Warehouse)
common/                          — cross-cutting base classes & error handling
config/                          — SecurityConfig, CorsConfig, OpenApiConfig
hr/                              — HR module [not scanned]
job/                             — [not scanned]
rbac/                            — roles/permissions [not scanned; seeded via migrations]
tenant/                          — TenantHeaders (X-Tenant-Id constant) [partially]
user/                            — app users [not scanned]
inventory/                       — THE module documented here
```

### `inventory/` sub-packages

| Package | Purpose | Where to add new files |
|---|---|---|
| `inventory/entity/` | JPA entities (tables) | New table → new `@Entity` here |
| `inventory/enums/` | Status/type enums persisted as `EnumType.STRING` | New enum here |
| `inventory/dto/request/` | Inbound DTOs (validated with Jakarta annotations) | New request body here |
| `inventory/dto/response/` | Outbound DTOs (`@Getter @Builder`, immutable `final` fields) | New response here |
| `inventory/dto/command/` | Internal service-to-service commands (`LedgerCommand`) | New cross-service command here |
| `inventory/mapper/` | Entity → response mappers (`@Component`, hand-written, no MapStruct) | New mapper here |
| `inventory/repository/` | Spring Data JPA repositories | New repo here |
| `inventory/service/core/` | **Foundational** services: ledger, stock balance, UOM, idempotency | Engine-level logic |
| `inventory/service/operation/` | **Document/transaction** services: purchase invoice, return, physical count, opening balance | Business operations that move stock |
| `inventory/service/setup/` | **Master-data** services: material, category, catalog, warehouse, supplier | CRUD of reference data |
| `inventory/controller/` | REST controllers | New endpoint here |
| `inventory/exception/` | `InventoryLedgerException`, `UomConversionException` | Domain exceptions → handled in `GlobalExceptionHandler` |

> **Where does Supplier CRUD live?** `SupplierService` is in `service/setup/`,
> `SupplierController` in `controller/`, DTOs in `dto/request|response/`,
> `SupplierMapper` in `mapper/`. Mirror this layout for any new master-data entity.

---

## Architecture Rules

### Service layer dependency chain (who calls whom)

```
Controllers
   └─> setup services      (MaterialService, MaterialCategoryService,
   │                         MaterialCatalogService, WarehouseService, SupplierService)
   │       └─> repositories only  (no ledger involvement)
   │
   └─> operation services  (PurchaseInvoiceService, PurchaseReturnService,
   │                         PhysicalCountService, OpeningBalanceService)
   │       ├─> InventoryLedgerService.record(LedgerCommand)   ← the ONLY way to move stock
   │       ├─> repositories (for document persistence + batch balance reads)
   │       └─> mappers
   │
   └─> core services
           InventoryLedgerService
               ├─> IdempotencyService            (read-only duplicate check)
               ├─> UomConversionService          (entered UOM → stock UOM)
               ├─> StockBalanceService.applyTransaction(...)   (MANDATORY tx propagation)
               └─> repositories (transaction, material, warehouse, uom)
           StockBalanceService  (also serves read queries for StockBalanceController)
           UomService           (served by UomController + PanelUomController)
```

**Hard rule:** Operation services never write `inventory_transaction` or mutate
`stock_balance.quantity`/`average_cost` directly. They build a `LedgerCommand` and
call `InventoryLedgerService.record(...)`. The ledger is the single writer.

### Who is allowed to write to which table

| Table | Sole/primary writer | Notes |
|---|---|---|
| `inventory_transaction` | **`InventoryLedgerService`** only | Comment in class: "The sole writer to the inventory_transaction table." |
| `stock_balance` (qty, average_cost) | **`StockBalanceService.applyTransaction`** | Called from within the ledger's transaction (`Propagation.MANDATORY`). |
| `stock_balance` (lastPurchasePrice/Date, lastCountDate/Qty) | operation services (PurchaseInvoiceService, PurchaseReturnService, PhysicalCountService) | These denormalized fields are updated by batch reads/saves in the posting step — **not** quantity/cost. |
| `stock_batch` | **`StockBatchService`** only | `createBatchFromInbound` opens a batch per inbound; `consumeFifo` depletes open batches oldest-first (reduces `remaining_quantity`, closes emptied batches). Both run inside the ledger's transaction (`Propagation.MANDATORY`). |
| `purchase_invoice` / `_line` | `PurchaseInvoiceService` | |
| `purchase_return` / `_line` | `PurchaseReturnService` | |
| `physical_count` / `_line` | `PhysicalCountService` | |
| `material`, `material_category`, `warehouse`, `supplier`, `uom` | matching setup/core service | |
| `material_catalog` | SysAdmin-managed (read-only from tenant side; imported via `MaterialCatalogService.importMaterials`) | |

### LedgerCommand usage pattern (real example)

`LedgerCommand` (`dto/command/LedgerCommand.java`) is an immutable `@Builder`.
Example from `PurchaseInvoiceService.post(...)`:

```java
LedgerCommand cmd = LedgerCommand.builder()
    .tenantId(tenantId)
    .warehouseId(warehouseId)
    .materialId(line.getMaterial().getId())
    .transactionType(InventoryTransactionType.PURCHASE)
    .direction(InventoryTransactionDirection.IN)
    .enteredQuantity(line.getQuantity())
    .enteredUomId(line.getUom().getId())
    .enteredUnitCost(line.getUnitCost())
    .referenceType("PURCHASE_INVOICE")
    .referenceId(invoice.getId())
    .transactionDate(invoice.getReceiptDate().atStartOfDay())
    .createdBy(userId)
    .build();
ledgerService.record(cmd);
```

`record(cmd)` then: validates → idempotency fast-path → resolves warehouse/material/uom
→ converts entered qty to stock UOM → computes `totalCost = unitCost * stockQuantity`
→ persists `InventoryTransaction` → calls `StockBalanceService.applyTransaction(saved)`.

`referenceType` string conventions seen in code: `"PURCHASE_INVOICE"`,
`"PURCHASE_RETURN"`, `"PHYSICAL_COUNT"`. Opening balance uses no referenceType but
an `idempotencyKey`.

### Batch processing pattern (real example)

When posting a document with many lines, fetch all balances in ONE query, build a
map, mutate, then `saveAll`. From `PurchaseInvoiceService.post(...)`:

```java
List<Long> materialIds = invoice.getLines().stream()
    .map(l -> l.getMaterial().getId()).toList();

Map<Long, StockBalance> balanceMap = stockBalanceRepository
    .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
    .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

for (PurchaseInvoiceLine line : invoice.getLines()) {
    StockBalance balance = balanceMap.get(line.getMaterial().getId());
    if (balance != null) {
        balance.setLastPurchasePrice(line.getUnitCost());
        balance.setLastPurchaseDate(purchaseDate);
    }
}
stockBalanceRepository.saveAll(balanceMap.values());
```

`StockBalanceRepository.findByWarehouseAndMaterials(...)` exists specifically for this.
`PhysicalCountService.reconcile` and `start` use the same map-then-saveAll pattern.

### Idempotency pattern (real example)

Two-layer: (1) read fast-path, (2) DB unique constraint as the real guard.

- Unique constraints: `uk_inventory_transaction_tenant_idempotency` (tenant_id +
  idempotency_key) and `uk_oce_tenant_idempotency` on `order_consumption_event`.
- `IdempotencyService.findExistingId(tenantId, scope, key)` reads the existing id
  (scope = `IdempotencyScope.INVENTORY_TRANSACTION` | `ORDER_CONSUMPTION_EVENT`).
- `InventoryLedgerService.saveWithIdempotencyGuard(...)` catches
  `DataIntegrityViolationException` on save and re-resolves the existing row:

```java
try {
    InventoryTransaction saved = transactionRepo.save(tx);
    stockBalanceService.applyTransaction(saved);
    return saved;
} catch (DataIntegrityViolationException ex) {
    if (idempotencyKey != null) {
        return idempotencyService
            .findExistingId(tenantId, IdempotencyScope.INVENTORY_TRANSACTION, idempotencyKey)
            .flatMap(transactionRepo::findById)
            .orElseThrow(() -> ex);
    }
    throw ex;
}
```

Concrete key example — `OpeningBalanceService`:
`"OPENING_" + tenantId + "_" + warehouseId + "_" + materialId` (one opening balance
per warehouse+material; re-entry returns the existing tx, flagged `idempotentHit`).

---

## Base Classes

### `common/BaseEntity` (`@MappedSuperclass`)
Provides audit columns + lifecycle hooks:
- `createdAt` (not null, not updatable), `updatedAt`, `createdBy`, `updatedBy` (all `Long` for the user ids).
- `@PrePersist onCreate()` sets `createdAt` if null; `@PreUpdate onUpdate()` sets `updatedAt`.

### `common/TenantAwareEntity extends BaseEntity`
Adds `tenantId` (`@Column(name="tenant_id", nullable=false)`). Use this for any
entity that is owned by exactly one tenant.

### When to use each

- **`TenantAwareEntity`** — tenant-scoped data with a non-null tenant:
  `Material`, `Warehouse`, `Supplier`, `StockBalance`, `InventoryTransaction`,
  `PurchaseInvoice`, `PurchaseReturn`, `PhysicalCount`, `PhysicalCountLine`,
  `InventoryTransfer`, `InventoryTransferLine`, `OrderConsumptionEvent`.
- **`BaseEntity`** (audit only, `tenant_id` nullable or absent) — entities that can
  be **global**:
  - `Uom` — declares its own nullable `tenantId` (NULL = global UOM, non-null = tenant-owned). Uniqueness via partial indexes `uk_uom_global_code` / `uk_uom_tenant_code`.
  - `MaterialCategory` — own nullable `tenantId` (NULL = global category, read-only to tenants).
  - `MaterialCatalog` — global catalog, **no tenant column at all** (`uk_material_catalog_code` on `code`).
- **No base class at all** — child/line entities that piggyback on a parent's id and
  are never queried tenant-independently: `PurchaseInvoiceLine`,
  `PurchaseReturnLine`, `DocumentHistory` (DocumentHistory declares its own
  `tenantId` + `@PrePersist`, extends nothing).

> **Exception rule of thumb:** if a row can be *global* (shared across tenants) it
> uses `BaseEntity` + a nullable `tenantId` (or none). Everything tenant-owned uses
> `TenantAwareEntity`.

---

## Entity Catalog

> All ids are `@GeneratedValue(IDENTITY)` `Long`. All money/qty are
> `BigDecimal(precision=18, scale=6)` unless noted. Percent fields are `(10,4)`.

### `Material` — table `material` (TenantAware)
- Fields: `code` (≤100, unique per tenant via `uk_material_tenant_code`), `name`, `nameAr`, `minimumStockLevel`, `active`, `notes`.
- Relations: `catalog` (optional `MaterialCatalog`), `category` (required), `stockUom` (required), `displayUom` (required).
- Rule: `code` immutable after create (enforced in `MaterialService.update`). `stockUom` = base unit for all calculations; `displayUom` = UI unit.

### `MaterialCategory` — table `material_category` (BaseEntity, nullable tenantId)
- `code` (≤100), `name`, `nameAr`, `active`, `sortOrder`. tenantId NULL = global (read-only to tenants).

### `MaterialCatalog` — table `material_catalog` (BaseEntity, NO tenant)
- Global, SysAdmin-managed. `code` unique (`uk_material_catalog_code`), `name`, `nameAr`, `active`, `sortOrder`.
- Relations: `category`, `defaultStockUom`, `defaultDisplayUom` (all required). Imported into tenant `Material` via `MaterialCatalogService`.

### `Uom` — table `uom` (BaseEntity, nullable tenantId)
- `code` (≤100), `name`, `nameAr`, `symbol` (≤50), `type` (`UomType`), `factorToBase` (required), `active`, `sortOrder`.
- Self-relation `baseUom` (NULL = this IS the base for its type). `factorToBase` converts this unit → base (base units have `factorToBase = 1`).
- Two tiers: global (tenantId NULL, SysAdmin) vs tenant-owned. Never hard-deleted if in use.

### `Warehouse` — table `warehouse` (TenantAware)
- `code` (unique per tenant `uk_warehouse_tenant_code`), `name`, `nameAr`, `type` (`WarehouseType`), `active`, `notes`.
- Optional `branch` (`com.smart.restaurant_saas.branch.Branch`). `code` immutable after create.

### `Supplier` — table `supplier` (TenantAware)
- `code` (unique per tenant `uk_supplier_tenant_code`), `name`, `nameAr`, `phone` (≤50), `email`, `address` (text), `taxNumber` (≤100), `active`, `notes`.
- `code` immutable after create. No hard delete — deactivate instead.

### `StockBalance` — table `stock_balance` (TenantAware)
- Unique `(tenant_id, warehouse_id, material_id)` = `uk_stock_balance_tenant_warehouse_material`.
- `quantity`, `averageCost`, `minimumQuantity`, `reorderPoint`, `maximumQuantity` (nullable), `lastTransactionDate`, `lastPurchasePrice`, `lastPurchaseDate`, `lastCountDate`, `lastCountQuantity`.
- Relations: `warehouse`, `material`, `uom` (all required).
- **`@Version version`** — optimistic locking. This is the ONLY entity with `@Version`.

### `InventoryTransaction` — table `inventory_transaction` (TenantAware)
- The immutable ledger. Unique `(tenant_id, idempotency_key)`.
- Key fields: `transactionType` (`InventoryTransactionType`), `direction` (`IN`/`OUT`), `enteredQuantity` + `enteredUom`, `stockQuantity` + `stockUom`, `unitCost`, `totalCost`, `referenceType`/`referenceId`, `transactionDate`, `idempotencyKey`, `reversesTransactionId`, `reasonCode`, `batchNumber`, `expiryDate`, `shiftId`.
- Indexed heavily (tenant+wh+material+date, type+date, reference, reverses). `@PrePersist` defaults `transactionDate`.
- Rule: never updated/deleted; corrections happen via a reversal row (`reverse(...)`) or new transactions.

### `PurchaseInvoice` — table `purchase_invoice` (TenantAware)
- Unique `(tenant_id, invoice_number)`. `status` (`DocumentStatus`, default DRAFT), `invoiceDate`, `receiptDate`.
- Money: `subtotal`, `discountPercent` (10,4), `discountAmount`, `taxPercent` (10,4), `taxAmount`, `totalAmount`, `paidAmount`, `paymentStatus` (`PurchasePaymentStatus`).
- Lifecycle audit: `postedToInventory`, `postedAt/By`, `completedAt/By`, `cancelledAt/By`, `cancelReason`, `createdBy`.
- Relations: `supplier` (optional), `warehouse` (required), `lines` (`@OneToMany cascade=ALL orphanRemoval=true`).

### `PurchaseInvoiceLine` — table `purchase_invoice_line` (NO base class)
- `quantity`, `unitCost`, `lineTotal` (gross = qty×unitCost), `discountPercent` (10,4), `discountAmount`, `lineNetTotal` (after discount), `notes`.
- Relations: `purchaseInvoice`, `material`, `uom`.

### `PurchaseReturn` — table `purchase_return` (TenantAware)
- Unique `(tenant_id, return_number)`. `originalInvoice` (required), `supplier` (optional), `warehouse` (required), `returnDate`, `reason` (`PurchaseReturnReason`), `status` (`DocumentStatus`), `subtotal`, `totalAmount`, same posted/completed/cancelled audit fields. `lines` cascade ALL/orphanRemoval.

### `PurchaseReturnLine` — table `purchase_return_line` (NO base class)
- `originalLine` (→ `PurchaseInvoiceLine`, required), `material`, `quantity`, `uom`, `unitCost`, `lineTotal`, `notes`.

### `PhysicalCount` — table `physical_count` (TenantAware)
- Unique `(tenant_id, code)` (code = `"PC-" + warehouseCode + "-" + scheduledDate`). `status` (`PhysicalCountStatus`), `scheduledDate`, `startedAt`, `frozenAt`, `reconciledAt/By`, `cancelledAt/By`, `cancelReason`, `hasLargeVariance`, `largeVarianceValue`, `notes`. `lines` cascade ALL/orphanRemoval.

### `PhysicalCountLine` — table `physical_count_line` (TenantAware)
- Unique `(physical_count_id, material_id)`. `expectedQuantity`, `countedQuantity`, `variance`, `unitCostAtFreeze`, `varianceValue`, `countedAt`, `adjustmentTransactionId`, `wasteTransactionId`, `actionTaken` (`CountLineAction`), `adjustedExpectedQuantity`, relations `physicalCount`, `material`, `uom`.

### `InventoryTransfer` — table `inventory_transfer` (TenantAware)
- Unique `(tenant_id, code)`. `status` (`TransferStatus`), `sourceWarehouse`, `destinationWarehouse` (both required), `requestedDate`, `dispatchedAt/By`, `receivedAt/By`, `cancelledAt`, `notes`.
- **No service/controller scanned for transfers** — entity + enum exist but the
  operation service appears **not yet implemented**. *unclear — verify before use.*

### `InventoryTransferLine` — table `inventory_transfer_line` (TenantAware)
- Unique `(transfer_id, material_id)`. `requestedQuantity`, `dispatchedQuantity`, `receivedQuantity`, `uom`, `unitCostSnapshot`, `dispatchTransactionId`, `receiveTransactionId`, `notes`.

### `OrderConsumptionEvent` — table `order_consumption_event` (TenantAware)
- Unique `(tenant_id, idempotency_key)`. `orderId`, `orderLineId`, `material`, `warehouse`, `quantity`, `uom`, `unitCostSnapshot`, `totalCostSnapshot`, `recipeId`, `businessDate`, `consumedAt`, `idempotencyKey`, `postedToLedger`, `postedTransactionId`, `reversesEventId`, `notes`. `@PrePersist` defaults `consumedAt`/`businessDate`.
- Only `OrderConsumptionEventRepository.findByTenantIdAndIdempotencyKey` + `IdempotencyService` reference it. No consumption-posting service scanned — *unclear — verify; appears to be a staging table for a not-yet-built consumption aggregation flow.*

### `DocumentHistory` — table `document_history` (NO base class; own tenantId)
- `documentType` (`DocumentType`), `documentId`, `action` (`DocumentHistoryAction`), `performedAt/By`, `details`. Table created in `V13` (later part of that migration). **No repository/service currently references it** (`DocumentHistoryRepository`/`DocumentHistoryService` were deleted per git history). *unclear — verify; currently appears dormant.*

---

## Enum Catalog

| Enum | Values | Used by |
|---|---|---|
| `DocumentStatus` | DRAFT, COMPLETE, POSTED, CANCELLED | PurchaseInvoice, PurchaseReturn status |
| `PhysicalCountStatus` | DRAFT, IN_PROGRESS, RECONCILED, CANCELLED | PhysicalCount status |
| `TransferStatus` | DRAFT, IN_TRANSIT, COMPLETED, CANCELLED | InventoryTransfer status |
| `PurchasePaymentStatus` | UNPAID, PARTIALLY_PAID, PAID | PurchaseInvoice.paymentStatus |
| `InventoryTransactionType` | OPENING_BALANCE, PURCHASE, PURCHASE_RETURN, TRANSFER_OUT, TRANSFER_IN, CONSUMPTION_SUMMARY, MANUAL_CONSUMPTION, WASTE, ADJUSTMENT, COUNT_ADJUSTMENT | InventoryTransaction.transactionType |
| `InventoryTransactionDirection` | IN, OUT | InventoryTransaction.direction |
| `CountLineAction` | PENDING, NO_DIFFERENCE, ADJUSTMENT, WASTE | PhysicalCountLine.actionTaken (WASTE only for negative variance) |
| `PurchaseReturnReason` | DAMAGED, WRONG_QUANTITY, WRONG_SPEC, EXPIRED, OTHER | PurchaseReturn.reason |
| `AdjustmentReasonCode` | DATA_ENTRY_ERROR, SYSTEM_MIGRATION, FOUND_STOCK, MISSING_STOCK, UNIT_CONVERSION_FIX, OTHER | reason codes for adjustments (free-string `reasonCode` on tx) |
| `WasteReasonCode` | EXPIRED, DAMAGED, SPOILED, CONTAMINATED, LOST, THEFT, OPERATIONAL_LOSS, CUSTOMER_RETURN, OTHER | waste reason codes |
| `UomType` | WEIGHT, VOLUME, COUNT, LENGTH | Uom.type (conversions only valid within same type) |
| `WarehouseType` | CENTRAL, BRANCH, KITCHEN, FREEZER, BAR, OTHER | Warehouse.type |
| `IdempotencyScope` | INVENTORY_TRANSACTION, ORDER_CONSUMPTION_EVENT | IdempotencyService routing |
| `MaterialImportSkipReason` | NOT_FOUND, INACTIVE_CATALOG_MATERIAL, ALREADY_IMPORTED, CODE_ALREADY_EXISTS | catalog import skip reasons |
| `DocumentType` | PURCHASE_INVOICE | DocumentHistory.documentType (only one value) |
| `DocumentHistoryAction` | COMPLETE, CANCEL | DocumentHistory.action |

> `AdjustmentReasonCode` / `WasteReasonCode` are enums but `InventoryTransaction.reasonCode`
> is a free `String(50)` — the enum values are the intended vocabulary, not a DB-enforced FK.

---

## Document Flows

### Purchase Invoice (`PurchaseInvoiceService`)
Status: **DRAFT → COMPLETE → POSTED**, or **→ CANCELLED** (from DRAFT/COMPLETE only).

- **create** → status DRAFT, `postedToInventory=false`; builds lines, runs
  `calculateLine` per line + `calculateInvoiceTotals`. No stock movement.
- **update** (DRAFT only) → clears + rebuilds all lines, recalculates totals.
- **complete** (DRAFT only) → status COMPLETE, sets `completedAt/By`. No stock movement.
- **post** (COMPLETE only, not already posted) → for each line emits a `PURCHASE`/`IN`
  `LedgerCommand` (stock in at unit cost), batch-updates `lastPurchasePrice`/`Date`
  on `StockBalance`, sets `postedToInventory=true`, `postedAt/By`, status POSTED.
  **Irreversible** — corrections via Purchase Return.
- **cancel** → status CANCELLED. POSTED invoices cannot be cancelled.
- Side effects: `inventory_transaction` rows (via ledger) + `stock_balance` qty/avg-cost
  (via `applyTransaction`) + denormalized last-purchase fields.

### Purchase Return (`PurchaseReturnService`)
Status: **DRAFT → COMPLETE → POSTED**, or **→ CANCELLED** (DRAFT/COMPLETE only).

- **create** → only against a **POSTED** invoice. Validates each return line against
  the original invoice line and `quantity ≤ original − alreadyReturned` (already-returned
  summed from POSTED returns via `findReturnedQuantitiesByInvoiceId`). Snapshots
  `unitCost` from the original line.
- **complete** → COMPLETE.
- **post** (COMPLETE only) → emits `PURCHASE_RETURN`/`OUT` ledger commands at original
  cost, then **restores** `lastPurchasePrice`/`Date` to the previous valid (non-reversed)
  purchase via `findLastValidPurchases`. Sets POSTED.
- **cancel** → CANCELLED (not from POSTED).

### Physical Count (`PhysicalCountService`)
Status: **DRAFT → IN_PROGRESS → RECONCILED**, or **→ CANCELLED** (DRAFT/IN_PROGRESS only).

- **create** (DRAFT) → one active count per warehouse+scheduledDate (`...StatusNot(CANCELLED)`).
  Lines created with expected=0, unitCostAtFreeze=0, action=PENDING.
- **addMaterials** (DRAFT only) → adds new material lines, skips duplicates.
- **start** (DRAFT→IN_PROGRESS) → **freezes**: snapshots `expectedQuantity` and
  `unitCostAtFreeze` from current `StockBalance`; sets `frozenAt`/`startedAt`.
- **updateCountedQuantities** (IN_PROGRESS) → records counted qty + interim variance; repeatable.
- **reconcile** (IN_PROGRESS→RECONCILED) → recomputes variance against
  `adjustedExpectedQuantity` (= frozen expected + IN − OUT after `frozenAt`, via
  `findAfterFreezeForMaterials`); validates WASTE only on negative variance; emits
  `COUNT_ADJUSTMENT` or `WASTE` ledger commands (IN if positive, OUT if negative) at
  `unitCostAtFreeze`; updates `lastCountDate`/`lastCountQuantity`; flags
  `hasLargeVariance` if total variance value > **500** (`LARGE_VARIANCE_THRESHOLD`).
  **Irreversible.**
- **cancel** → CANCELLED (not from RECONCILED).

### Opening Balance (`OpeningBalanceService`)
- Single `OPENING_BALANCE`/`IN` ledger command per warehouse+material, guarded by the
  `OPENING_{tenant}_{wh}_{material}` idempotency key. Re-entry returns the existing tx
  (`idempotentHit=true`); corrections must go through Adjustment. `createBulk` loops `create`.

---

## Calculation Rules

All money/quantity math: **`BigDecimal`, scale = 6, `RoundingMode.HALF_UP`** (the
shared constants `SCALE=6`, `ROUNDING=HALF_UP` appear in `InventoryLedgerService`,
`StockBalanceService`, `UomConversionService`, `UomService`, `PurchaseInvoiceService`).

### UOM conversion (`UomConversionService`)
- Identity (same id) → return value (scaled).
- Same base UOM → physical convert:
  `result = (quantity × from.factorToBase) ÷ to.factorToBase`.
- Different base / cross-type → **not supported**, throws `UomConversionException`.
- `convertToStockUom(qty, fromUom, material, tenantId)` = convert to `material.stockUom`
  (the ledger entry point). Base of a UOM = `u.baseUom?.id ?? u.id`.

#### Unit-cost normalization — single conversion point in the ledger
- **Contract:** `LedgerCommand.enteredUnitCost` is **always per the ENTERED UOM** — the same
  unit as `enteredQuantity`/`enteredUomId`. The user enters "10 kg at 10 each" → quantity 10
  (kg), cost 10 (per kg). Callers never pre-convert cost.
- `InventoryLedgerService.record` normalizes it **once**, next to the quantity conversion,
  using the invariant that **total cost is unit-invariant**:
  `totalCost = enteredUnitCost × enteredQuantity` (same amount in any UOM), and
  `tx.unitCost` (stored per **stock UOM**) `= totalCost ÷ stockQuantity`. Computed only when
  `enteredUnitCost != null` and `stockQuantity ≠ 0`; otherwise `tx.unitCost` is null. This needs
  no separate factor and is correct even when `stockUom ≠ base`.
- Downstream, `StockBalanceService.applyTransaction` / `StockBatchService` convert the stored
  per-stock-UOM cost to the balance's display UOM via `convert(1, displayUom, stockUom)`.
- **Do not re-introduce caller-side cost pre-conversion** (the old `÷ factorToBase` in
  `PurchaseInvoiceService.post` / `StockBalanceService.triggerOpeningBalance`) — it would
  double-convert. (Removed; it was the cause of a negative average cost on purchase return.)
- Separate & unrelated: `PurchaseInvoiceService.post` still converts the line cost to the
  **display** UOM for the `StockBalance.lastPurchasePrice` *display field* — that is not the
  ledger cost and is correctly retained.

### Stock balance weighted-average cost (`StockBalanceService.applyTransaction`)
- On **IN** with non-null unitCost and newQty > 0:
  `newAvg = (oldQty × oldAvg + deltaQty × unitCost) ÷ newQty`. Null unitCost → carry
  old average forward. `quantity += delta`.
- On **OUT**: `quantity -= delta`; average cost unchanged.
- `applyTransaction` runs with `@Transactional(propagation = MANDATORY)` — must be
  inside the ledger's transaction.

### Stock balance derived response fields (`StockBalanceMapper`)
- `totalValue = quantity × averageCost`.
- `isBelowMinimum = quantity < minimumQuantity`; `isBelowReorderPoint = quantity < reorderPoint`.

### Purchase invoice totals (`PurchaseInvoiceService`, since V26)
Per line (`calculateLine`):
- `lineSubtotal = qty × unitCost` (gross, stored in `lineTotal`).
- Discount: if `discountPercent > 0` → `discountAmount = lineSubtotal × pct ÷ 100`;
  else if `discountAmount > 0` → derive `discountPercent` (when subtotal > 0). **Percent wins** if both supplied.
- `lineNetTotal = lineSubtotal − discountAmount`.

Invoice (`calculateInvoiceTotals`):
- `subtotal = Σ lineNetTotal`.
- Invoice discount: pct-priority on `subtotal` → `afterDiscount = subtotal − discountAmount`.
- Tax: pct-priority on `afterDiscount` → `taxAmount`.
- `totalAmount = afterDiscount + taxAmount`.
- Worked example (10 × 100, line disc 5%, invoice disc 10%, tax 14%):
  line 1000 → −50 → net 950 → −95 → 855 → +119.7 → **974.7**.

### Purchase return line total
`lineTotal = quantity × originalLine.unitCost` (no discount/tax); `subtotal = totalAmount = Σ lineTotal`.

---

## Controller Patterns

### Auth headers
- **`X-Tenant-Id`** (`@RequestHeader("X-Tenant-Id") Long tenantId`) — required on
  essentially every tenant-scoped endpoint; threaded into every service call.
  The constant lives in `tenant/TenantHeaders.X_TENANT_ID` (used by `CorsConfig`).
- **`X-User-Id`** (`@RequestHeader(value="X-User-Id", required=false) Long userId`) —
  optional; passed as the acting user for audit (`createdBy`, `postedBy`, etc.) on
  operation endpoints (create/update/complete/post/cancel/reconcile/start).
- Auth itself is JWT (`JwtAuthenticationFilter`, stateless); the tenant/user **ids**
  come from these headers, not the token. *unclear — verify how header trust relates
  to the JWT principal in `auth/security`.*

### Permissions (`@PreAuthorize`)
Method-level SpEL against a `securityService` bean (method security enabled via
`@EnableMethodSecurity` in `SecurityConfig`). Standard pattern:

```java
@PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PERMISSION_CODE')")
```

Permission codes seen (seeded in migrations):
- `INVENTORY_SETUP_VIEW` / `INVENTORY_SETUP_MANAGE` — material, category, catalog, warehouse, supplier.
- `INVENTORY_STOCK_VIEW` / `INVENTORY_STOCK_MANAGE` — stock balance, physical count.
- `INVENTORY_PURCHASE_VIEW` / `INVENTORY_PURCHASE_MANAGE` — purchase invoice, purchase return.
- SysAdmin-only class-level: `PanelUomController` (`@PreAuthorize("@securityService.isSysAdmin()")`).
- Note: `UomController` (`/api/uom`) and `OpeningBalanceController` have **no**
  `@PreAuthorize` (rely on the global "authenticated" rule only). *Possibly intentional;
  verify whether opening-balance should be permission-gated.*

> `@securityService` bean is **not in the inventory module** — defined elsewhere
> (likely `auth`/`rbac`). Its `isSysAdmin()` / `hasPermission(String)` methods are
> the contract used everywhere. *unclear — verify exact class.*

### Swagger annotations
- Class: `@Tag(name = "...", description = "...")`. Tag naming convention:
  `"Inventory Setup - X"`, `"Inventory - X"`, `"SysAdmin - X"`.
- Method: `@Operation(summary = "...", description = "...")` with multi-line `description`.
- Global `OpenApiConfig` registers a `Bearer Auth` (HTTP bearer/JWT) security scheme.

### URL naming conventions
- Base prefix `/api/inventory/<plural-kebab>`:
  `warehouses`, `suppliers`, `materials`, `material-categories`, `global-materials`,
  `global-material-categories`, `purchase-invoices`, `purchase-returns`,
  `physical-counts`, `stock-balance`, `opening-balance`.
- Exceptions: tenant UOM at **`/api/uom`**; SysAdmin global UOM at **`/sys-admin/uom`**.
- CRUD verbs: `GET /` (list, query-param filters), `GET /{id}`, `POST /` (201 Created
  via `ResponseEntity.status(CREATED)`), `PUT /{id}` (full update), `PATCH /{id}/activate`
  & `/{id}/deactivate` (soft enable/disable on master data), `DELETE /{id}` (UOM only).
- Document state transitions are **POST sub-resources**: `/{id}/complete`, `/{id}/post`,
  `/{id}/cancel`, `/{id}/start`, `/{id}/reconcile`, `/{id}/counted-quantities` (PUT),
  `/{id}/add-materials`. Cancel takes optional `CancelDocumentRequest` body (`reason`).
- Filtering is via `@RequestParam(required=false)` (e.g. `search`, `active`, `categoryId`,
  `branchId`, `type`, `warehouseId`, `belowMinimum`). Service converts blank search to null.

---

## Migration Conventions

- **Current latest: `V26__purchase_invoice_discount_tax_fields.sql`.** Next is `V27`.
- Naming: `V<n>__snake_case_description.sql` (double underscore after version).
- **Numbering has gaps** (no V17, V20; see git history of renumbering) — Flyway tolerates
  gaps. Always use the next integer above the current max; never reuse/insert between.
- Patterns used consistently:
  - `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS` (idempotent-friendly).
  - Money/qty `NUMERIC(18,6)`, percent `NUMERIC(10,4)`, codes `VARCHAR(100)`, status/type `VARCHAR(30..50)`.
  - `tenant_id BIGINT NOT NULL REFERENCES tenants(id)` on tenant tables.
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP`.
  - **CHECK constraints** mirror enums (e.g. `chk_purchase_invoice_status CHECK (status IN (...))`)
    and non-negativity (`chk_..._amount CHECK (x >= 0)`). When an enum changes, drop &
    re-add the check constraint (see `V24` aligning status to DRAFT/COMPLETE/POSTED/CANCELLED).
  - Unique keys named `uk_...`, indexes `idx_...`, checks `chk_...`.
  - Lines use `ON DELETE CASCADE` to parent.
  - RBAC seeding: `INSERT INTO permissions ... ON CONFLICT (code) DO UPDATE`, then
    `role_permissions` and `user_permissions` with `ON CONFLICT ... DO NOTHING`.
  - Bilingual columns added incrementally as `name_ar VARCHAR(255)` (see `V5`, V16-era).
  - **JSON column exception — `waste_document.stock_warnings` (`jsonb`, V9):** this is the
    project's **first and (for now) only** JSON/jsonb column. It was chosen specifically because
    `stock_warnings` is point-in-time descriptive data (advisory shortfall warnings computed once
    at COMPLETE time) that: (a) has no independent query need, (b) is never filtered or joined on,
    and (c) must travel with the document in the same row to guarantee zero extra DB round-trips on
    every GET. All other structured child data (lines, balance rows, etc.) must still use proper
    tables per the established convention. **Do not treat this as a general precedent** — if you
    find yourself reaching for a JSON column for new child data that might need independent
    queries, indexing, or reporting, use a table instead.

---

## Frontend Integration Notes

### Existing API contracts (inventory)

> Conventions: all return JSON; list endpoints return arrays; create returns `201`.
> Errors use `ApiErrorResponse { success:false, message, timestamp }`. All require
> `X-Tenant-Id` (except where noted); operation writes also accept optional `X-User-Id`.

**Setup / master data**
- `GET/POST /api/inventory/warehouses`, `GET/PUT /api/inventory/warehouses/{id}`,
  `PATCH /api/inventory/warehouses/{id}/activate|deactivate`
  (`WarehouseRequest` → `WarehouseResponse`; filters: search, branchId, type, active).
- `GET/POST /api/inventory/suppliers`, `GET/PUT /api/inventory/suppliers/{id}`,
  `PATCH .../activate|deactivate` (`SupplierRequest` → `SupplierResponse`; filters: search, active).
- `GET/POST /api/inventory/materials`, `GET/PUT /api/inventory/materials/{id}`,
  `POST /api/inventory/materials/import` (`ImportMaterialsRequest` → `ImportMaterialsResponse`),
  `PATCH .../activate|deactivate` (`MaterialRequest` → `MaterialResponse`; filters: search, categoryId, defaultUomId, active).
- `GET/POST /api/inventory/material-categories`, `PUT`, `PATCH .../activate|deactivate`
  (`MaterialCategoryRequest` → `MaterialCategoryResponse`; global categories read-only, `global=true`).
- `GET /api/inventory/global-materials` (catalog browse, `MaterialCatalogResponse`, `alreadyImported` flag).
- `GET /api/inventory/global-material-categories` (`MaterialCategoryResponse`, query `active`).

**UOM**
- `GET /api/uom` (available = global + tenant, global first), `POST /api/uom`
  (`UomRequest`→`UomResponse`), `PATCH /api/uom/{id}/deactivate`, `DELETE /api/uom/{id}`
  (only if unused).
- `GET/POST /sys-admin/uom` (global, SysAdmin), `PATCH /sys-admin/uom/{id}/deactivate`.
- `UomResponse` exposes `isGlobal` (custom Jackson getter) + `tenantId`.

**Stock**
- `GET /api/inventory/stock-balance/warehouse/{warehouseId}` (filters: search, categoryId, belowMinimum),
  `GET .../warehouse/{warehouseId}/material/{materialId}`,
  `GET .../material/{materialId}` (all warehouses). Returns `StockBalanceResponse`
  (with derived `totalValue`, `isBelowMinimum`, `isBelowReorderPoint`).

**Operations**
- `POST /api/inventory/opening-balance`, `POST .../bulk` (`OpeningBalanceRequest` /
  `OpeningBalanceBulkRequest` → `OpeningBalanceResponse`, `idempotentHit` flag).
- Purchase invoices: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`,
  `POST /{id}/complete|post|cancel` (`PurchaseInvoiceRequest` → `PurchaseInvoiceResponse`;
  list omits lines, detail includes them).
- Purchase returns: `GET /`, `GET /{id}`, `POST /`, `POST /{id}/complete|post|cancel`
  (`PurchaseReturnRequest` → `PurchaseReturnResponse`).
- Physical counts: `GET /`(opt `warehouseId`), `GET /{id}`, `POST /`,
  `POST /{id}/add-materials|start|reconcile|cancel`, `PUT /{id}/counted-quantities`
  (returns `PhysicalCountSummaryResponse` for list, `PhysicalCountResponse` for detail).

### FE patterns observed
- **List vs detail split:** list/summary responses omit line items (`toSummary` builds
  with `lines(null)`); fetch `GET /{id}` for full detail. Physical counts even have a
  dedicated `PhysicalCountSummaryResponse` (with `lineCount`/`varianceCount`).
- **Denormalized names in responses** for table display (e.g. `materialName`,
  `warehouseName`, `categoryName`, `*UomName/Code/Symbol`) so the FE needn't join.
- **`defaultUomId` aliasing:** `MaterialRequest` accepts both `stockUomId` and
  `defaultUomId` (same value) for UI compatibility; responses echo both groups.
- Soft activate/deactivate instead of delete on master data; document cancel instead
  of delete on transactions.
- Percent-or-amount duality on purchase discounts/tax: FE may send either; percent wins.

### RTL / Arabic UI conventions
- Bilingual fields everywhere: `name` (English/primary) + `nameAr` (Arabic). Entities,
  requests, and responses all carry `nameAr`; DB columns are `name_ar VARCHAR(255)`.
- `common/BilingualFieldUtils` provides `firstNonBlank`, `englishOrLegacy`, `trimToNull`
  for choosing/normalizing a display value across English/Arabic/legacy fields.
- CORS (`CorsConfig`) allows FE origins `http://localhost:5180` and `:5188`
  (allowed headers include `Authorization`, `Content-Type`, `Accept`, `X-Tenant-Id`).
- No explicit `dir="rtl"`/layout logic in backend (it's a FE concern) — backend only
  guarantees both language fields are persisted and returned.

---

## Exception Handling Convention (all modules)

> **Exception handling convention (all modules):** Every thrown exception must extend one of the
> six `com.smart.restaurant_saas.common` base exceptions (`BusinessException`,
> `ResourceNotFoundException`, `ValidationException`, `AuthenticationException`,
> `AuthorizationException`, `ExternalServiceException`) — all of which extend the abstract
> `AppException`. Each module defines its own `{ModuleName}ErrorCode` enum implementing
> `ErrorCode`, scoped to that module only — never reuse another module's error code enum, and
> never throw a raw `RuntimeException` or the base `AppException` directly from feature code.
> Error messages passed to exceptions are English debug text for server logs only; all
> user-facing text is derived by the frontend from `errorCode` + `params`, which must carry every
> dynamic value (entity names, ids, quantities, statuses) needed to reconstruct a full message
> with no backend string parsing on the frontend side.

**Migration status (as of this pass — inventory only):**
- The new hierarchy lives in `common/`: `ErrorCode`, `AppException` (abstract base),
  the six subclasses, `CommonErrorCode` (cross-module generic codes), and `ErrorParams`
  (null-tolerant param-map builder). `ApiErrorResponse` is the single structured response body
  (`errorCode`, `message`, `params`, `status`, `timestamp`, `path`, `fieldErrors`) and
  `GlobalExceptionHandler` maps one handler per hierarchy branch plus a non-leaking catch-all.
- **Inventory** module codes: `InventoryErrorCode`. Migrated throw sites:
  `InventoryLedgerService`/`InventoryLedgerException`, `UomConversionService`/`UomConversionException`,
  `PhysicalCountService`, `StockBalanceService`, `PurchaseInvoiceService`, `PurchaseReturnService`,
  `StockBatchService`.
- **Coexistence pattern:** the legacy `common.ApiException` (concrete, `HttpStatus` + message) is
  **retained untouched** for modules not yet migrated (auth/hr/rbac/tenant/user/branch/job and a
  few inventory setup services). It has its own `GlobalExceptionHandler` branch preserving prior
  behavior. New abstract base is named `AppException` (not `ApiException`) precisely so the two
  coexist. The `auth` module has a minimal `AuthErrorCode` (only `INVALID_CREDENTIALS`) added so
  login throws a proper 401 `AuthenticationException`.
- `BusinessException(String)` is **deprecated** (maps to `CommonErrorCode.BUSINESS_RULE_VIOLATION`,
  409) and exists only so not-yet-migrated inventory files keep compiling. New code must pass an
  explicit `ErrorCode` + `params`.
- **Follow-up (not done in this pass):** migrate the remaining inventory files still on legacy
  `ApiException` / deprecated `BusinessException(String)` — `WasteService`, `UomService`,
  `MaterialService`, `MaterialCategoryService`, `SupplierService`, `WarehouseService`,
  `InvoiceSequenceService` — then extend the pattern to the other modules and finally delete the
  legacy `ApiException` and the deprecated constructor.

---

## What NOT to do

- **Don't write `inventory_transaction` or mutate `stock_balance.quantity`/`average_cost`
  directly.** Always go through `InventoryLedgerService.record(LedgerCommand)`. The
  ledger owns idempotency, UOM conversion, costing, and balance updates atomically.
- **Don't call `StockBalanceService.applyTransaction` outside a ledger transaction** —
  it's `Propagation.MANDATORY` and will throw if no transaction is active.
- **Don't edit a non-DRAFT document.** Purchase invoice `update` rejects anything not
  DRAFT; posting/reconciling are irreversible (use Purchase Return / Adjustment).
- **Don't change immutable `code` fields.** Material/Warehouse/Supplier/Category services
  throw `BusinessException("Code cannot be changed")` on update.
- **Don't attempt cross-type UOM conversion** (e.g. WEIGHT→VOLUME). It throws
  `UomConversionException`. Conversions rely solely on `factorToBase` within one `UomType`.
- **Don't add a separate UOM conversion table.** There is intentionally none — the prior
  `MaterialUomConversion`-style design was removed; `factorToBase` + `baseUom` self-ref
  is the whole mechanism (`UomService` javadoc: "no separate conversion table").
- **Don't rely on Hibernate to create/alter schema** (`ddl-auto: validate`). Every column
  must exist via a Flyway migration first, or startup fails validation.
- **Don't reuse/insert migration version numbers.** Append the next integer; gaps are fine.
- **Don't assume `InventoryTransfer` / `OrderConsumptionEvent` / `DocumentHistory` are
  wired up.** Entities/enums exist but no operation service/controller was found for
  transfers or consumption posting, and `DocumentHistoryRepository`/`Service` were
  deleted. Treat these as **scaffolding / not-yet-implemented** — *verify before building on them.*
- **Deprecated/removed patterns to avoid resurrecting:**
  - `is_consumption_warehouse` flag on `warehouse` — added in `V21`, **removed in `V22`**.
    Don't reintroduce a "consumption warehouse" boolean.
  - Legacy purchase-invoice status values: `V13` first defined `DRAFT/POSTED/CANCELLED`,
    then (later in the same migration) switched the check to `DRAFT/COMPLETED/CANCELLED`
    and migrated `POSTED`→`COMPLETED`. `V24` settled the canonical set
    `DRAFT/COMPLETE/POSTED/CANCELLED` (migrating `COMPLETED`→`COMPLETE`). Use the
    `DocumentStatus` enum spelling (`COMPLETE`, not `COMPLETED`). `V16` also references
    `'COMPLETED'` for the transfer flow — *verify TransferStatus spelling if implementing.*
  - Several `Create*Request`/`Update*Request` DTOs and `Admin*`/`InventoryStock*` controllers
    referenced in older code were replaced by single combined request DTOs (e.g.
    `MaterialRequest`, `WarehouseRequest`, `PurchaseInvoiceRequest`) — don't recreate the
    split create/update DTO pattern; use one request DTO per resource.
- **Don't put business math at any scale/rounding other than `scale=6, HALF_UP`** — it's
  the project-wide convention and mismatches will diverge from existing stored values.
```
