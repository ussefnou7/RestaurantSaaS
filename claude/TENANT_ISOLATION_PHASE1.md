# Tenant Isolation Audit — Phase 1

## Method stamp

| Item | Value |
|---|---|
| Checkout | Shared primary worktree (not an isolated audit worktree) |
| Repository | `/home/ussef/Restaurant SAAS/restaurant-saas` |
| Branch | `fix/cleanup-batch-2-tenant-binding` |
| Commit audited | `3ddda12a18010b6778ba1b20f251c32b622529a7` |
| Schema source | Flyway `V1`–`V51`; `V51` does not change table ownership |
| Database inspected | PostgreSQL 16.15, `restaurant-saas`, schema `public`, Flyway version 51; read-only catalog queries via `psql` |
| Test database | `restaurant_saas_test`, Flyway version 51 |
| Suite | `./mvnw test`: **708 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**, 2026-08-31 |

The audit combined repository/controller/service call-site tracing, searches for request-derived
tenant inputs and inherited Spring Data calls, inspection of all seven native queries and four
`JdbcTemplate` consumers, Flyway review, and live PostgreSQL catalog queries. Tests were used to
identify what is demonstrated, never as a substitute for reading the control.

## Result and findings

> **Status 2026-08-31:** Finding 1 is **fixed** (`8519821`, `b3b3fdd`) and the caller-scoping
> follow-up is **done** (`d7ca4ab`); suite **709/709**. See the ranked remediation list below. The
> finding is recorded as entry 11 in `claude/AUDIT_FINDINGS_CODE.md`. The audit text below is left
> as written at the time of the audit — it describes the code as it stood at `3ddda12`.

Phase 1 found one concrete breach. No cross-tenant report query, direct-ID endpoint, customer
lookup, document-number lookup, or idempotency-key lookup was found. The central JWT binding holds
for tenant principals. The one breach is a referenced ID inside a waste-line request.

| # | Location (`path:line`) | Surface | What leaks | Exploit sketch | Severity |
|---|---|---|---|---|---|
| 1 | `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:418`; `src/main/java/com/smart/restaurant_saas/inventory/core/InventoryLedgerService.java:79`; `src/main/resources/db/migration/V11__waste.sql:86` | Waste line `uomId` on add/update and later ledger posting | Tenant B's private UOM ID and symbol become readable in tenant A's waste response; A can persist a foreign-tenant UOM reference in `waste_line` and, after posting, in `inventory_transaction.entered_uom_id` | Authenticated as tenant A with `INVENTORY_STOCK_MANAGE`, create an A draft waste document whose material uses a global base UOM, then `POST /api/inventory/waste-documents/{A-doc-id}/lines` with `{"materialId":A_material,"quantity":1,"uomId":B_private_uom}` where B's UOM has the same global base. The request succeeds and saves B's UOM on A's line; `PUT .../lines/{lineId}` has the same path. Posting the document carries the reference into the ledger. | **CRITICAL** |

This is confirmed statically, not inferred from a generic 4xx test: `loadOwned` and `loadMaterial`
scope the document and material (`WasteService.java:442-462`), but `resolveUom` performs inherited
`findById` and checks only convertibility (`:418-430`). `UomConversionService` accepts matching base
IDs and never uses its `tenantId` argument (`UomConversionService.java:46-73,87-108`). The database
FK is only `uom_id -> uom(id)`. The response includes `uomId` and `uomSymbol`
(`WasteLineResponse.java:16-17`). No current test attempts a cross-tenant convertible waste UOM,
and no test was found that asserts the missing check as intended behaviour.

## §0 — Central binding verification

| Question | Verification method | Conclusion |
|---|---|---|
| Any tenant-principal path still reads tenant from the request? | Searched production Java for `X-Tenant-Id`, `TenantHeaders`, `getHeader`, `getHeaders`, `getParameter`, `@RequestHeader`, filters, interceptors and argument resolvers. Manually traced every hit. | **No.** The only raw read is the intended SYS_ADMIN branch at `CurrentTenantProvider.java:100-115`. `CorsConfig` only allows the header, and `UomLookupVersionHeaderFilter` calls the provider instead of reading it. |
| Can a controller receive an independently supplied tenant argument? | Searched every controller parameter and every tenant-ID path variable. All tenant-route parameters are `@CurrentTenantId Long`; the resolver registration and implementation were traced. | **No for tenant routes.** `CurrentTenantIdArgumentResolver.java:23-35` supports only the annotation plus `Long` and calls the provider. The `NativeWebRequest` argument is unused. The only explicit tenant path parameters are the intended SYS_ADMIN tenant/tenant-user APIs. |
| Does resolution fail closed? | Traced every branch in `CurrentTenantProvider.getCurrentTenantId`, then ran the provider tests in the full suite. | **Yes.** Missing/invalid authentication fails 401 (`:85-93`); a tenant principal with null/zero tenant fails 403 (`:51-56`); missing/inactive tenant fails 400/403 (`:117-131`); SYS_ADMIN without a header fails 400 (`:40-48`). No default, null, or zero reaches a controller. `getCurrentTenantIdOrNull` (`:65-70`) is explicitly limited to infrastructure decoration and skips the version header. |
| Is binding independent of `@PreAuthorize`? | Traced MVC argument resolution registration at `WebMvcConfig.java:16-19` and compared it with method-security evaluation. | **Yes.** Resolution occurs because the handler parameter is annotated, not because an authorization expression happens to call the provider. Services that resolve the tenant themselves also call the same provider. |
| Does `X-Tenant-Id` affect a normal tenant principal? | Read the branch condition and ran `CurrentTenantProviderTest` plus `CrossTenantIsolationIntegrationTest`. | **No.** Only a signed principal whose role code is `SYS_ADMIN` enters the header branch (`CurrentTenantProvider.java:38-56,96-101`). Tests cover missing, malformed, unknown and forged header values and the three former critical write paths. The two post controls also prove the target documents are postable by their owner, so the rejection is not an unrelated precondition failure. |

