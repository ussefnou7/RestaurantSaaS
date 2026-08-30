# Tenant Isolation Audit — Phase 1 stop report

**Audit date:** 2026-08-30  
**Backend revision:** `63ff8e7` (`main`)  
**Disposition:** **STOPPED in Phase 0.** The tenant context is caller-controlled on authenticated
routes that do not invoke `CurrentTenantProvider`. Per the prompt's stop rule, the repository,
IDOR, report-SQL, collision/constraint, and SysAdmin-surface sweeps were not performed. No module
is declared clean by this report.

## Critical answer first: where the acting tenant comes from

### Conclusion

The signed JWT contains a `tenantId` claim, but the application does **not** centrally derive every
request's effective tenant from it. Controllers commonly accept `X-Tenant-Id` as a method argument
and pass that value to services. Validation against the JWT tenant happens only if the request path
also calls `CurrentTenantProvider.getCurrentTenantId()`.

That call is normally reached indirectly from
`@PreAuthorize("... hasPermission(...) ...")`: `SecurityService.hasPermission` calls
`CurrentTenantProvider` at `src/main/java/com/smart/restaurant_saas/auth/service/SecurityService.java:33-46`.
It is not a request-wide invariant.

Therefore:

- **JWT claim or header?** Both exist. `JwtService` signs `tenantId` into the token at
  `src/main/java/com/smart/restaurant_saas/auth/service/JwtService.java:29-42`, and
  `JwtAuthenticationFilter` reconstructs the principal from the token at
  `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59-67`.
  Nevertheless, tenant-facing controllers use the request header as the service tenant argument.
- **Where is the header validated?** Only in
  `src/main/java/com/smart/restaurant_saas/tenant/CurrentTenantProvider.java:23-50`, specifically
  the mismatch rejection at lines 42-47. The JWT filter does not compare the header, and
  `SecurityConfig` merely requires authentication at
  `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:35-42`.
- **Can tenant A submit `X-Tenant-Id: B` and execute in B?** **Yes.** Any authenticated tenant user
  can reach a controller method that lacks a provider-calling authorization expression. Concrete
  confirmed examples are tenant UOM creation and both purchase posting transitions (findings 1-3).
- **Why do most permission-gated methods appear protected?** For a non-SysAdmin, the usual
  `isSysAdmin() or hasPermission(...)` expression reaches `hasPermission`, which calls
  `getCurrentTenantId`; the provider then compares the header to the JWT claim. Removing or
  forgetting the annotation also removes the tenant binding. Tenant isolation is therefore
  accidentally coupled to method authorization.
- **SysAdmin behavior:** a JWT principal with `SYS_ADMIN` may select an active tenant through the
  header by design (`CurrentTenantProvider.java:27-35`). That is the intended cross-tenant case.

### POS path

`../restaurant-pos/src/apiClient.ts:92-106` sends cached device tenant and branch values as headers and
sends the cashier JWT separately. The device secret is used only at device login
(`../restaurant-pos/src/apiClient.ts:137-146`); the subsequent cashier login sends `deviceId`
(`:149-162`). The backend verifies that device against the user's tenant and branch at
`src/main/java/com/smart/restaurant_saas/auth/service/AuthService.java:108-131`, then creates a user
JWT containing the user's tenant at lines 145-151.

For `POST /api/orders`, `OrderController.java:40-53` has an `ORDERS_CREATE` permission check, so a
non-SysAdmin's forged tenant header reaches `CurrentTenantProvider` and is rejected. Thus tenant is
bound to the cashier JWT on that path, not continuously to the device secret. `X-Branch-Id` remains
a separate unbound-header problem acknowledged by D33. However, the same authenticated POS JWT can
call an affected route lacking the provider-calling guard, so the application-wide tenant answer is
still unsafe.

## Prerequisite: test-suite health

`PROMPT_SUITE_HEALTH.md` was not present in this repository or its parent workspace. The required
baseline was established directly with the repository's documented full backend command:

