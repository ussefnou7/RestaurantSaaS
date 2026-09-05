# Actor identity and authorization enforcement audit

Audit date: 2026-09-05. Backend revision: `7b17b5d`. Findings only; no application changes, tests, migrations, requests that mutate data, or commits were made during this audit. Existing test output is cited as historical execution evidence, not as a new test run.

Evidence uses repository-relative `path:line`. Source inventories exclude comments and account for class-level annotations. External deployment settings and deployed runtime behavior were not inspected. The requested `claude/PROMPT_TENANT_ISOLATION_AUDIT.md` is absent from this checkout; the available boundary reports are cited under Q4. O29 and O19 were read as investigation context, not proof of behavior (`docs/DECISIONS.md:4317`; `docs/DECISIONS.md:4123`).

Stop conditions were checked first. Production method security is enabled (`src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:22`). The original tenant stop report has a later boundary audit confirming remediation; Q4 cites both, without repeating the tenant audit (`claude/TENANT_ISOLATION_AUDIT.md:14`; `claude/TENANT_ISOLATION_PHASE1.md:44`). Neither stop condition is established against the current checkout.

## Q1 — Acting user identity

**WEAK — The actor provider uses the signed JWT, but 68 controller header parameters provide a separate caller-controlled identity for writes and cashier operations.** Evidence: `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:73`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:84`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:36`; complete inventory below.

### Verified identity path

`JwtService.generateAccessToken` signs the database user's ID into `userId`; parsing verifies the signature and reads that claim into `CurrentUserPrincipal` (`src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:145`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:29`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:45`). The JWT filter places that principal in the SecurityContext, and `getActorUserId()` returns its `userId()` (`src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:73`). `CurrentUserService.getCurrentUserId()` has the same principal source (`src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserService.java:17`).

There is **no token/header fallback in these identity providers**. A missing/non-numeric `userId` claim causes `getLongClaim` to throw and the filter to return 401. Missing authentication or the wrong principal type causes the actor provider to throw an explicit 401; it does not return a default or null. With neither token nor header, no actor is established. A header alone does not authenticate a request (`src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:60`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:49`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:68`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:85`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:40`).

### Attribution is a separate, unbound path

Expense permission evaluation uses the JWT actor via `SecurityService.hasPermission`, but the create/void controller forwards `X-User-Id` separately. `ExpenseService` stores that supplied value in `createdBy`, `voidedBy`, and `updatedBy`, without principal comparison or user lookup. Thus a caller who holds **EXPENSES_CREATE** can record an expense under a colleague's ID, and one who holds **EXPENSES_VOID** can attribute a void to a colleague. This does **not** confer the colleague's permissions; it falsifies the actor recorded after the caller's own permission gate succeeds (`src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:42`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:75`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:90`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:124`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:131`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:159`).

For expenses, the actor columns are scalar BIGINTs, and the migration's FKs cover tenant, branch, and category only. Even an invented actor ID is not rejected by an actor FK. Void-field checks require presence, not truthful identity (`src/main/resources/db/migration/V54__expenses.sql:29`; `src/main/resources/db/migration/V54__expenses.sql:40`; `src/main/resources/db/migration/V54__expenses.sql:117`).

Neither the JWT filter nor the entity listener repairs this: the filter does not read/compare `X-User-Id`; the listener only stamps timestamps, while BaseEntity actor columns are nullable scalar values (`src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:47`; `src/main/java/com/smart/restaurant_saas/common/TenantTimestampListener.java:44`; `src/main/java/com/smart/restaurant_saas/common/BaseEntity.java:31`). This establishes the architectural cause behind O29 without adopting its historical coverage percentage as a current measurement.

### Every direct X-User-Id reader

There are **68 parameters in 21 controllers: 63 optional and 5 required**. No production service directly reads this HTTP header; controllers pass scalar IDs to the service consumers listed next. The only additional production string occurrence is the CORS allowlist, which permits transport and does not validate identity (`src/main/java/com/smart/restaurant_saas/config/CorsConfig.java:33`).

Missing behavior notation:

- **O:** `required=false` supplies null; there is no principal fallback. This describes actor handling, not a promise that unrelated business validation succeeds.
- **R:** required header omission prevents handler invocation. The shared catch-all maps otherwise-unhandled exceptions to 500, with no dedicated missing-header handler. Exact live missing-header response is **UNVERIFIED**; a request through the production MVC advice would establish it. Do not assume the default Spring 400 (`src/main/java/com/smart/restaurant_saas/common/GlobalExceptionHandler.java:85`).
- **O-order:** an absent header supplies null to the cashier shift query; a normal shift has a cashier, so the service throws `NO_OPEN_SHIFT_FOR_CASHIER` rather than substituting the JWT actor (`src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:107`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:168`).

| Reader evidence | Method | Header | When missing |
|---|---|---|---|
| `src/main/java/com/smart/restaurant_saas/assets/disposal/AssetDisposalController.java:88` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/assets/maintenance/AssetMaintenanceController.java:88` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/device/DeviceController.java:43` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/device/DeviceController.java:67` | `deactivate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:84` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:97` | `voidExpense` | required | Rejected before handler invocation (R). |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryController.java:47` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryController.java:60` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryController.java:71` | `activate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryController.java:82` | `deactivate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionController.java:84` | `recalculate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:112` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:165` | `start` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:179` | `revertToDraft` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:196` | `updateCountedQuantities` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:219` | `reconcile` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:234` | `cancel` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:95` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:113` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:172` | `complete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:187` | `post` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:203` | `unpost` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:218` | `cancel` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:250` | `uncomplete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:76` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:92` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:122` | `addLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:138` | `updateLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:153` | `deleteLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:166` | `complete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:182` | `post` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:197` | `unpost` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:212` | `uncomplete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:226` | `cancel` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/warehouse/WarehouseController.java:168` | `addMaterial` | required | Rejected before handler invocation (R). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:77` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:93` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:109` | `addLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:125` | `updateLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:139` | `deleteLine` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:152` | `complete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:167` | `uncomplete` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:182` | `post` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:196` | `cancel` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/loyalty/customer/CustomerController.java:66` | `findOrCreate` | optional | Ignored whether supplied or absent; no actor forwarded. |
| `src/main/java/com/smart/restaurant_saas/menu/category/MenuCategoryController.java:56` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/category/MenuCategoryController.java:68` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductAddOnController.java:51` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductController.java:77` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductController.java:92` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductController.java:102` | `toggleActive` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/menu/recipe/RecipeController.java:68` | `createNewVersion` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/order/core/OrderController.java:53` | `createCompletedOrder` | optional | Null reaches open-shift lookup; no matching cashier shift → error (O-order). |
| `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestController.java:46` | `createRequest` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestController.java:85` | `markSentToPos` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestController.java:96` | `linkToCompletedOrder` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:36` | `openShift` | required | Rejected before handler invocation (R). |
| `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:45` | `getCurrentShift` | required | Rejected before handler invocation (R). |
| `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:56` | `closeShift` | required | Rejected before handler invocation (R). |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:61` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:72` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:82` | `activate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:92` | `deactivate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:113` | `updateLayout` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionController.java:60` | `create` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionController.java:71` | `update` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionController.java:81` | `activate` | optional | Null passed to service (O). |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionController.java:91` | `deactivate` | optional | Null passed to service (O). |

### Service consumers and consequences of a missing/present header

The following inventory traces every controller group above to its direct service and relevant downstream actor sink. None compares the supplied actor with the principal. Tenant/resource ownership checks are not actor authentication.

| Service evidence | Use of the supplied ID / absence behavior |
|---|---|
| `src/main/java/com/smart/restaurant_saas/assets/disposal/AssetDisposalService.java:78` | Create stamps disposal.createdBy; absent → null. |
| `src/main/java/com/smart/restaurant_saas/assets/maintenance/AssetMaintenanceService.java:64` | Create stamps maintenance.createdBy; absent → null. |
| `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:41`; `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:62` | Create/deactivate stamp createdBy/updatedBy; absent → null. |
| `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:124`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:159` | Create permits null createdBy; void stamps supplied voidedBy and updatedBy and requires the header at the controller. |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryService.java:40`; `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryService.java:53`; `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryService.java:71` | Create/update/activate/deactivate stamp nullable audit actor values. |
| `src/main/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionService.java:193`; `src/main/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionService.java:468` | Recalculation stamps updatedBy and passes the ID into consumption processing/ledger commands; absent remains null. Order creation also forwards it at `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:136`. |
| `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:253`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:374`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:418`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:423`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:590`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:618` | Create stamps createdBy; start forwards the ID while settling consumption; revert logs it only; counted-quantity update receives but never uses it; reconcile/cancel stamp dedicated actor fields. Missing optional values are not replaced. |
| `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:115`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:130`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:210`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:251`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:297` | Create/update and lifecycle operations accept the nullable ID for audit, completion/posting/reversal/cancellation trace; no actor fallback. |
| `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:104`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:176`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:203`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:217`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:238`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:321`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:397`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:440`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:479` | Header/line edits and lifecycle operations stamp supplied nullable audit/lifecycle actors; posting/reversal forwards them to the ledger. |
| `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:111`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:129`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:150`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:170`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:183`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:212`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:229`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:276`; `src/main/java/com/smart/restaurant_saas/inventory/core/WasteService.java:309` | Create/edit/line/lifecycle actor fields and ledger commands retain the caller's value, including null. |
| `src/main/java/com/smart/restaurant_saas/inventory/core/StockBalanceService.java:131`; `src/main/java/com/smart/restaurant_saas/inventory/core/StockBalanceService.java:181`; `src/main/java/com/smart/restaurant_saas/inventory/core/StockBalanceService.java:360` | Required controller ID is passed as actingUserId; positive opening quantity forwards it to opening-balance creation. The stock-balance creation itself does not stamp createdBy. |
| `src/main/java/com/smart/restaurant_saas/loyalty/customer/CustomerController.java:67`; `src/main/java/com/smart/restaurant_saas/loyalty/customer/CustomerService.java:70` | Header is ignored entirely; customer creation has no actor argument or createdBy assignment. |
| `src/main/java/com/smart/restaurant_saas/menu/category/MenuCategoryService.java:39`; `src/main/java/com/smart/restaurant_saas/menu/category/MenuCategoryService.java:49` | Create/update stamp nullable createdBy/updatedBy. |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductAddOnService.java:63` | Create stamps nullable createdBy. |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductService.java:63`; `src/main/java/com/smart/restaurant_saas/menu/product/ProductService.java:82`; `src/main/java/com/smart/restaurant_saas/menu/product/ProductService.java:92` | Create/update/toggle stamp nullable audit actors. |
| `src/main/java/com/smart/restaurant_saas/menu/recipe/RecipeService.java:63`; `src/main/java/com/smart/restaurant_saas/menu/recipe/RecipeService.java:70`; `src/main/java/com/smart/restaurant_saas/menu/recipe/RecipeService.java:148` | Recipe replacement, creation, and item creation stamp supplied nullable actors. |
| `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:90`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:107`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:168`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:181` | ID stamps order/lines and selects the cashier's open shift in the tenant. Absent ID cannot resolve a normal cashier shift; another cashier's ID can target their open shift. |
| `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestService.java:40`; `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestService.java:58`; `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestService.java:74` | Create/mark-sent/link stamp nullable audit actor values. |
| `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:43`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:75`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:86`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:159` | Open loads header-selected user by tenant only, current selects that user's shift, close stamps the supplied ID. Headers required by controller; neither JWT equality nor cashier branch/status is validated here. Close does not check that caller/header user owns the shift. |
| `src/main/java/com/smart/restaurant_saas/table/TableService.java:49`; `src/main/java/com/smart/restaurant_saas/table/TableService.java:61`; `src/main/java/com/smart/restaurant_saas/table/TableService.java:83`; `src/main/java/com/smart/restaurant_saas/table/TableService.java:102` | Create/update/layout/activate/deactivate stamp nullable actors. |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionService.java:48`; `src/main/java/com/smart/restaurant_saas/table/section/TableSectionService.java:58`; `src/main/java/com/smart/restaurant_saas/table/section/TableSectionService.java:90` | Create/update/activate/deactivate stamp nullable actors. |

The ledger is a downstream consumer, not an identity validator: it copies `cmd.createdBy` and supplied reversal actor IDs into transactions (`src/main/java/com/smart/restaurant_saas/inventory/core/InventoryLedgerService.java:251`; `src/main/java/com/smart/restaurant_saas/inventory/core/InventoryLedgerService.java:162`).

### Every direct getActorUserId consumer

These consumers use the principal-backed provider rather than the header. Authorization/self-action checks and hand-written audit assignments therefore coexist with the unbound path above. All direct production call sites:

| Evidence | Use |
|---|---|
| `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserScopeProvider.java:61` | `Long userId = currentTenantProvider.getActorUserId();` |
| `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:44` | `currentTenantProvider.getActorUserId(),` |
| `src/main/java/com/smart/restaurant_saas/hr/service/EmployeeService.java:77` | `employee.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/EmployeeService.java:115` | `employee.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/EmployeeService.java:126` | `employee.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveBalanceService.java:76` | `balance.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveBalanceService.java:99` | `balance.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveRequestService.java:111` | `leaveRequest.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveRequestService.java:115` | `balance.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveRequestService.java:156` | `balance.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveRequestService.java:159` | `leaveRequest.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveTypeService.java:64` | `leaveType.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveTypeService.java:100` | `leaveType.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/LeaveTypeService.java:110` | `leaveType.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/SalaryAdjustmentService.java:54` | `adjustment.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/SalaryAdjustmentService.java:68` | `adjustment.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/SalaryService.java:68` | `salary.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/hr/service/SalaryService.java:81` | `currentSalary.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/job/service/JobService.java:53` | `job.setCreatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/job/service/JobService.java:74` | `job.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/job/service/JobService.java:84` | `job.setUpdatedBy(currentTenantProvider.getActorUserId());` |
| `src/main/java/com/smart/restaurant_saas/rbac/service/UserPermissionService.java:131` | `if (userId.equals(currentTenantProvider.getActorUserId())) {` |
| `src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:249` | `if (userId.equals(currentTenantProvider.getActorUserId())) {` |

## Q2 — Sysadmin and other role decisions

**WEAK — SYS_ADMIN and the OWNER/BRANCH_MANAGER authorization helpers trust the token's role snapshot, not the current database role.** Evidence: `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:97`; `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:19`; `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:23`.

`isSysAdmin()` delegates to the provider, which compares `CurrentUserPrincipal.roleCode()` with SYS_ADMIN. The principal was constructed from the signed JWT, and the filter separately creates an authority with that same string. There is no live role/user lookup on this branch (`src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:56`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:65`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:81`).

**Yes: revoking a sysadmin's database role does not invalidate their existing token or remove the SYS_ADMIN permission bypass before expiry.** `hasPermission` returns true before reaching its repository, while controller expressions often short-circuit before calling `hasPermission` at all. Tenant selection still validates the selected tenant's active status; other ordinary business checks can still fail. This is retained authorization, not a claim that every operation must succeed (`src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:33`; `src/main/java/com/smart/restaurant_saas/tenant/TenantController.java:28`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:40`).

Every direct token-role read or propagation, and every distinct authorization use:

| Evidence | Use |
|---|---|
| `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:38`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:56` | Claim writing and principal reconstruction. |
| `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:65` | Copies roleCode into SecurityContext authorities. No production hasRole/hasAuthority/getAuthorities authorization consumer was found; existing custom helpers read the principal. |
| `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:77`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:97` | Exposes roleCode and decides SYS_ADMIN, including cross-tenant header selection at line 40. |
| `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:23` | OWNER or BRANCH_MANAGER permits HR salary, adjustment, balance, and request surfaces through class-level guards. |
| `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:28` | OWNER permits leave-type mutations. |
| `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:33` | SYS_ADMIN bypasses every permission query. |
| `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserScopeProvider.java:24`; `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserScopeProvider.java:33` | SYS_ADMIN bypasses database branch scoping: tenant-wide scope and no restricted branch. Non-sysadmins load the current database user and role at lines 59 and 68, but do not check either active state. |
| `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserService.java:29` | Exposes token roleCode; no production call site of this accessor was found. It is not a separate authorization gate. |

The full controller inventory below covers **213 role-helper annotations in 54 files**, including class-level guards. Each row anchors the first annotation; the line list enumerates every occurrence in that exact file. Permission portions of these expressions additionally query live user permissions for non-sysadmins. The role-only HR checks do not (`src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:23`; `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:42`).

| Controller evidence | All role-helper annotation lines in that file | Token role helper |
|---|---|---|
| `src/main/java/com/smart/restaurant_saas/assets/asset/AssetController.java:35` | 35, 44, 54, 64, 75 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/assets/assetline/AssetLineController.java:33` | 33, 42, 53, 65 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/assets/disposal/AssetDisposalController.java:44` | 44, 54, 78 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/assets/maintenance/AssetMaintenanceController.java:44` | 44, 54, 78 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/assets/report/AssetReportController.java:29` | 29, 38 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/branch/BranchController.java:31` | 31, 38, 44, 50, 59 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/device/DeviceController.java:35` | 35, 49, 59 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:41` | 41, 68, 77, 90 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/expense/category/ExpenseCategoryController.java:33` | 33, 41, 53, 65, 76 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/hr/controller/EmployeeController.java:31` | 31, 38, 44, 50, 59 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/hr/controller/LeaveBalanceController.java:22` | 22 | OWNER / BRANCH_MANAGER |
| `src/main/java/com/smart/restaurant_saas/hr/controller/LeaveRequestController.java:24` | 24 | OWNER / BRANCH_MANAGER |
| `src/main/java/com/smart/restaurant_saas/hr/controller/LeaveTypeController.java:31` | 31, 38, 44, 50, 59 | SYS_ADMIN; SYS_ADMIN / OWNER |
| `src/main/java/com/smart/restaurant_saas/hr/controller/SalaryAdjustmentController.java:23` | 23 | OWNER / BRANCH_MANAGER |
| `src/main/java/com/smart/restaurant_saas/hr/controller/SalaryController.java:22` | 22 | OWNER / BRANCH_MANAGER |
| `src/main/java/com/smart/restaurant_saas/inventory/category/GlobalMaterialCategoryController.java:24` | 24 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/category/MaterialCategoryController.java:37` | 37, 53, 67, 82, 95 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/material/MaterialCatalogController.java:27` | 27 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/material/MaterialController.java:41` | 41, 58, 71, 86, 102, 117, 130 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionController.java:36` | 36, 52, 61, 74 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/physicalcount/PhysicalCountController.java:41` | 41, 57, 80, 101, 118, 133, 148, 170, 184, 201, 224, 240 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:41` | 41, 54, 67, 84, 101, 118, 132, 148, 162, 177, 192, 208, 224, 240 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:41` | 41, 52, 65, 82, 97, 111, 127, 143, 158, 171, 187, 202, 217, 232 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/purchase/SupplierController.java:37` | 37, 52, 65, 78, 91, 104 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/LossComparisonReportController.java:29` | 29 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/LowStockReportController.java:26` | 26 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/PurchasePriceDriftReportController.java:29` | 29 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/ShrinkageReportController.java:29` | 29 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/StockValuationReportController.java:26` | 26 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/reports/WasteAnalysisReportController.java:29` | 29 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/stock/StockBalanceController.java:27` | 27 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/uom/PanelUomController.java:25` | 25 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/uom/UomController.java:39` | 39, 66, 93, 106, 122, 136 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/warehouse/WarehouseController.java:43` | 43, 60, 73, 87, 101, 114, 127, 143, 156, 174 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/inventory/waste/WasteController.java:40` | 40, 56, 68, 83, 98, 114, 130, 144, 157, 172, 187 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/job/controller/JobController.java:31` | 31, 38, 44, 50, 59 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/loyalty/customer/CustomerController.java:35` | 35, 56 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/menu/MenuController.java:25` | 25 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/menu/category/MenuCategoryController.java:34` | 34, 42, 51, 62, 73 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductAddOnController.java:33` | 33, 42, 57 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/menu/product/ProductController.java:37` | 37, 51, 60, 69, 83, 97, 107 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/menu/recipe/RecipeController.java:32` | 32, 41, 50, 59 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/order/core/OrderController.java:43` | 43, 59, 68 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/order/intake/IncomingOrderRequestController.java:41` | 41, 52, 61, 80, 90 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/order/reports/SalesByPaymentMethodReportController.java:30` | 30 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/order/reports/SalesByProductReportController.java:30` | 30 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/order/reports/SalesOverTimeReportController.java:43` | 43, 71 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:31` | 31, 42, 51 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/rbac/controller/RoleController.java:22` | 22 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/table/TableController.java:37` | 37, 47, 56, 66, 77, 87, 97, 107 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/table/section/TableSectionController.java:36` | 36, 46, 55, 65, 76, 86, 96 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/tenant/TenantController.java:28` | 28 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/user/controller/TenantUserController.java:32` | 32, 39, 45, 51, 60, 70 | SYS_ADMIN |
| `src/main/java/com/smart/restaurant_saas/user/controller/UserController.java:26` | 26 | SYS_ADMIN |

Other `roleCode` search hits concern request DTO role selection, error parameters, or **database** roles, not additional token-role authorization. In particular login's role and POS permission decisions use the loaded role (`src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:78`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:134`); permission-edit target exclusions use the target user's database role (`src/main/java/com/smart/restaurant_saas/rbac/service/UserPermissionService.java:137`; `src/main/java/com/smart/restaurant_saas/rbac/service/UserPermissionService.java:150`). Tenant user role selection and HR employee-link exclusions likewise concern selected database roles (`src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:100`; `src/main/java/com/smart/restaurant_saas/hr/service/HrValidationService.java:107`).

## Q3 — Active user enforcement

**BROKEN — Deactivating a user does not reject their existing authenticated requests, and the permission query ignores user and role active state.** Evidence: `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59`; `src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`; `src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:122`.

The actual permission JPQL is:

```sql
select count(up) > 0
from UserPermission up, Permission p
where p.id = up.permissionId
  and p.active = true
  and up.tenantId = :tenantId
  and up.userId = :userId
  and p.code = :permissionCode
```

Source: `src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`. It joins **only user_permissions and permissions**. The active check belongs to the permission definition, not the user. It does not join users, roles, or role links.

The user model actually has `status = ACTIVE/...`, not a boolean active column; its role link is `users.role_id`. UserPermission has no active field. Therefore there is no active user-role-link predicate either (`src/main/java/com/smart/restaurant_saas/user/entity/User.java:49`; `src/main/java/com/smart/restaurant_saas/user/entity/User.java:56`; `src/main/java/com/smart/restaurant_saas/rbac/entity/UserPermission.java:24`).

Role inactivity is also not checked when a new token is minted: login's `findRoleOrFail` uses the inherited `findById` rather than an active-role lookup. An active user assigned an inactive role can still reach token creation; existing user-permission assignments remain the permission query's authority (`src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:182`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:99`; `src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`).

The request filter only parses JWTs and sets authentication; SecurityConfig only applies route rules. Neither loads a user. `CurrentUserScopeProvider` does load a user/role for some branch checks, but uses `findByIdAndTenantId` and `findById` without status/active checks (`src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:25`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:28`; `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserScopeProvider.java:59`; `src/main/java/com/smart/restaurant_saas/user/repository/UserRepository.java:11`). The other request filter decorates UOM lookup versions, not user authentication (`src/main/java/com/smart/restaurant_saas/inventory/uom/UomLookupVersionHeaderFilter.java:37`).

The confirmed active-user check is at **login**. Another active-user check in HR validates an optional employee-linked target user, not the acting principal. It cannot be credited as a request authentication control (`src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:75`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:96`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:187`; `src/main/java/com/smart/restaurant_saas/hr/service/HrValidationService.java:92`; caller: `src/main/java/com/smart/restaurant_saas/hr/service/EmployeeService.java:60`).

Status-only deactivation saves the status and leaves permission assignments intact. The tenant user's delete operation is also a status change. Platform status updates likewise do not revoke tokens or assignments (`src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:122`; `src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:137`; `src/main/java/com/smart/restaurant_saas/user/service/UserService.java:169`).

**What a deactivated user can still do:** until JWT expiry, they can continue authentication-only reads, use permission-gated operations whose live permission assignment remains present/active, and retain any token-role bypass. For example, a deactivated user still assigned EXPENSES_CREATE can create expenses; one with EXPENSES_VOID can void them. This assumes their tenant and ordinary resource prerequisites remain valid. A new password login fails; that does not cancel the old token (`src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:75`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:90`; `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:42`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:187`).

### Endpoints without PreAuthorize

Count: **4 of 246 explicitly mapped controller handler methods** have neither method- nor class-level `@PreAuthorize`. Expanding three three-path permission aliases gives 252 explicit method/path mappings; the unguarded count remains four. Of 37 handlers without a local annotation, 33 inherit a class-level gate. These counts exclude implicit HEAD/OPTIONS, framework documentation handlers, and comments. Alias evidence: `src/main/java/com/smart/restaurant_saas/rbac/controller/PermissionController.java:34`, `src/main/java/com/smart/restaurant_saas/rbac/controller/PermissionController.java:40`, `src/main/java/com/smart/restaurant_saas/rbac/controller/PermissionController.java:49`. Class-level examples: `src/main/java/com/smart/restaurant_saas/user/controller/UserController.java:26`; `src/main/java/com/smart/restaurant_saas/hr/controller/SalaryController.java:22`.

| Endpoint | Evidence | Effective access |
|---|---|---|
| POST /api/auth/login | `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:22` | Public credential exchange; checks user status in AuthService. |
| POST /api/devices/login | `src/main/java/com/smart/restaurant_saas/device/DeviceController.java:71` | Public device-secret exchange; checks device active state. |
| GET /api/auth/me | `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:27`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:64` | Authentication only; loads the user without status restriction. |
| GET /api/rbac/roles | `src/main/java/com/smart/restaurant_saas/rbac/controller/RbacRoleController.java:18`; `src/main/java/com/smart/restaurant_saas/rbac/service/RoleService.java:35` | Authentication only; returns active role catalog, no actor-status check or permission requirement. |

The two authentication-only reads are reachable by deactivated users with valid tokens; the public login exceptions are not themselves evidence of unauthorized access (`src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:35`).

## Q4 — Acting tenant identity

**SAFE for the previously audited tenant-binding boundary — existing reports cover this question; the original critical finding was superseded by the later binding audit.** Evidence: `claude/TENANT_ISOLATION_AUDIT.md:14`; `claude/TENANT_ISOLATION_PHASE1.md:44`.

Per the prompt, Q4 was not re-derived. The original report at revision 63ff8e7 found unvalidated header-based tenant context on routes bypassing the provider (`claude/TENANT_ISOLATION_AUDIT.md:36`). The later Phase 1 report explicitly verified JWT-derived tenant context, ignored tenant headers for ordinary principals, annotation-based argument resolution independent of permissions, and deliberate SYS_ADMIN tenant selection (`claude/TENANT_ISOLATION_PHASE1.md:48`; `claude/TENANT_ISOLATION_PHASE1.md:49`; `claude/TENANT_ISOLATION_PHASE1.md:51`; `claude/TENANT_ISOLATION_PHASE1.md:52`).

This is a citation of that established boundary, not a new whole-repository tenant certification. The provider inspected for Q1/Q2 agrees with that documented mechanism (`src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:37`). Retained token SYS_ADMIN privileges remain a distinct Q2 finding; they do not turn ordinary tenant headers into an unvalidated auth source.

## Q5 — Method security enabled and demonstrated

**UNVERIFIED for the complete production runtime path; enabling configuration is present and real controller gates are demonstrated in MVC slices.** Evidence: `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:22`; `src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:67`.

Production `SecurityConfig` has `@Configuration` and `@EnableMethodSecurity`; the stop condition “method security is not enabled” does not fire (`src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:20`).

An existing test named **ExpenseControllerSecurityTest.listRequiresExpensesView** invokes the real mapped `GET /api/expenses` controller without the permission and asserts 403. The existing Surefire artifact records a completed passing testcase (`src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:67`; `target/surefire-reports/TEST-com.smart.restaurant_saas.expense.ExpenseControllerSecurityTest.xml:97`). This proves annotation interception in that test context, not merely a unit call to SecurityService.

However, that slice disables servlet filters, imports its **own** `@EnableMethodSecurity`, and installs a RecordingSecurityService with in-memory permission answers (`src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:41`; `src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:186`; `src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:195`; `src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:214`). It would not demonstrate that production configuration had enabled method security if that production annotation were removed.

No existing full-application test was found that combines the production filter/configuration, a real JWT principal, a real missing database permission, and a specific 403 assertion. The full cross-tenant test uses real JWTs but seeds the tested permissions and checks cross-tenant failures rather than a missing-permission 403 (`src/test/java/com/smart/restaurant_saas/tenant/CrossTenantIsolationIntegrationTest.java:44`; `src/test/java/com/smart/restaurant_saas/tenant/CrossTenantIsolationIntegrationTest.java:112`; `src/test/java/com/smart/restaurant_saas/tenant/CrossTenantIsolationIntegrationTest.java:152`). A production-context denial test or an observed authorized read-only runtime probe would establish the remaining point. Neither was created or executed in this audit.

## Q6 — Token lifetime, refresh, revocation

**WEAK — Access tokens default to 24 hours, with no refresh-token or per-token revocation mechanism in the inspected backend.** Evidence: `src/main/resources/application.yml:47`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:29`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:45`.

| Checked-in configuration | expirationMinutes |
|---|---|
| `src/main/resources/application.yml:47` | `${APP_JWT_EXPIRATION_MINUTES:1440}`: default 1,440 minutes / 24 hours. |
| `src/test/resources/application.yml:51` | Same environment override and default. |

No profile-specific application configuration file or profile-specific lifetime override is checked in. The deployed environment's actual override is **UNVERIFIED**; deployment configuration would be needed. The default is not asserted to be the deployed value (`src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:23`; configuration anchors above).

There is no refresh route, refresh token in LoginResponse, or refresh exchange implementation. AuthController exposes login and me only; the sole production access-token generation caller is the password-login response builder. Thus refresh storage and active-state rechecking “at refresh” are **not applicable**, not a hidden safe fallback. Re-login performs the active-user checks described in Q3 (`src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:22`; `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:27`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:145`).