### Where SYS_ADMIN header selection applies

A signed SYS_ADMIN principal is allowed by `isSysAdmin()` explicitly or by
`SecurityService.hasPermission`, which returns true for SYS_ADMIN (`SecurityService.java:19-46`).
On the following **non-admin tenant routes**, the selected header tenant therefore becomes the
effective tenant. This is an enumeration of current reachability, not a recommendation to retain
or redesign it:

- `/api/branches`, `/api/jobs`, `/api/users`, `/api/permissions` and the user-permission aliases,
  `/api/hr/employees`, `/api/hr/leave-types`.
- `/api/assets` including lines, maintenance, disposals and reports; `/api/devices`.
- `/api/inventory/material-categories`, `/api/inventory/materials`,
  `/api/inventory/global-materials` (the tenant-dependent `alreadyImported` projection),
  `/api/inventory/warehouses`, `/api/inventory/stock-balances`, `/api/uom`,
  `/api/inventory/suppliers`, `/api/inventory/purchase-invoices`,
  `/api/inventory/purchase-returns`, `/api/inventory/waste-documents`,
  `/api/inventory/physical-counts`, `/api/inventory/order-consumption-docs`, and all six
  `/api/inventory/reports/*` routes.
- `/api/menu`, `/api/menu/categories`, `/api/menu/products`, product add-ons and recipes.
- `/api/loyalty/customers`, `/api/orders`, `/api/order-requests`, all order reports,
  `/api/shifts`, `/api/tables`, and `/api/table-sections`.

The owner/branch-manager-only HR surfaces—leave balances, leave requests, salaries and salary
adjustments—reject SYS_ADMIN at method security and do not reach tenant selection. `/api/auth/me`
reads the signed principal directly and does not select a tenant. The true admin/global endpoints
are enumerated in §E.

## §A — Repository layer

Ownership was reused from `claude/TENANT_ISOLATION_AUDIT.md` and reconciled against Flyway 51.
`V51` adds one physical-count value column and changes no ownership classification. The tables
remain 58 base tables: tenant-owned, mixed (`uom`, `material_category`), five parent-inherited
children, global reference, and system tables as recorded there.

“Scoped by parent/provenance” means the query itself has no local tenant predicate, but every
production caller supplies IDs obtained from a tenant-scoped parent in the same service flow.
Those controls would fail if a new caller passed an arbitrary ID. No generic repository test can
prove that future callers preserve provenance; the verdict is based on a complete current
call-site search.

### Declared repository methods