```text
./mvnw test
Tests run: 687, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

The sole failure is
`src/test/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionServiceTest.java:423-430`,
`recalculateRejectsNonConflictDoc`. The production guard is commented out at
`src/main/java/com/smart/restaurant_saas/inventory/orderconsumption/OrderConsumptionService.java:182-191`.
This reproduces the prerequisite warning on `main`; no additional red tests were found.

No checked-in GitHub Actions, GitLab CI, Jenkins, or Azure Pipelines definition was found. The suite
is red and there is no repository CI gate demonstrating that red blocks release. A cross-tenant
test harness is not a durable control until both conditions are corrected.

## Phase 0 boundary: table ownership

### Classification rules

- `TENANT_OWNED`: contains tenant business/security state and must be scoped to the effective
  tenant. This includes child tables whose tenant is inherited through a mandatory parent.
- `GLOBAL_SHARED`: deliberately shared reference/RBAC data. An unscoped read is expected.
- `SYSTEM`: platform coordination or tenant-directory metadata, not a restaurant-owned row set.

`TenantAwareEntity` supplies a non-null `tenant_id`
(`src/main/java/com/smart/restaurant_saas/common/TenantAwareEntity.java:10-14`). `BaseEntity` supplies
audit fields only (`BaseEntity.java:21-35`), so extending it does not make a row global.

The list below was reconciled against the migrated PostgreSQL schema at Flyway version 50 and the
entity hierarchy. It contains all 58 current base tables. The migrations also create two obsolete
tables that are not listed as current: `user_roles` is dropped by
`V14__rbac_role_scoping.sql:84`, and `order_consumption_event` is dropped by
`V20__drop_order_consumption_event.sql:3`.

| Table | Classification | Boundary evidence |
|---|---|---|
| `asset` | `TENANT_OWNED` | Non-null `tenant_id` |
| `asset_disposal` | `TENANT_OWNED` | Non-null `tenant_id` |
| `asset_line` | `TENANT_OWNED` | Non-null `tenant_id` |
| `asset_maintenance` | `TENANT_OWNED` | Non-null `tenant_id` |
| `branches` | `TENANT_OWNED` | Non-null `tenant_id` |
| `customer` | `TENANT_OWNED` | Non-null `tenant_id`; phone uniqueness is tenant-scoped |
| `device` | `TENANT_OWNED` | Non-null `tenant_id` |
| `document_history` | `TENANT_OWNED` | Non-null `tenant_id` |
| `employee_leave_balances` | `TENANT_OWNED` | Non-null `tenant_id` |
| `employee_salaries` | `TENANT_OWNED` | Non-null `tenant_id` |
| `employee_salary_adjustments` | `TENANT_OWNED` | Non-null `tenant_id` |
| `flyway_schema_history` | `SYSTEM` | Flyway migration bookkeeping |
| `hr_employees` | `TENANT_OWNED` | Non-null `tenant_id` |
| `hr_leave_request` | `TENANT_OWNED` | Non-null `tenant_id` |
| `hr_leave_type` | `TENANT_OWNED` | Non-null `tenant_id` |
| `incoming_order_request` | `TENANT_OWNED` | Non-null `tenant_id` |
| `inventory_transaction` | `TENANT_OWNED` | Non-null `tenant_id` |
| `inventory_transfer` | `TENANT_OWNED` | Non-null `tenant_id` |
| `inventory_transfer_line` | `TENANT_OWNED` | Non-null `tenant_id` |
| `invoice_sequence` | `TENANT_OWNED` | Counter scope includes non-null `tenant_id` |
| `jobs` | `TENANT_OWNED` | Non-null `tenant_id` |
| `material` | `TENANT_OWNED` | Non-null `tenant_id` |
| `material_catalog` | `GLOBAL_SHARED` | No tenant column (`V6__inventory_master_data.sql:25-39`) |
| `material_category` | `TENANT_OWNED` | **Mixed table:** null is global, non-null is tenant custom (`V6:41-53`) |
| `menu_category` | `TENANT_OWNED` | Non-null `tenant_id` |
| `order_consumption` | `TENANT_OWNED` | Non-null `tenant_id` |
| `order_consumption_line` | `TENANT_OWNED` | Tenant inherited through mandatory `doc_id` parent |
| `order_consumption_material` | `TENANT_OWNED` | Tenant inherited through mandatory `doc_id` parent |
| `order_line` | `TENANT_OWNED` | Non-null `tenant_id` |
| `orders` | `TENANT_OWNED` | Non-null `tenant_id` |
| `permissions` | `GLOBAL_SHARED` | Seeded global permission catalog; no tenant column |
| `physical_count` | `TENANT_OWNED` | Non-null `tenant_id` |
| `physical_count_code_sequence` | `TENANT_OWNED` | Counter scope begins with non-null `tenant_id` (`V38:2-8`) |
| `physical_count_line` | `TENANT_OWNED` | Non-null `tenant_id` |
| `product` | `TENANT_OWNED` | Non-null `tenant_id` |
| `product_add_on` | `TENANT_OWNED` | Non-null `tenant_id` |
| `purchase_invoice` | `TENANT_OWNED` | Non-null `tenant_id` |
| `purchase_invoice_line` | `TENANT_OWNED` | Tenant inherited through mandatory `purchase_invoice_id` parent |
| `purchase_return` | `TENANT_OWNED` | Non-null `tenant_id` |
| `purchase_return_line` | `TENANT_OWNED` | Tenant inherited through mandatory `purchase_return_id` parent |
| `recipe` | `TENANT_OWNED` | Non-null `tenant_id` |
| `recipe_item` | `TENANT_OWNED` | Non-null `tenant_id` |
| `restaurant_table` | `TENANT_OWNED` | Non-null `tenant_id` |
| `role_permissions` | `GLOBAL_SHARED` | Mapping between global role and permission catalogs; no tenant column |
| `roles` | `GLOBAL_SHARED` | Current role codes/data are global; nullable `tenant_id` is unused by repository reads |
| `shedlock` | `SYSTEM` | Scheduler coordination table (`V19:8-14`) |
| `shift` | `TENANT_OWNED` | Non-null `tenant_id` |
| `stock_balance` | `TENANT_OWNED` | Non-null `tenant_id` |
| `stock_batch` | `TENANT_OWNED` | Non-null `tenant_id` |
| `supplier` | `TENANT_OWNED` | Non-null `tenant_id` |
| `table_section` | `TENANT_OWNED` | Non-null `tenant_id` |
| `tenants` | `SYSTEM` | Platform tenant directory; SysAdmin-managed |
| `uom` | `TENANT_OWNED` | **Mixed table:** null is global, non-null is tenant custom (`V6:73-90`) |
| `user_permissions` | `TENANT_OWNED` | Non-null `tenant_id` |
| `users` | `TENANT_OWNED` | Non-null `tenant_id` (system users use the system tenant) |
| `warehouse` | `TENANT_OWNED` | Non-null `tenant_id` |
| `waste_document` | `TENANT_OWNED` | Non-null `tenant_id` |
| `waste_line` | `TENANT_OWNED` | Tenant inherited through mandatory `waste_document_id` parent |

### Correction to the broad “global reference data” description

`material_catalog` is wholly global. `uom` and `material_category` are not wholly global: both
schemas allow nullable `tenant_id`, and tenant-specific UOMs already exist in the migrated local
database. They are mixed global-plus-tenant tables. Their safe tenant-facing predicate is
`tenant_id IS NULL OR tenant_id = :effectiveTenantId`; an unqualified query can leak tenant custom
rows. For this audit they are therefore conservatively classified `TENANT_OWNED`.

## Findings

| # | Location (`path:line`) | Surface | What leaks | Exploit sketch | Severity |
|---|---|---|---|---|---|
| 1 | `src/main/java/com/smart/restaurant_saas/auth/security/JwtAuthenticationFilter.java:59-74`; `src/main/java/com/smart/restaurant_saas/config/SecurityConfig.java:35-42`; `src/main/java/com/smart/restaurant_saas/inventory/uom/UomController.java:99-111` | Tenant context / tenant UOM create | The JWT filter authenticates but never binds or rejects `X-Tenant-Id`; this authenticated route has no provider-calling method guard and creates a row using the header tenant. | Authenticated as tenant A, send `POST /api/uom` with `X-Tenant-Id: B` and a valid custom-UOM body referencing a global base UOM. `UomService.createForTenant` writes the new row with `tenant_id = B` (`inventory/core/UomService.java:118-139`). | `CRITICAL` |
| 2 | `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseInvoiceController.java:174-185`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseInvoiceService.java:213-248` | Purchase invoice posting | Cross-tenant stock, batches, balances, prices, ledger rows, and invoice state can be mutated. The service's ownership query uses the attacker-supplied header tenant (`:663-667`). | Authenticated as tenant A, send `POST /api/inventory/purchase-invoices/{B-complete-invoice-id}/post` with `X-Tenant-Id: B`. The unguarded transition loads B's invoice and records inbound inventory under B. | `CRITICAL` |
| 3 | `src/main/java/com/smart/restaurant_saas/inventory/purchase/PurchaseReturnController.java:168-180`; `src/main/java/com/smart/restaurant_saas/inventory/core/PurchaseReturnService.java:243-254,308-329` | Purchase return posting | Cross-tenant stock, source batches, balances, ledger rows, prices, and return state can be mutated. The ownership query again trusts the supplied tenant (`:668-672`). | Authenticated as tenant A, send `POST /api/inventory/purchase-returns/{B-complete-return-id}/post` with `X-Tenant-Id: B`. The unguarded transition posts stock-out movements and depletes B's source batch. | `CRITICAL` |