The response record contains only `accessToken` and `user` (`src/main/java/com/smart/restaurant_saas/auth/dto/response/LoginResponse.java:3`).

No token blacklist, revocation lookup, session version, issued-before cutoff, or logout invalidation was found in the production authentication path. Parsing verifies signed claims and reconstructs the principal, then the filter authenticates it; status mutations do not touch token validity (`src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:45`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59`; `src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:122`).

An issued token therefore remains **cryptographically accepted** until expiry while its signing key remains accepted. It is not accurate to say every endpoint remains usable “no matter what”: live permission removal/permission deactivation can deny non-sysadmins, and tenant deactivation blocks provider-scoped operations. Neither invalidates the token itself, and neither substitutes for user deactivation enforcement (`src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`; `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:117`).

## Q7 — Authentication paths and POS attribution

**WEAK — Bearer authentication identifies a user; device login issues no token, and later cashier actions can use an independently supplied actor header.** Evidence: `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:29`; `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:87`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:36`.

| Path | Authentication input and identity output | Evidence |
|---|---|---|
| POST /api/auth/login, tenantCode supplied | Tenant + username/password; requires active tenant/user. Returns user JWT with userId, tenantId, username and roleCode. | `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:87`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:145` |
| POST /api/auth/login, tenantCode absent/blank | System-tenant username/password; requires active user and SYS_ADMIN database role. Returns the same user JWT shape. | `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:53`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:72` |
| POST /api/auth/login, optional deviceId supplied | Additional POS check: SHIFTS_OPEN, device belonging to user's tenant, user's branch equals device branch. Still returns a **user-only JWT**; no deviceId/branch claim. | `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:108`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:33` |
| POST /api/devices/login | Hashes submitted device secret, finds device, checks device.active. Returns device/tenant/branch/timezone metadata; **no access token and no user identity**. | `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:67`; `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:87`; `src/main/java/com/smart/restaurant_saas/device/dto/DeviceLoginResponse.java:8` |
| Subsequent protected requests | Authorization: Bearer user JWT, for all clients. No separate device authentication filter. HTTP Basic/form login disabled; session policy stateless. | `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:47`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:32`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:42` |

No separate mobile authentication path is implemented in the inspected backend. A future mobile client's behavior is **UNVERIFIED**; no mobile implementation was supplied. The current callable authentication routes are the ones above (`src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:36`; `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:22`).

For the actual POS caller, device-secret exchange is followed by cashier password login with the cached deviceId. Subsequent requests send both `Authorization: Bearer session.accessToken` and `X-User-Id: session.user.id`. That frontend normally keeps them equal, but it does not enforce equality on the backend (`../restaurant-pos/src/apiClient.ts:137`; `../restaurant-pos/src/apiClient.ts:149`; `../restaurant-pos/src/apiClient.ts:103`).

The backend knows the authenticated cashier from the JWT, yet **ShiftController uses the header** for business identity. Open verifies that the selected user belongs to the tenant, but not that this is the authenticated user, an active user, or a user assigned to the supplied branch. Branch ownership is checked independently. Current-shift reads accept the selected cashier ID; close checks shift tenant/status but not cashier ownership, and stamps the supplied ID. A SHIFTS_OPEN holder can therefore open a shift for another tenant colleague, inspect their current summary, or close a tenant shift and misattribute the action (`src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:29`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:41`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftController.java:50`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:59`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:75`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:86`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:159`).

Order creation also selects the open shift using the header cashier ID and tenant, without comparing it to the principal. An ORDERS_CREATE holder can target a colleague's open shift when the order satisfies the other business prerequisites (`src/main/java/com/smart/restaurant_saas/order/core/OrderController.java:53`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:107`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:168`).

Device login checks device.active, but the optional deviceId check during **user** login checks neither the device secret nor device.active. It is skipped entirely when deviceId is omitted. A user with valid credentials can obtain an ordinary token without demonstrating device possession; an otherwise qualifying POS login can name an inactive device ID. Later requests carry no cryptographic device binding. Deactivating a device does not revoke an already-issued cashier token (`src/main/java/com/smart/restaurant_saas/device/DeviceService.java:74`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:108`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:119`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:33`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59`). This verifies the current mechanism relevant to O19 without claiming that an independently named `device_auth` implementation exists.

### Every configured unauthenticated surface

| Surface | Evidence | Notes |
|---|---|---|
| POST /api/auth/login | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:36` | Password authentication happens inside AuthService. |
| POST /api/devices/login | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:37` | Device secret checked inside DeviceService. |
| /swagger-ui.html and /swagger-ui/** | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:38` | Public documentation routes, any method allowed by the matcher; actual resource mappings still apply. |
| /api-docs and /api-docs/** | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:38` | Public API schema routes. |
| OPTIONS /** | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:39` | Public OPTIONS/CORS handling; this does not grant unauthenticated POST/PUT/DELETE. |

All other routes require authentication under the single configured chain (`src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:40`). Only the two POST login routes skip the JWT filter: malformed/stale Authorization headers on public documentation requests can still cause a 401 before permitAll; public reachability here means a request without that header (`src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:35`; `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:49`).

## Ranked findings — non-SAFE only

| # | Finding | Verdict | Evidence | What it lets someone do |
|---|---|---|---|---|
| 1 | Token SYS_ADMIN role survives database revocation | WEAK | `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:97`; `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:33`; `src/main/java/com/smart/restaurant_saas/auth/service/CurrentUserScopeProvider.java:24` | A former sysadmin holding an unexpired token retains permission bypass and platform/tenant-selection privileges after their database role changes. |
| 2 | Deactivated user remains authenticated and permission-eligible | BROKEN | `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59`; `src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`; `src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java:122` | A disabled employee can continue reads and writes allowed by their remaining permissions until expiry, subject to tenant and business checks. |
| 3 | Caller chooses cashier for shifts and order assignment; closing does not check cashier ownership | WEAK | `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:43`; `src/main/java/com/smart/restaurant_saas/pos/shift/ShiftService.java:86`; `src/main/java/com/smart/restaurant_saas/order/core/OrderService.java:168` | A suitably permissioned user can operate another tenant colleague's shift or attribute sales to that colleague's open shift. |
| 4 | Expense and broader audit attribution accepts unbound X-User-Id | WEAK | `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:84`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:97`; `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:159`; complete Q1 inventory | A caller with the relevant write permission can record spending, voids, and other operations under a colleague's or invented actor ID. |
| 5 | OWNER/BRANCH_MANAGER token-role checks survive role changes | WEAK | `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:23`; `src/main/java/com/smart/restaurant_saas/hr/controller/SalaryController.java:22`; `src/main/java/com/smart/restaurant_saas/hr/controller/LeaveTypeController.java:38` | A former owner or branch manager can retain role-gated HR operations until token expiry, subject to the separate live branch scope checks. |
| 6 | POS device checks are optional and do not bind the access token | WEAK | `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:108`; `src/main/java/com/smart/restaurant_saas/device/DeviceService.java:74`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:33` | A credentialed user can skip device proof or name an inactive device, and a disabled device's existing cashier token stays usable. |
| 7 | No access-token revocation; default exposure window 24 hours | WEAK | `src/main/resources/application.yml:47`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:45` | A holder of an issued token can keep presenting it until expiry without a per-token cancellation check. |
| 8 | Permission lookup and login ignore role.active | WEAK | `src/main/java/com/smart/restaurant_saas/rbac/repository/UserPermissionRepository.java:41`; `src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:182` | Disabling a role alone does not stop an active member from logging in or using their still-assigned permissions and token role gates. |
| 9 | Optional/ignored actors leave durable records without attribution | WEAK | `src/main/java/com/smart/restaurant_saas/expense/ExpenseService.java:124`; `src/main/java/com/smart/restaurant_saas/loyalty/customer/CustomerController.java:67`; `src/main/java/com/smart/restaurant_saas/inventory/core/PhysicalCountService.java:423` | A caller can omit optional actor headers to leave null audit identities, while some operations discard identity even when supplied. |
| 10 | Authentication-only current-user and role-catalog reads | WEAK | `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:27`; `src/main/java/com/smart/restaurant_saas/rbac/controller/RbacRoleController.java:18` | A deactivated user with a valid token can still read these endpoints even if all permission assignments are removed. |
| 11 | Production-path missing-permission 403 evidence incomplete | UNVERIFIED | `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:22`; `src/test/java/com/smart/restaurant_saas/expense/ExpenseControllerSecurityTest.java:186` | No bypass is established, but slice tests alone cannot establish rejection through the complete production authentication and authorization path. |
| 12 | Deployed token lifetime not inspected | UNVERIFIED | `src/main/resources/application.yml:47`; `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:23` | The actual time a retained token remains usable cannot be bounded from the checked-in default alone. |
| 13 | Required actor-header omission response not observed | UNVERIFIED | `src/main/java/com/smart/restaurant_saas/expense/ExpenseController.java:97`; `src/main/java/com/smart/restaurant_saas/common/GlobalExceptionHandler.java:85` | A caller can trigger pre-handler rejection by omitting the header; whether that is incorrectly reported as 500 is not runtime-verified. |
| 14 | Future mobile authentication behavior unavailable | UNVERIFIED | `src/main/java/com/smart/restaurant_saas/auth/controller/AuthController.java:22`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:36` | No distinct mobile bypass is established, and a planned client cannot yet be credited with enforcing actor attribution. |