| Method | Kind | Tenant-filtered? | Evidence (`path:line`) | Verdict |
|---|---|---:|---|---|
| `AssetRepository`: `findByTenantIdOrderByIdDesc`, `findByIdAndTenantId` | Derived | Yes | `assets/asset/AssetRepository.java:11-13` | CLEAN |
| `AssetLineRepository`: `findByTenantIdAndAssetIdOrderByIdAsc`, `findByTenantId`, `findByIdAndTenantId`, `countByTenantIdAndAssetId` | Derived | Yes | `assets/assetline/AssetLineRepository.java:11-17` | CLEAN |
| `AssetDisposalRepository`: `findByTenantIdAndAssetLineIdOrderByIdDesc`, `findByTenantIdOrderByDisposalDateDescIdDesc`, `countByTenantIdAndAssetLineId`, `findListItems` | Derived/JPQL | Yes; JPQL scopes disposal, asset and line | `assets/disposal/AssetDisposalRepository.java:17-55` | CLEAN |
| `AssetMaintenanceRepository`: `findByTenantIdAndAssetLineIdOrderByIdDesc`, `countByTenantIdAndAssetLineId`, `findListItems` | Derived/JPQL | Yes; JPQL scopes maintenance, asset and line | `assets/maintenance/AssetMaintenanceRepository.java:17-53` | CLEAN |
| `BranchRepository`: `findByTenantId`, `findByTenantIdOrderByIdDesc`, `findByIdAndTenantId`, both tenant/code existence methods, `countByTenantIdAndActiveTrue` | Derived | Yes | `branch/BranchRepository.java:9-19` | CLEAN |
| `TenantSequenceCounterRepository.findForUpdate` | JPQL + lock | Yes, `(tenantId, year, sequenceKey)` | `common/sequence/TenantSequenceCounterRepository.java:12-23` | CLEAN |
| `DeviceRepository.findByTenantIdOrderByIdDesc`, `findByIdAndTenantId` | Derived | Yes | `device/repository/DeviceRepository.java:14-17` | CLEAN |
| `DeviceRepository.findBySecretKeyHash` | Derived | No | `device/repository/DeviceRepository.java:20` | INTENTIONAL GLOBAL AUTH RESOLVER; possession of the device secret is the credential |
| `EmployeeRepository`: tenant list, tenant/branch list, ID+tenant (active and unrestricted), tenant/code existence (create/update), tenant/job active existence, tenant/user active existence (create/update) | Derived | Yes | `hr/repository/EmployeeRepository.java:10-26` | CLEAN; all nine methods enumerate tenant in their name/signature |
| `LeaveBalanceRepository`: tenant/employee/year list, tenant/employee/type/year find, ID+tenant find, and both locked equivalents | Derived/locked derived | Yes | `hr/repository/LeaveBalanceRepository.java:12-29` | CLEAN |
| `LeaveRequestRepository`: tenant list, tenant/branch list, tenant/employee list, ID+tenant | Derived | Yes | `hr/repository/LeaveRequestRepository.java:10-16` | CLEAN |
| `LeaveTypeRepository`: tenant list, active tenant list, ID+tenant (active and unrestricted), tenant/code existence (create/update) | Derived | Yes | `hr/repository/LeaveTypeRepository.java:10-20` | CLEAN |
| `SalaryAdjustmentRepository.findByTenantIdAndEmployeeIdOrderByAdjustmentDateDescIdDesc`, `findByIdAndTenantId` | Derived | Yes | `hr/repository/SalaryAdjustmentRepository.java:10-12` | CLEAN |
| `SalaryRepository.findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc`, `findByTenantIdAndEmployeeIdAndActiveTrue` | Derived | Yes | `hr/repository/SalaryRepository.java:10-12` | CLEAN |
| `OrderConsumptionRepository`: `findByFilters`, `findByIdAndTenantId`, `findByTenantIdAndWarehouseIdAndStatus`, `findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc`, `findByIdAndTenantIdForUpdate` | JPQL/derived/lock | Yes | `inventory/orderconsumption/OrderConsumptionRepository.java:25-75` | CLEAN |
| `OrderConsumptionRepository.findByIdForUpdate`, `findBatchingCandidates` | JPQL/lock | No local filter | `inventory/orderconsumption/OrderConsumptionRepository.java:78-114` | SYSTEM SCHEDULER: candidate contains tenant; ID is claimed from that candidate |
| `OrderConsumptionLineRepository.findExistingOrderLineIds`, `sumRecipeQuantitiesByDocId`, `countLinesByDocIds`, `findLinesByDocId` | JPQL | By parent/ID provenance | `inventory/orderconsumption/OrderConsumptionLineRepository.java:12-26,51-67` | CLEAN BY PARENT; not safe for arbitrary IDs |
| `OrderConsumptionLineRepository.sumPendingRecipeQuantitiesByWarehouse`, `summarizeMaterialsByDocId` | JPQL | Yes | `inventory/orderconsumption/OrderConsumptionLineRepository.java:35-49,69-87` | CLEAN |
| `OrderConsumptionMaterialRepository.findByDocId` | JPQL | By parent provenance | `inventory/orderconsumption/OrderConsumptionMaterialRepository.java:17-26` | CLEAN BY PARENT |
| `OrderConsumptionMaterialRepository.sumUnconsumedRequiredQuantitiesByWarehouse` | JPQL | Yes, through mandatory doc | `inventory/orderconsumption/OrderConsumptionMaterialRepository.java:33-47` | CLEAN |
| `InventoryTransactionRepository`: `findByTenantIdAndIdempotencyKey`, `findOriginalsByReference`, `existsByReference`, `findByTenantIdAndFilters`, `findLastValidPurchases`, `findBackdatedConsumptionConflicts`, both `findPhysicalCountMovements` overloads, `aggregateShrinkage`, `aggregateWaste`, `aggregateLossComparison`, `summarizeMovementsAfterFreeze` | Derived/JPQL/default wrapper | Yes | `inventory/repository/InventoryTransactionRepository.java:24-425,465-493` | CLEAN |
| `InventoryTransactionRepository.findReversalOf` | JPQL | By original transaction provenance | `inventory/repository/InventoryTransactionRepository.java:26-30` | CLEAN BY CALLER; not safe for arbitrary IDs |
| `InventoryTransactionRepository.findPhysicalCountMovementReferences` | Native SQL | Input IDs come from tenant-scoped movement query; joined references also require `child.tenant_id = tx.tenant_id` | `inventory/repository/InventoryTransactionRepository.java:435-460` | CLEAN BY PROVENANCE |
| `MaterialCategoryRepository.findByIdAndTenantId`, `existsByTenantIdAndCode` | Derived | Yes but intentionally tenant-only | `inventory/repository/MaterialCategoryRepository.java:14-16` | CLEAN for mutations |
| `MaterialCategoryRepository.findByFilters` | JPQL | Yes, `tenantId IS NULL OR tenantId=:tenantId` | `inventory/repository/MaterialCategoryRepository.java:18-30` | CLEAN MIXED TABLE |
| `GlobalMaterialCategoryRepository.findByTenantIdIsNullAndActiveOrderBySortOrderAscNameAsc`, `findByTenantIdIsNullOrderBySortOrderAscNameAsc` | Derived | Global rows only (`tenant_id IS NULL`) | `inventory/repository/GlobalMaterialCategoryRepository.java:15-17` | CLEAN GLOBAL VIEW |
| `MaterialRepository`: `findByIdAndTenantId`, `existsByTenantIdAndCode`, `findByFilters`, `findAllWithUomsByIdIn`, `findAlreadyImportedCatalogIds`, `findImportedCatalogPairs` | Derived/JPQL | Yes; every query has tenant | `inventory/repository/MaterialRepository.java:14-84` | CLEAN |
| `PhysicalCountRepository`: `findByIdAndTenantId`, `findDetailByIdAndTenantId`, tenant list, tenant/warehouse list, `findFreezeConflicts` | Derived/JPQL | Yes | `inventory/repository/PhysicalCountRepository.java:16-65` | CLEAN |
| `PhysicalCountLineRepository.findByPhysicalCountId`, `findByPhysicalCountIdWithDetails` | Derived/JPQL | Through mandatory physical-count parent ID | `inventory/repository/PhysicalCountLineRepository.java:13-24` | CLEAN BY PARENT |
| `PurchaseInvoiceRepository.findByTenantIdOrderByInvoiceDateDesc`, `findByTenantIdAndStatusOrderByInvoiceDateDesc`, `findByIdAndTenantId` | Derived | Yes | `inventory/repository/PurchaseInvoiceRepository.java:13-20` | CLEAN |
| `PurchaseReturnRepository.findByIdAndTenantId`, `findByTenantIdOrderByReturnDateDesc`, `findReturnSummariesByOriginalInvoice`, `findPostedReturnLinesByInvoiceId` | Derived/JPQL | Yes; line access scopes through mandatory return parent | `inventory/repository/PurchaseReturnRepository.java:15-41` | CLEAN |
| `StockBalanceRepository`: `findByTenantWarehouseMaterial`, `findByWarehouse`, `findByTenantIdAndWarehouseIdAndMaterialId`, `findByIdAndTenantId`, `findForStockValuation`, `findForLowStock`, `findByWarehouseAndMaterials` | JPQL/derived | Yes | `inventory/repository/StockBalanceRepository.java:16-132` | CLEAN |
| `StockBatchRepository`: `findByStockBalanceIdOrderByMovementDateAscIdAsc`, status variant, source-invoice-line variant, `sumOpenBatchTotals` | Derived/JPQL | Through tenant-scoped stock-balance ID | `inventory/repository/StockBatchRepository.java:22-84` | CLEAN BY PARENT |
| `StockBatchRepository.findByTenantIdAndSourceTransactionId`, `findOpenedByPurchaseInvoice` | Derived/JPQL | Yes | `inventory/repository/StockBatchRepository.java:41-59` | CLEAN |
| `StockBatchRepository.aggregatePurchasePriceDrift` | Native SQL | Yes: `batch.tenant_id=:tenantId`; invoice and reversal joins also match batch tenant | `inventory/repository/StockBatchRepository.java:132-185` | CLEAN |
| `SupplierRepository.findByIdAndTenantId`, `existsByTenantIdAndCode`, `findByFilters` | Derived/JPQL | Yes | `inventory/repository/SupplierRepository.java:14-29` | CLEAN |
| `UomRepository.findByCodeForTenant`, `findAllVisibleToTenant`, `findAllByTypeForTenant`, `findAvailableForTenant`, `findLookupForTenant`, `findResolvableByIdForTenant` | JPQL | Yes, `tenant_id IS NULL OR tenant_id=:tenantId` | `inventory/repository/UomRepository.java:15-87` | CLEAN MIXED TABLE |
| `UomRepository.findByTenantIdIsNullOrderByNameAsc`, global code existence | Derived | Global only | `inventory/repository/UomRepository.java:90,103` | CLEAN GLOBAL VIEW |
| `UomRepository.countMaterialsUsingUom` | JPQL | No | `inventory/repository/UomRepository.java:92-100` | SAFE DELETE GUARD only after UOM ownership/visibility load; count may include other tenants but only affects whether deletion is refused |
| `UomRepository.existsByCodeAndTenantId` | Derived | Yes | `inventory/repository/UomRepository.java:106` | CLEAN |
| `WarehouseRepository`: `findByIdAndTenantId`, locked equivalent, `findByBranchIdAndTenantId`, `existsByTenantIdAndCode`, `findByFilters` | Derived/JPQL/lock | Yes | `inventory/repository/WarehouseRepository.java:17-48` | CLEAN |
| `WasteDocumentRepository`: tenant ID find, tenant list, tenant/warehouse list | Derived | Yes | `inventory/repository/WasteDocumentRepository.java:12-17` | CLEAN |
| `JobRepository`: tenant list, ID+tenant (active and unrestricted), tenant/code existence (create/update) | Derived | Yes | `job/repository/JobRepository.java:10-18` | CLEAN |
| `CustomerRepository`: tenant list, tenant filter page, ID+tenant, tenant+phone | Derived/JPQL | Yes | `loyalty/customer/CustomerRepository.java:15-32` | CLEAN |
| `MenuCategoryRepository.findByTenantIdOrderBySortOrderAscIdAsc`, `findByIdAndTenantId` | Derived | Yes | `menu/category/MenuCategoryRepository.java:11-13` | CLEAN |
| `ProductAddOnRepository`: tenant/product list, tenant-wide list, tenant/product/add-on existence and find | Derived | Yes | `menu/product/ProductAddOnRepository.java:11-21` | CLEAN |
| `ProductRepository`: tenant list, `findMenuCatalog`, `findParentEligible`, ID+tenant (normal/locked), category+tenant list, name/category/parent/sibling tenant existence methods, parent+tenant list | Derived/JPQL/lock | Yes | `menu/product/ProductRepository.java:17-80` | CLEAN |
| `ProductRepository.existsByParentProductId` | Derived | No | `menu/product/ProductRepository.java:65` | SAFE CURRENT CALLER: used only after the parent is tenant-locked; tenant-scoped alternative exists |
| `RecipeRepository.findByIdAndTenantId`, `findByProductIdAndTenantIdAndActiveTrue`, `findByProductIdAndTenantIdOrderByCreatedAtDescIdDesc` | Derived | Yes | `menu/recipe/RecipeRepository.java:11-15` | CLEAN |
| `RecipeItemRepository.findByRecipeId`, `findByRecipeIds` | JPQL | Yes, local tenant column | `menu/recipe/RecipeItemRepository.java:12-33` | CLEAN |
| `OrderRepository.findByIdAndTenantId`, `findByTenantIdAndIdempotencyKey`, `findByFilters` | Derived/JPQL | Yes | `order/core/OrderRepository.java:24-63` | CLEAN |
| `OrderRepository.existsByTableId`, `existsByTableSectionId` | JPQL | No | `order/core/OrderRepository.java:34-39` | SAFE CURRENT CALLERS: table/section is first loaded for tenant; existence is a delete guard |
| `OrderRepository.aggregateByShift`, `aggregateSalesOverTime`, `aggregateSalesByHour`, `aggregateSalesByPaymentMethod` | Native SQL | Yes | `order/core/OrderRepository.java:74-216` | CLEAN |
| `OrderLineRepository.findByOrderIdAndTenantIdOrderByIdAsc` | Derived | Yes | `order/core/OrderLineRepository.java:14` | CLEAN |
| `OrderLineRepository.aggregateSalesByProduct` | Native SQL | Yes through `orders o`, `o.tenant_id=:tenantId` | `order/core/OrderLineRepository.java:42-69` | CLEAN |
| `IncomingOrderRequestRepository.findByIdAndTenantId`, `findByFilters` | Derived/JPQL | Yes | `order/intake/IncomingOrderRequestRepository.java:15-31` | CLEAN |
| `ShiftRepository.findByIdAndTenantId`, `findByCashierUserIdAndTenantIdAndStatus` | Derived | Yes | `pos/shift/ShiftRepository.java:9-13` | CLEAN |
| `UserPermissionRepository`: `findPermissionIdsByTenantIdAndUserId`, `findActivePermissionsByTenantIdAndUserId`, `existsPermissionByTenantIdAndUserIdAndCode`, `deleteByTenantIdAndUserId` | JPQL | Yes | `rbac/repository/UserPermissionRepository.java:16-62` | CLEAN |
| `TableRepository.findByIdAndTenantId`, `findByFilters` | Derived/JPQL | Yes | `table/TableRepository.java:11-24` | CLEAN |
| `TableRepository.existsBySectionId`, `findAllBySectionId` | JPQL | No | `table/TableRepository.java:27-37` | SAFE CURRENT CALLERS: section is first tenant-scoped; delete/move helper only |
| `TableSectionRepository.findByIdAndTenantId`, `findByTenantIdAndBranchId`, `findActiveByTenantIdAndBranchId` | Derived/JPQL | Yes | `table/section/TableSectionRepository.java:11-37` | CLEAN |
| `UserRepository`: both ID+tenant finds, tenant+username find, both tenant lists, username create/update existence, email create/update existence, branch/status existence | Derived | Yes | `user/repository/UserRepository.java:11-31` | CLEAN |
| `UserRepository.findByUsername` | Derived | No | `user/repository/UserRepository.java:17` | UNUSED production declaration; authentication uses `findByTenantIdAndUsername` at `AuthService.java:73,94` |