These are confirmed code paths, not suspicions. No live exploit was executed and no data was
changed.

## Explicitly unaudited because of the stop rule

| Requested Phase 1 area | Status |
|---|---|
| Repository method matrix for every tenant-owned entity | **NOT AUDITED** |
| Endpoint IDOR pass, including referenced IDs in bodies | **NOT AUDITED** |
| Six inventory reports, order reports, and asset reports | **NOT AUDITED** |
| Customer phone resolution, sequences, idempotency, Flyway FK/unique-constraint pass | **NOT AUDITED** |
| Complete enumeration and permission verification of legitimate SysAdmin cross-tenant surfaces | **NOT AUDITED** |
| Per-module clean declarations | **NONE — audit stopped before modules could be cleared** |

The known SysAdmin surface candidates named in the task—global catalog/UOM management, tenant
administration, and tenant-user management—must be enumerated and checked after the tenant-context
fix. Their presence here is not a clean verdict.

## Ranked remediation

1. **P0 — Bind the effective tenant centrally before controller dispatch.** For a non-SysAdmin,
   derive it from the signed `CurrentUserPrincipal.tenantId`; either ignore `X-Tenant-Id` or require
   an exact match and reject before any controller. For SysAdmin, permit an explicit active-tenant
   selection only on intended admin/tenant-scoped routes. No controller should receive an
   independently trusted tenant value.