Global/system repositories (`TenantRepository`, role/permission repositories,
`MaterialCatalogRepository`) are not tenant-owned. They were still traced: tenant administration
is SYS_ADMIN-gated; roles/permissions/catalog rows are shared reference data.

`document_history`, `inventory_transfer`, and `inventory_transfer_line` have entities/tables but no
production repository or endpoint in this checkout. `purchase_invoice_line`,
`purchase_return_line`, and `waste_line` are aggregate children without their own repositories;
their access paths are classified below.

### Inherited `JpaRepository` methods used directly

| Method | Kind | Tenant-filtered? | Evidence (`path:line`) | Verdict |
|---|---|---:|---|---|
| `UomRepository.findById` in waste add/update | Inherited | **No** | `inventory/core/WasteService.java:422` | **FINDING 1** |
| `UomRepository.findById` in ledger record | Inherited | **No** | `inventory/core/InventoryLedgerService.java:79` | **FINDING 1 amplification**; no independent visibility check |
| `UomRepository.findById` in material, recipe, invoice, return and UOM services | Inherited + post-load check | Yes in service | `MaterialService.java:110-117`; `RecipeService.java:182-189`; `PurchaseInvoiceService.java:651-658`; `PurchaseReturnService.java:647-654`; `UomService.java:276-282` | CLEAN; global-or-own check follows load |
| `MaterialRepository.findById`, `WarehouseRepository.findById` in ledger | Inherited + post-load check | Yes in service | `InventoryLedgerService.java:195-210` | CLEAN; explicit mismatch exception |
| `InventoryTransactionRepository.findById` in idempotency/reversal | Inherited | By tenant-scoped ID provenance | `InventoryLedgerService.java:65-72,110-132` | CLEAN current flows; `reverse` is not an endpoint and callers obtain originals by tenant/reference |
| `StockBalanceRepository.findById` in return rollback | Inherited | By impact ID from tenant-scoped post | `PurchaseReturnService.java:407` | CLEAN BY PROVENANCE |
| `MaterialRepository.findById` in `OpeningBalanceService` | Inherited | No local check | `inventory/material/OpeningBalanceService.java:95` | Not externally reachable with arbitrary request; only caller passes an already scoped material and null UOM. CLEAN CURRENT CALL GRAPH |
| `ProductRepository.findAllById` | Inherited + in-memory filter | Yes after load | `menu/product/ProductAddOnService.java:31-35` | CLEAN |
| `AssetLineRepository.findAllById`, `AssetRepository.findAllById` | Inherited | IDs originate in tenant-scoped disposal page | `assets/report/AssetReportService.java:58-67` | CLEAN BY PROVENANCE |
| `StockBalanceRepository.findAll` | Inherited | No; intentional system sweep | `inventory/backfill/StockBalanceAverageCostBackfill.java:50` | STARTUP BACKFILL, not request surface |
| `saveAll` on consumption, recipe, physical-count, purchase, RBAC collections | Inherited write | Entity collections were built from scoped parents/inputs | Call-site search recorded at `OrderConsumptionService.java:161,272,289`; `RecipeService.java:75`; `PhysicalCountService.java:564,596`; purchase services; RBAC services | CLEAN current flows |
| Tenant/global repositories' inherited `findById/findAll/existsById` | Inherited | System scope | Tenant admin, auth role resolution, catalog import | OUTSIDE TENANT-OWNED DATA |

No production use of inherited tenant-owned `deleteById`, `existsById`, `count`, or unqualified
tenant-owned `findAll` was found beyond the backfill above. Entity `save`/`delete` calls operate on
objects first loaded or constructed under the service checks listed in §B.

### Parent-inherited child tables

| Child table | Mandatory parent | Access-path result |
|---|---|---|
| `order_consumption_line` | `order_consumption` | Document-facing methods scope the parent; batching helpers consume candidate/scoped doc IDs. No endpoint accepts a raw child ID. |
| `order_consumption_material` | `order_consumption` | Same; warehouse aggregate explicitly filters `doc.tenantId`. |
| `purchase_invoice_line` | `purchase_invoice` | Lines are reached through `findByIdAndTenantId` invoice aggregates; line IDs are searched inside the owned invoice collection. |
| `purchase_return_line` | `purchase_return` | Lines are reached through the owned return; reporting query filters `purchaseReturn.tenantId`. |
| `waste_line` | `waste_document` | Line ID is searched inside the owned document. Parent isolation is clean, but its UOM FK is Finding 1. |

## §B — Endpoint IDOR pass

### Direct identifiers

Every controller path/filter identifier was mapped to its service loader. The following table
groups endpoints only where every identifier follows the same control.

| Surface | Identifier(s) | Ownership confirmation | Verdict |
|---|---|---|---|
| Branch, job, tenant-user, permission-user | `id`, `userId` | Tenant repository methods (`BranchService.java:125`, `JobService.java:90`, `TenantUserService.java:158`, `UserPermissionService.java:119`) | CLEAN |
| Assets, asset lines, disposals, maintenance | asset/line/disposal/maintenance IDs | Asset and line loads include tenant; nested line is also checked against path asset (`AssetLineService.java:95-107`; `AssetDisposalService.java:82-101`; `AssetMaintenanceService.java:68-87`) | CLEAN |
| HR | employee, leave type/balance/request, salary/adjustment IDs | All service loads use `...AndTenantId`; validation service scopes employee/branch/job/user (`HrValidationService.java:35-100`) | CLEAN |
| Inventory setup | category, material, UOM, warehouse, supplier, stock-balance IDs | Tenant methods or mixed-table global-or-own visibility (`MaterialService.java:97-123`; `UomService.java:82,149-181`; `WarehouseService.java:107-114`; `SupplierService.java:89`; `StockBalanceService.java:107`) | CLEAN |
| Purchase invoice/return | document and line IDs | Document load by ID+tenant; line searched inside owned aggregate (`PurchaseInvoiceService.java:664`; `PurchaseReturnService.java:669`) | CLEAN |
| Waste and physical count | document/count and line IDs | Owned parent first; child searched inside it (`WasteService.java:433-462`; `PhysicalCountService.java:952`) | CLEAN for direct IDs; request UOM is Finding 1 |
| Order consumption | document ID | ID+tenant including lock transitions (`OrderConsumptionService.java:110-180`) | CLEAN |
| Menu | category/product/add-on/recipe IDs | ID+tenant; nested IDs additionally tied to owned product (`ProductService.java:217-232`; `ProductAddOnService.java:79`; `RecipeService.java:91-105`) | CLEAN |
| Orders/intake/shifts/tables/sections/customers | all path and filter IDs | ID+tenant loaders; filter repositories begin with tenant predicate (`OrderService.java:325`; `IncomingOrderRequestService.java:136`; `ShiftService.java:87`; `TableService.java:112`; `TableSectionService.java:99`; `CustomerRepository.java:15-32`) | CLEAN |
| Admin tenant and tenant-user APIs | tenant/user IDs | Deliberate global admin surface, class-level SYS_ADMIN gate | CLEAN; see §E |