2. **P0 — Contain the confirmed write paths.** Add the intended permission gates to all tenant UOM
   methods and both purchase `post` transitions, while treating that as defense in depth rather
   than the tenant-binding fix. Add regression tests using tenant A's real JWT plus tenant B's
   header for all three paths.
3. **P0 — Restore the test gate.** Re-enable/fix the commented order-consumption state guard, make
   the full 687-test Maven suite green, and add mandatory CI that blocks merge/release on failure.
4. **P1 — Resume this audit from Phase 1.** The central defect does not prove individual queries
   safe. Complete the repository, IDOR, report/native-SQL, constraint, and SysAdmin-surface matrices
   after containment.
5. **P1 — Add endpoint-wide two-tenant negative tests.** Every tenant-scoped endpoint should reject
   tenant A credentials used with tenant B identifiers, referenced IDs, and tenant headers with
   403/404 and no mutation.
6. **P1 — Add database-enforced isolation.** Roll out PostgreSQL RLS as described below so a later
   missed repository predicate or native query is fail-closed.

## Phase 2 — durable isolation options

None of the three options can repair this report's primary defect if its tenant context is itself
initialized from the untrusted header. Central principal-to-tenant binding is a prerequisite for
all of them.

| Option | Coverage in this codebase | Important misses / plumbing | Cost |
|---|---|---|---|
| Hibernate `@Filter` / discriminator `@TenantId` | Would cover ORM entity loads, derived repository methods, inherited `findById`/`findAll`, JPQL, Criteria, and Specifications once every tenant entity participates and the session tenant comes from the JWT principal. | Does not protect native SQL. This code currently has 7 `nativeQuery = true` declarations and 4 `JdbcTemplate` consumers. Five tenant-owned child tables have no local tenant column and need parent-aware treatment. Mixed global-plus-tenant UOM/category visibility and intentional SysAdmin cross-tenant sessions also require custom handling. | **Medium-high**: entity/session plumbing plus query inventory and exceptions; still leaves a second native-SQL control problem. |
| PostgreSQL Row-Level Security | Enforces reads and writes in the database, including native repository SQL, JDBC, forgotten `findById`, and new callers. It directly addresses the “weakest query wins” failure model. | Set a transaction-local tenant value from the authenticated principal on every application transaction; fail closed if absent; ensure pooled connections cannot retain scope. Add policies to all tenant-owned tables. The five parent-owned child tables need either backfilled non-null `tenant_id` or parent-`EXISTS` policies. Mixed global rows need explicit read policies. SysAdmin work needs a narrow, audited bypass/context strategy. Flyway must use a migration owner/role distinct from the restricted app role; use `FORCE ROW LEVEL SECURITY` or ensure the app does not own tables. | **High**: staged schema/policy rollout, transaction integration, admin/migration separation, query-plan testing, and operational validation. |
| Cross-tenant endpoint test harness | Catches header, path-ID, query-ID, and payload-reference regressions and is the fastest way to exercise the public contract. | Detection only; it cannot protect an untested new endpoint or direct SQL. It currently has no release value because the suite already fails and no checked-in CI gate blocks on that failure. | **Low-medium** once two-tenant fixtures/auth helpers exist; ongoing endpoint-matrix maintenance. |

### Recommendation

After the immediate JWT-derived tenant binding, adopt **PostgreSQL RLS** as the durable enforcement
layer. It is the only option of the three that covers both ORM and the native/JDBC paths that matter
most for reports and operational updates. Migration cost is high: 51 tenant-owned tables
need policies, including five parent-owned child tables, plus transaction-scoped connection context,
Flyway/application role separation, SysAdmin bypass design, indexes/query-plan checks, and staged
verification.

Build the cross-tenant test harness in parallel with that rollout, but do not call it a control until
the existing suite is green and CI blocks on it. Hibernate filtering can be useful defense in depth,
but it is not the recommended primary boundary because its native-SQL gap preserves the exact
single-missed-query failure mode this audit is intended to eliminate.