### Referenced IDs in request bodies

| Request reference | Verification | Verdict |
|---|---|---|
| Asset/device/table/section/warehouse/employee `branchId` | `BranchRepository.findByIdAndTenantId` before assignment | CLEAN |
| Employee `jobId`, `userId` | HR validation uses tenant-scoped job/user loads | CLEAN |
| Asset disposal/maintenance `assetId`, `assetLineId` | Both tenant-scoped; line-to-asset consistency checked | CLEAN |
| Product `menuCategoryId`, `parentProductId`; add-on product ID | Category/product tenant loaders; parent/add-on tenant loaders | CLEAN |
| Recipe item `materialId`, `uomId` | Material ID+tenant; UOM global-or-own post-load check | CLEAN |
| Order `tableId`, line `productId`, derived recipe/customer/branch/shift | Table/product/recipe/branch/shift are tenant-scoped; customer resolution is `(tenantId, phone)` | CLEAN |
| Intake `branchId`, linked `orderId` | Both ID+tenant | CLEAN |
| Stock add/opening references | Warehouse/material tenant loads; opening request is internal-only | CLEAN current call graph |
| Physical-count `warehouseId`, material IDs/line IDs | Warehouse/material ID+tenant; child line inside owned count | CLEAN |
| Purchase invoice `supplierId`, `warehouseId`, line `materialId`, `uomId` | Tenant loads; UOM global-or-own check | CLEAN |
| Purchase return `originalInvoiceId`, `originalLineId`, `uomId` | Original invoice ID+tenant; line inside invoice; UOM global-or-own check | CLEAN |
| Waste `warehouseId`, `materialId` | ID+tenant | CLEAN |
| Waste `uomId` | Unqualified UOM load plus convertibility only | **FINDING 1** |

Tests do not encode any currently absent tenant control as an invariant. The former reflection
contract now asserts that purchase post annotations are present
(`PurchaseInvoiceControllerContractTest.java:23-42`). Security slice tests that still send
`X-Tenant-Id` mostly mock tenant resolution; they are authorization tests, not evidence that the
header selects the tenant. Only the dedicated central-binding and three cross-tenant integration
tests demonstrate the header boundary end to end.

## §C — Reports and native SQL

### Seven native queries

| Query | Tenant predicate / provenance | Verdict |
|---|---|---|
| Purchase-price drift | `WHERE batch.tenant_id = :tenantId`; invoice join requires `invoice.tenant_id = batch.tenant_id`; reversal subquery requires the same (`StockBatchRepository.java:132-183`) | CLEAN |
| Physical-count movement references | Transaction IDs originate from tenant-scoped `findPhysicalCountMovements`; each reference join requires its parent tenant to equal `tx.tenant_id` (`InventoryTransactionRepository.java:435-459`) | CLEAN BY PROVENANCE |
| Sales by product | `WHERE o.tenant_id = :tenantId` (`OrderLineRepository.java:42-62`) | CLEAN |
| Payment methods by shift | `AND tenant_id = :tenantId` (`OrderRepository.java:74-87`) | CLEAN |
| Sales over time | `WHERE o.tenant_id = :tenantId` (`OrderRepository.java:111-136`) | CLEAN |
| Sales by hour | `WHERE o.tenant_id = :tenantId` (`OrderRepository.java:149-176`) | CLEAN |
| Sales by payment method | `WHERE o.tenant_id = :tenantId` (`OrderRepository.java:195-222`) | CLEAN |

### Four `JdbcTemplate` consumers

| Consumer | Predicate | Verdict |
|---|---|---|
| `TenantTimeZoneService` | Tenant timezone reads system table by tenant PK; branch timezone is `WHERE id=? AND tenant_id=?` (`TenantTimeZoneService.java:123-145`) | CLEAN |
| `UomLookupVersionService` | `WHERE tenant_id IS NULL OR tenant_id = ?` (`UomLookupVersionService.java:147-165`) | CLEAN MIXED TABLE |
| `PhysicalCountCodeSequenceService` | insert/upsert conflict scope is `(tenant_id, warehouse_id, scheduled_date)` (`PhysicalCountCodeSequenceService.java:17-29`) | CLEAN |
| `SchedulingConfig` / ShedLock | Library access to global `shedlock`; no business rows (`SchedulingConfig.java:25-34`) | SYSTEM |

### Report controllers

| Report surface | Scoping evidence | Verdict |
|---|---|---|
| Low stock | `StockBalanceRepository.findForLowStock` starts with `b.tenantId=:tenantId` (`StockBalanceRepository.java:87-114`) | CLEAN |
| Stock valuation | `findForStockValuation` starts with tenant (`StockBalanceRepository.java:57-80`) | CLEAN |
| Shrinkage | `t.tenantId=:tenantId`; follow-up material load also takes tenant (`InventoryTransactionRepository.java:277-304`) | CLEAN |
| Waste analysis | `t.tenantId=:tenantId`; follow-up material load also takes tenant (`InventoryTransactionRepository.java:328-356`) | CLEAN |
| Purchase-price drift | Native predicate quoted above | CLEAN |
| Loss comparison | Transaction join and material root both require tenant (`InventoryTransactionRepository.java:374-426`) | CLEAN |
| Sales over time/hour/product/payment method | Native predicates quoted above | CLEAN |
| Asset summary | `AssetLineRepository.findByTenantId` | CLEAN |
| Asset disposals | Page is tenant-scoped; inherited enrichment IDs come only from that page (`AssetReportService.java:54-67`) | CLEAN BY PROVENANCE |

For report controls, the failure condition is removal/omission of the repository tenant argument
or an arbitrary-ID caller entering the two provenance-based enrichments. Integration tests exercise
several report calculations and controller permissions, but there is no comprehensive two-tenant
fixture for every report. Therefore the CLEAN verdicts above are code/predicate verification, not a
claim that each predicate has been mutation-tested.

## §D — Identifier collisions and constraints

### Customer phone

The live schema has `UNIQUE (tenant_id, phone)` (`V18__loyalty_customer.sql:23-26`). Both normal
and duplicate-insert retry lookups use `findByTenantIdAndPhone` (`CustomerService.java:51-80`). A
phone shared by tenants A and B resolves independently. **CLEAN.** It would fail if either lookup
dropped tenant; both call sites were inspected, and `CustomerServiceTest` exercises lookup/create
but is not a two-tenant database proof.

### Roles

The live schema confirms `roles_code_key UNIQUE (code)` while `roles.tenant_id` is nullable
(`V1__foundation_rbac_schema.sql:268`). Tenant-scoped role creation is **not reachable today**:
role codes are the `RoleCode` enum; tenant/user services attach shared role rows; `/api/rbac/roles`
is read-only; `/sys-admin/rbac` only edits permissions on existing global roles. The constraint
therefore causes no present cross-tenant lookup.

It forecloses a future tenant role from reusing any global role code and forecloses two tenants
from independently using the same custom code. That is a schema/product dependency, not a current
tenant-isolation exploit. Per-tenant roles must not be declared supported while this constraint and
the enum/global repository model remain.

### Sequences and document numbers

- `TenantSequenceService` locks/counters `(tenantId, year, sequenceKey)`
  (`TenantSequenceService.java:43-57`); live constraint:
  `invoice_sequence (tenant_id, year, doc_type)`.
- Physical-count codes upsert `(tenant_id, warehouse_id, scheduled_date)` and the live unique
  constraint matches.
- Purchase invoice number is unique on `(tenant_id, invoice_number)`; return number on
  `(tenant_id, return_number)`; physical-count/waste/order-code searches are under tenant filters.
- No endpoint resolves a purchase document by number alone. Direct document endpoints resolve the
  global PK with `findByIdAndTenantId`. Order number is an optional condition inside a tenant-rooted
  query (`OrderRepository.java:44-63`).

Therefore a number generated in A cannot resolve B's document. **CLEAN.** This is demonstrated by
schema/catalog inspection and call-site search, not by assuming globally unique display numbers.

### Idempotency keys

Live PostgreSQL catalog output confirms:

- `inventory_transaction`: `UNIQUE (tenant_id, idempotency_key)`.
- `orders`: `UNIQUE (tenant_id, idempotency_key)`.

Both lookup paths include tenant (`InventoryTransactionRepository.java:24`,
`OrderRepository.java:31`). **CLEAN.** The failure condition would be a global unique or global
lookup; neither exists in the schema or services.

### Foreign keys and other uniqueness

A live `pg_constraint` query counted **148 foreign keys and zero composite foreign keys**. Thus the
database itself does not enforce `child.tenant_id = referenced.tenant_id`; all such integrity is in
services. The audit traced current request references and found service checks generally present.
Finding 1 is the demonstrated consequence where one is absent: both `waste_line.uom_id` and
`inventory_transaction.entered_uom_id` are simple FKs to `uom(id)`.

Other tenant business uniques inspected are tenant-qualified: branch/job/material/supplier/
warehouse/employee/leave-type codes, users' username/email, stock balance, product add-on,
permissions, documents and idempotency. Child-local uniques such as
`inventory_transfer_line(transfer_id, material_id)` inherit scope through their parent. Device
secret hash is intentionally installation-wide because it is a credential. No second reachable
collision or cross-reference was found. Durable database isolation design is Phase 2 and is
deliberately not recommended here.

## §E — Legitimate cross-tenant and global surfaces

### Actual SYS_ADMIN cross-tenant surfaces

| Route(s) | Operations | Gate | Verdict |
|---|---|---|---|
| `/api/admin/tenants`, `/{id}`, `/{id}/status`, `/{tenantId}/owner` | Create/list/read/update/status tenants; create owner | Class-level `@PreAuthorize("@securityService.isSysAdmin()")` at `TenantController.java:28` | CLEAN |
| `/api/admin/tenants/{tenantId}/users`, `/{userId}`, `/{userId}/status` | Create/list/read/update/status tenant users | Class-level SYS_ADMIN at `UserController.java:26` | CLEAN |
| `/sys-admin/uom`, `/{id}/deactivate` | List/create/deactivate global UOMs | Class-level SYS_ADMIN at `PanelUomController.java:25` | CLEAN |
| `/sys-admin/rbac/roles`, `/roles/{roleCode}/permissions` | List roles; read/replace global role permissions | Class-level SYS_ADMIN at `RoleController.java:22` | CLEAN |

No SysAdmin material-catalog management controller exists in this checkout. The two catalog reads
below are global reference-data surfaces rather than cross-tenant business-row access:

- `/api/inventory/global-materials` is available to SYS_ADMIN or
  `INVENTORY_SETUP_VIEW`; its tenant-dependent `alreadyImported` component is scoped to the
  effective tenant (`MaterialCatalogController.java:26-40`).
- `/api/inventory/global-material-categories` is the same permission model and returns only
  `tenant_id IS NULL` rows (`GlobalMaterialCategoryController.java:23-33`).
- `/api/rbac/roles` is an authenticated read of installation-wide role metadata, not tenant-owned
  role rows (`RbacRoleController.java:13-20`).

These shared reads do not enumerate another tenant's rows. All actual multi-tenant administration
routes found are in the explicit SYS_ADMIN table above.

## Ranked remediation list

1. **CRITICAL — waste/ledger UOM ownership:** close the foreign-tenant UOM acceptance on both waste
   line add/update and the ledger entry boundary. Add a two-tenant regression whose foreign UOM is
   deliberately convertible through a shared global base, assert stored state, and prove the same
   document succeeds with an own/global UOM. This avoids the unrelated-precondition false positive
   described in the prompt.

   > **DONE — `8519821`, `b3b3fdd`, 2026-08-31. Suite 709/709.** Fixed at the lookup, not at the
   > call site: `findResolvableByIdForTenant` is now the only way a UOM is loaded by id. Both
   > missing sites use it, and the five services that carried a hand-written visibility check use
   > it too, with that check removed. One inherited `findById` survives on purpose —
   > `UomService.loadUom` (`:285`) serves the SysAdmin deactivate path, which passes a null
   > tenantId and must still resolve globals; no tenant is available in scope to supply.
   >
   > The regression is in `CrossTenantIsolationIntegrationTest`, covering line add, line update
   > and posting, each paired with a positive control. Confirmed failing against a local revert:
   > A's document returned HTTP 200 carrying `uomId 977501 / uomSymbol "bwp"`.

2. **Make the caller-scoping requirement visible at the declaration.** The methods this audit
   classified as CLEAN BY PROVENANCE / SAFE CURRENT CALLER / CLEAN BY PARENT carry no tenant
   predicate and hold only because of who calls them. `UomRepository.findById` had exactly that
   shape, which is how Finding 1 came to exist — so the classification belongs next to the code,
   not only in this file.

   > **DONE — `d7ca4ab`, 2026-08-31.** 21 methods across 10 repositories now carry
   > `@TenantUnscoped("<what the caller must guarantee>")`
   > (`src/main/java/com/smart/restaurant_saas/tenant/TenantUnscoped.java`), a SOURCE-retention
   > marker chosen over a `...Unscoped` name suffix because it carries the precondition as a value
   > and does not churn call sites: `InventoryTransactionRepository.findReversalOf`,
   > `findPhysicalCountMovementReferences`; `OrderConsumptionRepository.findByIdForUpdate`,
   > `findBatchingCandidates`; `OrderConsumptionLineRepository.findExistingOrderLineIds`,
   > `sumRecipeQuantitiesByDocId`, `countLinesByDocIds`, `findLinesByDocId`;
   > `OrderConsumptionMaterialRepository.findByDocId`;
   > `PhysicalCountLineRepository.findByPhysicalCountId`, `findByPhysicalCountIdWithDetails`;
   > `StockBatchRepository.findByStockBalanceIdOrderByMovementDateAscIdAsc`,
   > `findByStockBalanceIdAndStatusOrderByMovementDateAscIdAsc`,
   > `findByStockBalanceIdAndSourceInvoiceLineId`, `sumOpenBatchTotals`;
   > `UomRepository.countMaterialsUsingUom`; `ProductRepository.existsByParentProductId`;
   > `OrderRepository.existsByTableId`, `existsByTableSectionId`;
   > `TableRepository.existsBySectionId`, `findAllBySectionId`.
   >
   > It is documentation, not enforcement — nothing reads it at runtime. No tenant predicates were
   > added: several of these are legitimately parent-scoped and changing their signatures would
   > ripple through their callers.
   >
   > The inherited `JpaRepository` calls in §A cannot be marked — `findById`, `findAllById` and
   > `findAll` are declared in Spring's interface, so they remain recorded here only. Likewise
   > `UserRepository.findByUsername`, which is an unused declaration rather than a caller-scoped
   > one; deleting it is the cleaner fix and is left separate.

No Phase 2 architecture recommendation is included. The zero-composite-FK catalog result is
recorded as a dependency for that deferred work, not turned into an RLS/Hibernate/schema design.

## Explicitly left unaudited

- Phase 2 durable isolation options and architecture recommendation (RLS, Hibernate filters, or
  alternative database enforcement), explicitly deferred by the task.
- Tenant settings entities and per-tenant thresholds, explicitly out of scope.
- Separating system routes from tenant routes or narrowing SYS_ADMIN header selection; §0 provides
  the requested complete current-route input only.
- Runtime black-box exploitation of Finding 1. The path is settled from reachable controller,
  service, converter, mapper and live FK evidence, but no production/test data was mutated solely
  to demonstrate it.

Within Phase 1 §§0–E, **nothing else is marked not audited**. Items called CLEAN BY PROVENANCE are
settled for the current call graph but explicitly identify the condition under which a new caller
would invalidate the result.
