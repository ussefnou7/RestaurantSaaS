# DECISIONS

> Two strictly separated sections. **DECIDED** items are ground truth: do not reopen them,
> and any code that contradicts one is a bug to be fixed (not a reason to change the decision).
> **OPEN** items are undecided — never present them as settled or build irreversible code on a
> guessed answer.
>
> Each DECIDED item was verified against the real code in this pass; the verification note and
> `file:line` anchor follow it. Status legend: ✅ holds · ⚠️ holds with a wording nuance ·
> ❌ code currently violates it.

---

## DECIDED (ground truth)

### D1 — `StockBalance.quantity` is the signed ledger delta only, not batch-derived; negative is allowed. ✅
Quantity moves by the ledger’s signed delta (positive on IN, negative on OUT) and is permitted
to go negative on a FIFO shortfall; it is **not** re-derived from batches.
`StockBalanceService.applyMovement` — `inventory/core/StockBalanceService.java:240-244`
(and the class javadoc, lines 40-47).

### D2 — Average cost is derived from OPEN batches only (`remainingQuantity > 0`); no running incremental formula. ✅
`averageCost = Σ(remainingQty × unitCost) / Σ(remainingQty)` over OPEN batches, recomputed
after every batch mutation; there is no incremental `(oldQty·oldAvg + Δ·cost)/newQty` formula
and no “cost-bearing transaction” classification.
`StockBalanceService.deriveAverageFromOpenBatches` — `inventory/core/StockBalanceService.java:290-304`;
`StockBatchRepository.sumOpenBatchTotals`.
> Note: the legacy `docs/PROJECT_SKILL.md` still documents the old running weighted-average
> formula. That doc is stale; **this decision + the current code are authoritative.**

### D3 — Entered→stock UOM conversion for a ledger entry happens once, inside `InventoryLedgerService.record()`; callers never pre-convert. ⚠️
Callers build a `LedgerCommand` with the **raw entered** quantity/UOM/unit-cost; `record()`
performs the single `convertToStockUom(...)` — `inventory/core/InventoryLedgerService.java:80`.
> Wording nuance: `UomConversionService` **is** used elsewhere (`StockBalanceService`,
> `StockBatchService`, `PurchaseReturnService`, `WasteService`) for *downstream* display-UOM
> math and guard checks — that is legitimate and not “caller-side pre-conversion.” Read this
> invariant as: **the ledger entry’s entered→stock conversion is done exactly once in
> `record()`; no caller pre-converts the entered qty/cost it passes in.**

### D4 — `inventory_transaction` is written only via `InventoryLedgerService`. ✅
Sole `transactionRepo.save(...)` is `InventoryLedgerService.java:260` (inside
`saveWithIdempotencyGuard`). No other class writes the table.

### D5 — `stock_balance` is written only via `StockBalanceService`. ⚠️ (holds for qty/avg; violated for denormalized fields)
The ledger-owned fields — `quantity` and `averageCost` — are written **only** by
`StockBalanceService` (`applyMovement`, `recalculateFromOpenBatches`). ✅
However the **denormalized display fields** are `saveAll`’d directly by operation services:
- `lastPurchasePrice` / `lastPurchaseDate` — `PurchaseInvoiceService.java:250`,
  `PurchaseReturnService.java:364`
- `lastCountDate` / `lastCountQuantity` — `PhysicalCountService.java:387`
- (`StockBalanceAverageCostBackfill.java:83` — one-off backfill utility)
> Recommended precise wording: **`stock_balance.quantity` and `averageCost` are written only via
> `StockBalanceService`; the denormalized last-purchase / last-count fields may be written by the
> owning operation service.** With that refinement the code holds. As literally worded (“only via
> StockBalanceService”), the three sites above are violations — see [REVIEW](REVIEW.md) → invariants.

### D6 — Delete is allowed only when status is DRAFT **and** there are no ledger transactions (dual-check, not status alone). ✅
`PurchaseInvoiceService.delete` — status==DRAFT (`:340`) **and** `!existsByReference(...)`
(`:346`); `PurchaseReturnService.delete` — same dual-check (`:446`, `:452`).

### D7 — Waste has no Unpost and no reversal of any kind. ✅
`WasteService` exposes no `unpost`/`reverse`; once POSTED a waste document is terminal
(`cancel` is rejected from POSTED, allowed only from DRAFT/COMPLETE; `uncomplete` only moves
COMPLETE→DRAFT, i.e. pre-posting). `inventory/core/WasteService.java:279-294`.

### D8 — Purchase Invoice Unpost checks the return-existence guard **first**, then the batch-consumption guard. ✅
`PurchaseInvoiceService.unpost` — `assertNoPurchaseReturns(...)` (`:274`) runs before
`assertNoConsumedBatches(...)` (`:280`), so “unpost the return first” wins over the vaguer
batch message. A third independent `assertBatchesReversible(...)` runs immediately before the
batches are hard-deleted (`:288`).

### D9 — Purchase Return Unpost needs no batch-consumption guard (it is additive). ✅
`PurchaseReturnService.unpost` guards only `assertOriginalInvoiceStillPosted(...)` (`:386`),
then reverses the ledger and `restoreSourceBatch(...)` (additive, capped at the batch’s
original quantity). No consumption guard. `inventory/core/PurchaseReturnService.java:376-418`.

### D10 — Batches are ordered by creation order (ascending id), not `movementDate`. ✅
FIFO consumption and batch listing both order by **id asc**:
`StockBatchService.consumeFifo` → `findByStockBalanceIdAndStatusOrderByIdAsc`
(`inventory/core/StockBatchService.java:178`); `StockBalanceService.findBatchesForBalance`
→ `findByStockBalanceIdOrderByIdAsc` (`:109`). `StockBatch` has a `movementDate` field, but it
is **not** used for ordering.

### D11 — A FIFO shortfall is priced at the current (pre-movement) average cost; no retroactive COGS correction. ✅
When requested quantity exceeds total open-batch remaining, the unmatched remainder is valued
at `balance.getAverageCost()` (read before `applyMovement` re-derives the average).
`StockBatchService.consumeFifo` — `inventory/core/StockBatchService.java:206-214`. Nothing
retroactively corrects prior COGS.

### D12 — The backend emits `errorCode` + `params`; the `message` field is logs-only and never shown to the user. ❌ (partial — migration incomplete)
The structured hierarchy is correct: `AppException(errorCode, debugMessage, params)` with
status derived from the code (`common/AppException.java`, `common/ErrorCode.java`); the FE
`translateApiError` renders from `errorCode` + `params` and treats `message` as “logs only,
never rendered” (`restaurant-saas-web/src/utils/errors.ts`). **But 7 inventory services still
throw legacy `ApiException(HttpStatus, message)` / deprecated `BusinessException(String)` with
no `errorCode`/`params`** — a user-facing message with nothing structured to translate:
`WasteService`, `UomService`, `InvoiceSequenceService`, `MaterialService`,
`MaterialCategoryService`, `SupplierService`, `WarehouseService`. Tracked in
[ROADMAP](ROADMAP.md) §4; listed as violations in [REVIEW](REVIEW.md).

### D13 — No premature abstraction (§1.4). ✅ (principle — no violations found)
The code consistently prefers the concrete: hand-written mappers (no MapStruct), one combined
request DTO per resource (no split Create*/Update* DTOs), no separate UOM-conversion table
(`factorToBase` + `baseUom` self-ref only), and the single documented `jsonb` exception
(`waste_document` warnings) explicitly flagged as *not a precedent*. Reviewers should block new
abstractions introduced “for the future” without a second concrete caller.

### D14 — Menu module: every `Product` requires a `Recipe` (BOM); no BOM-less products.
Every `Product`, regardless of complexity, must go through a `Recipe`, even single-ingredient
products (e.g. bottled water, canned soda). No special-case "direct material sale" path — this
keeps consumption logic, costing (COGS), and future extensibility (e.g. adding a cup for
takeaway) unified through one mechanism. Rejected alternative: skip Recipe for simple products
(would force branching logic in the consumption job).

### D15 — Menu module: no `Menu` entity in V1; tenant has a single implicit menu.
`MenuCategory` links directly to the tenant with no `Menu` layer on top. Revisit only when
multi-menu (e.g. breakfast vs dinner) becomes an actual requirement.

### D16 — Menu module: `Product`/`MenuCategory` are tenant-level only in V1; no `branch_id`.
Per-branch menu customization (different products per branch, different pricing per branch, or
entirely separate branch menus) is a real, expected future need — not hypothetical — but is
explicitly deferred. Chosen approach when it's built: an additive availability layer (e.g.
`product_branch_availability` table) on top of the existing tenant-level entities, not a
structural change to `Product`/`MenuCategory` themselves.

### D17 — Menu module: per-channel visibility (`isPOS`/`isDelivery`) deferred from V1.
All products are available across all channels uniformly for now. Chosen approach when built:
boolean flags directly on `Product` (not a separate table), defaulting to `true`.

### D18 — Menu module: `Recipe` is versioned and immutable; `RecipeItem` belongs to a `Recipe`, not directly to a `Product`.
A `Product` has a history of `Recipe` versions; only one `Recipe` per product may be
`isActive = true` at a time, enforced at the service layer (not a DB partial unique index).
Once created, a `Recipe` is never edited — changing a recipe means deactivating the current one
and creating a new active one. This exists specifically so `OrderLine.recipeId` can freeze a
reference at order-completion time that always resolves to the exact ingredient list that was
true at that moment, regardless of later recipe edits.

### D19 — Order module: the system never tracks the internal kitchen/cashier status cycle.
The system does not track Ordered → In Progress → Done. That cycle (and, internally,
kitchen-display/cashier communication) is fully owned by the POS. We only ever receive an order
in a final state: `status: COMPLETE | CANCELLED` (renamed from "PAID" to stay consistent with
the DRAFT/COMPLETE/POSTED lifecycle used elsewhere). All kitchen-performance metrics (time from
order to done, etc.) are the POS's own responsibility — out of scope for us entirely.

### D20 — Order module: cancellation carries a POS-supplied `cancellationStage`, never inferred.
Populated only when `status = CANCELLED`, sent as-is by the POS:
`BEFORE_KITCHEN | IN_KITCHEN_COOKED | IN_KITCHEN_NOT_COOKED | AFTER_DONE`. No separate audit
field for "last kitchen stage before cancel" — rejected as unnecessary complexity;
`cancellationStage` alone is sufficient since it's already the POS's authoritative decision.
Maps directly to consumption behavior: `COMPLETE` → sale consumption. `IN_KITCHEN_COOKED` /
`AFTER_DONE` → waste consumption. `BEFORE_KITCHEN` / `IN_KITCHEN_NOT_COOKED` → no consumption,
order excluded from `OrderConsumptionDoc` entirely.

### D21 — Order module: `OrderLine.recipeId` and `unitPrice` are frozen at "Complete Order on System" time.
Not at consumption time. `recipeId` freezing is what makes the versioned/immutable Recipe model
(D18) actually work for consumption accuracy. `unitPrice` is frozen at sale time so historical
invoices stay correct even if the product's selling price changes later.
> **Error code note**: order creation must reject a product with no active recipe using a
> **dedicated** error code (`PRODUCT_HAS_NO_ACTIVE_RECIPE`), not the Menu module's
> `RECIPE_NOT_FOUND`. The two situations need different user-facing treatment: in the Menu
> module, "no active recipe yet" is a normal, low-key empty state for a newly created product;
> in the Order module, it's a hard blocker preventing order creation. Sharing one error code
> would force one generic message to serve both an informational empty-state and an urgent
> validation failure — reject that; keep them as two separate codes even though the underlying
> condition (`Recipe` lookup returns none) is the same.

### D22 — Order module: dine-in + linked takeaway ("same customer, two invoices") is deferred.
Real-world need confirmed (e.g. service-charge differs between dine-in and takeaway, so they
can't share one invoice), but deferred as a Backlog item. V1: every order is fully independent —
its own `Order`, its own invoice, no linking mechanism between orders.

### D23 — Order module: `orderSource` distinguishes origin; every `Order` row is ultimately POS-completed.
`orderSource: POS | ONLINE | AGGREGATOR`, plus a nullable `aggregatorName` populated only when
`orderSource = AGGREGATOR`. Direction of flow differs by source: POS-sourced orders originate at
the POS itself (we only receive the final result). ONLINE and AGGREGATOR orders originate
outside the POS — received first as an `IncomingOrderRequest`, forwarded to the POS (the
"Confirmation Step"), and the POS runs its full internal cycle before handing back a final order
— exactly like a POS-native order. This keeps `Order` pure and uniform regardless of origin.

### D24 — Order module: `IncomingOrderRequest` is a separate table with a one-directional link to `Order`.
Used only for online/aggregator intake before the POS has produced a final order. Keeps `Order`
uniform (no in-progress/intermediate rows) and avoids one row meaning two different things. The
link is one-directional, `IncomingOrderRequest → Order`, via a nullable `completedOrderId` FK —
`Order` has zero awareness of `IncomingOrderRequest`. `externalReferenceId` on
`IncomingOrderRequest` is for a *different* purpose (matching the aggregator's own order
numbering for future settlement/reconciliation) and is *not* used for the internal POS-linking
mechanism. Internal linking mechanism: we generate our own reference (`IncomingOrderRequest.id`),
send it to the POS when forwarding (`SENT_TO_POS`), and require the POS integration to echo it
back with the final completed order — see O6.

### D25 — Order module: `paymentMethod` on `Order`; aggregator orders are treated as instantly settled for now.
`paymentMethod: CASH | CARD | WALLET | AGGREGATOR`. Aggregator orders are `status = COMPLETE`,
`paymentMethod = AGGREGATOR` immediately, even though real-world payout is batched/delayed
(e.g. weekly). The gap between "order complete" and "aggregator actually pays out" is deferred
to a future separate accounting document (Accounts Receivable per aggregator) — not modeled in
the Order module itself.

### D26 — Order module: table management is entirely out of scope; `Order.tableNo` is a plain field.
Same principle as D19: table status/reservations/merging/real-time table map is a POS-native
operational concern, not something the system manages. `Order` just carries a simple `tableNo`
(nullable string/number, populated only when `orderType = DINE_IN`), sent as-is by the POS. No
`RestaurantTable` entity, no table status, no table-level business logic on our side.

### D27 — Order module: branch scoping — `Order.branchId` is direct; `OrderLine` inherits it; `IncomingOrderRequest` differs by source.
`Order` carries `branchId` directly (in addition to inherited `tenantId`) — this determines
which warehouse the order's consumption is drawn from. `OrderLine` has no own `branchId`, it
inherits from its parent `Order`. For `IncomingOrderRequest`: **Online** (client has an
integrated online store/POS) — branch is known upfront, `branchId` populated at intake.
**Aggregator** — not yet confirmed how their API communicates branch selection (see O7);
`branchId` may need to stay nullable at intake for this source until resolved.

### D28 — Order Consumption Doc: batching, locking, and the Doc/Line shape.
One `OrderConsumptionDoc` (header) per Scheduler run, per tenant. When the Scheduler picks up a
`PENDING` doc, it locks it (`status = IN_PROGRESS`) so no new orders attach mid-processing; any
order arriving during processing goes into a new/next `PENDING` doc, never one that's
`IN_PROGRESS`.
```
OrderConsumptionDoc
├── id
├── tenantId
├── status: PENDING | IN_PROGRESS | COMPLETED | CONFLICT
├── errorDetails (JSON array, nullable) — only populated on CONFLICT,
│     one entry per failed material:
│     [{ materialId, materialName, exceptionClass, message }]
├── processedAt
└── ...

OrderConsumptionDocLine
├── id
├── docId
├── orderLineId (FK -> OrderLine)
├── isConsumed (boolean, default false)
└── ...
```
No `errorDetails` on the line level — errors are recorded once on the Doc header (D30). No
`totalSuccess`/partial-success tracking at the line level — status is binary at the Doc level.

### D29 — Order Consumption Doc: 3-step, DB-side aggregation algorithm (performance).
1. **DB-side aggregation**: `SELECT recipe_id, SUM(order_qty) FROM order_consumption_doc_line
   WHERE doc_id = :docId GROUP BY recipe_id` — one row per distinct recipe actually sold in this
   run (bounded by distinct recipes, not order count), instead of loading every line into
   application memory.
2. **In-memory resolve**: fetch the distinct `Recipe`s from step 1 in one `IN (...)` query, loop
   over the small recipe-totals list (not the lines) to produce `materialId -> totalQty`.
3. **Per-material consumption**: loop over the aggregated materials (bounded by distinct
   materials touched, not order count), each wrapped in its own try/catch — FIFO batch
   consumption, `StockBalance` update, and `InventoryTransaction` recording happen here.

### D30 — Order Consumption Doc: failure handling is "full conflict", not partial.
`Material` is non-deletable and `Recipe` is immutable with a frozen `recipeId` per `OrderLine` —
so failures at this stage are expected to be systemic/technical (DB timeout, deadlock,
constraint violation, unexpected negative-balance edge case), not missing-reference data issues.
Because of this, partial-success tracking per line isn't worth the added complexity. If **any**
material fails to consume: the whole Doc goes to `CONFLICT`, **all** lines stay
`isConsumed = false` (no partial success), and `errorDetails` on the Doc header lists every
material that failed and why. If all materials consume successfully: Doc → `COMPLETED`, all
lines → `isConsumed = true` in one bulk update.

### D31 — Order Consumption Doc: retry is a full re-run, not selective, and is a deliberate operator action.
The `errorDetails` array tells the operator exactly which material(s) failed and why (e.g.
negative balance) — the expected flow is: inspect the error, fix the underlying cause (e.g.
enter the missing purchase invoice), *then* retry. Retry re-runs the full D29 algorithm from
scratch on the same Doc — no partial/selective re-processing of specific lines, consistent with
D30.

### D32 — Real-time stock display formula (reconfirmed across Inventory and Orders).
`Displayed available qty = Current StockBalance − SUM(qty in all PENDING/IN_PROGRESS
OrderConsumptionDoc lines)`, computed on the fly. `StockBalance` itself is only updated when a
Doc reaches `COMPLETED`.

### D33 — Device auth model: POS devices authenticate separately from cashier users via a one-time secret exchange.
A `Device` (tenant-owned, `device/` package) represents a physical POS terminal and is tied to
exactly **one branch** at creation (`Device.branch`, `@ManyToOne(optional = false)`). This is
deliberately decoupled from `User`/`Employee` — branch identity belongs to the **device**, not
the cashier logging into it, so the same cashier can work any device/branch without any
User-side branch field, and no `Employee.branchId` duplication is introduced.

**Secret handling**: on `POST /api/devices`, a high-entropy random secret is generated and
returned to the caller **exactly once** in the create response; only its SHA-256 hash is
persisted (`secretKeyHash`, unique-indexed) — deterministic hashing is accepted here (unlike
BCrypt for user passwords) because the secret is a generated high-entropy token, not a
guessable password.

**Login flow (MVP, not yet cryptographically bound)**: the device calls `POST
/api/devices/login` (no JWT — the secret itself is the credential) **once**, gets back `{
branchId, tenantId }`, and caches `branchId` locally. Every subsequent order-creation request
sends the cached `branchId` as a plain `X-Branch-Id` header — **not re-validated against the
secret per request**. This is an accepted MVP trade-off (see note below), not a full signed
device-token design.
> Trade-off explicitly accepted: `X-Branch-Id` is trusted like `X-Tenant-Id` already is,
> without per-request cryptographic proof. Upgrading to a signed device JWT (branchId as a
> verified claim) is deferred until a real production POS integration needs it — tracked as a
> future hardening item, not blocking Orders module work.

**Warehouse resolution**: `warehouseId` is never sent by the client — it's resolved
server-side from `branchId` at order-creation time (one warehouse per branch currently, no DB
constraint enforcing it yet — see Roadmap follow-up). Zero or multiple warehouses for a branch
must fail loudly with a dedicated error code, never silently pick one.

### D33 — RBAC: `UserRole` removed; `User` holds `roleId` and `branchId` directly.
Confirmed 1:1 user-to-role in V1 (no evidence of a real multi-role need), so the join table
was unnecessary indirection. `User.roleId` (FK, NOT NULL) and `User.branchId` (FK, NULLABLE)
replace it. `user_roles` table dropped via `V14__rbac_role_scoping.sql` (post-squash numbering).

### D34 — RBAC: `Role` is a global entity (`BaseEntity`, not `TenantAwareEntity`).
Same pattern as `Uom`/`MaterialCategory`. Tenants cannot create custom roles in V1 — only
the sysadmin panel can create/edit `Role` and `RolePermission`. All write endpoints for
`Role`/`RolePermission` live exclusively in the panel controllers, gated by
`@securityService.isSysAdmin()`. The tenant-facing RBAC controller exposes only
`GET /api/rbac/roles` (read-only, for the user-creation dropdown) — no write route exists
there at all, not even permission-gated.
> Deferred (not built): tenant-created custom roles. See O9.

### D35 — RBAC: `Role.isBranchScoped` gates whether `User.branchId` is required.
`Role` has `isBranchScoped: boolean` (default false), set only via the sysadmin panel.
Validated at user create/update: if the selected role has `isBranchScoped = true`,
`branchId` is required (`RbacErrorCode.BRANCH_REQUIRED_FOR_ROLE`); if false, `branchId`
must be null (`RbacErrorCode.BRANCH_NOT_ALLOWED_FOR_ROLE`). Roles are shared across
branches (global), but a given user's assignment to a branch-scoped role is tied to one
specific branch.

### D36 — RBAC: `UserPermission` is a materialized snapshot, not a live composition with `RolePermission`.
At user creation, the new user's `RolePermission` set (for their assigned role) is copied
into `UserPermission`. From then on, `UserPermission` is the sole source of truth checked
at runtime (`hasPermission()` queries `UserPermission` directly, live, on every request —
no JWT-embedded permission cache exists). Editing a user's permissions via the FE checklist
is a hard-delete-all + bulk-insert of the new full list — not a merge, not a grant/deny
overlay. No `type: GRANT|DENY` column exists or is needed, since there is no live
role↔user composition to override.
> Consequence (accepted, confirmed): editing a role's default permissions from the sysadmin
> panel does NOT retroactively affect already-created users. Each user's permissions are
> independent once created. `POST /api/rbac/users/{id}/permissions/reset-to-role-defaults`
> (D37) is the explicit, manual mechanism to re-sync a user back to current role defaults.

### D37 — RBAC: explicit reset-to-role-defaults endpoint.
`POST /api/rbac/users/{id}/permissions/reset-to-role-defaults` re-reads current
`RolePermission` rows for the user's role and performs the same hard-delete + bulk-insert
as the checklist edit, sourcing from the role instead of the request body. Exists because
of D36's snapshot behavior — this is the only way to pull a drifted user back in sync.

### D38 — RBAC: default roles and their default permissions are seeded, not left empty.
A tenant's first users (e.g. the initial Cashier) must be usable immediately after signup
without requiring sysadmin panel intervention first. Seeded via dedicated, standalone
migration files (not squashed into module migrations): a permissions seed script, a
default role→permission mapping seed script, and a sysadmin user seed script (password
hash preserved byte-for-byte across any migration squash — never regenerated). The seed is
a baseline only — the sysadmin can still edit `RolePermission` afterward via the panel;
seeding does not lock the defaults.

### D40 — POS device login: gated by existing `SHIFTS_OPEN` permission + device/user branch match.
Login request accepts an optional `deviceId` (nullable — populated only by the POS client;
web client login is completely unaffected). When `deviceId` is present: (1) the
authenticating user must hold the existing `SHIFTS_OPEN` permission — used as a proxy for
"qualified to operate a POS device," not as an actual shift-open action, and deliberately
reuses an existing permission rather than adding a new one; (2) `Device` (looked up by
`deviceId`, never trusted from a raw client-supplied `branchId`) must have a `branch` that
matches `user.branchId` exactly, including the case where `user.branchId` is null (e.g.
Owner/Accountant — non-branch-scoped roles are rejected by the same mismatch check, no
special-case exemption needed). Failing either check rejects the login before JWT issuance,
with structured `AuthErrorCode` (`DEVICE_NOT_FOUND` / `DEVICE_BRANCH_MISMATCH` / permission
failure). When `deviceId` is absent, login proceeds exactly as before with zero new checks.

### D41 — Device-side warehouse resolution for order creation (complements D33/D35/D40, does not replace them).
A `Device` (tenant-owned, `device/` package) represents a physical POS terminal and is tied to
exactly **one branch** at creation (`Device.branch`, `@ManyToOne(optional = false)`). This is
a separate concern from the RBAC `User.branchId`/`Role.isBranchScoped` model (D33/D35) and the
login-time device/user branch-match guard (D40) — those govern **who is allowed to log in on
which device**; this decision governs **which warehouse an order's consumption is drawn from**
once that login has already succeeded.

**Secret handling**: on `POST /api/devices`, a high-entropy random secret is generated and
returned to the caller **exactly once** in the create response; only its SHA-256 hash is
persisted (`secretKeyHash`, unique-indexed) — deterministic hashing is accepted here (unlike
BCrypt for user passwords) because the secret is a generated high-entropy token, not a
guessable password.

**Order-time branch/warehouse resolution (MVP, not yet cryptographically bound)**: the device
calls `POST /api/devices/login` (no JWT — the secret itself is the credential) **once**, gets
back `{ branchId, tenantId }`, and caches `branchId` locally. Every subsequent order-creation
request (`POST /api/orders`) sends the cached `branchId` as a plain `X-Branch-Id` header —
**not re-validated against the device secret per request, and independent of the
authenticated user's own `branchId`/D40 check that already happened at login time**.
`warehouseId` is never sent by the client — resolved server-side from `X-Branch-Id` at
order-creation time (`OrderService.resolveWarehouseForBranch`), one active warehouse per
branch assumed (no DB constraint yet), zero/multiple matches fail loudly
(`WAREHOUSE_NOT_FOUND` / `AMBIGUOUS_WAREHOUSE_FOR_BRANCH`) rather than silently picking one.

> Trade-off explicitly accepted: `X-Branch-Id` is trusted like `X-Tenant-Id` already is,
> without per-request cryptographic proof. Upgrading to a signed device JWT (branchId as a
> verified claim) is deferred until a real production POS integration needs it — tracked as a
> future hardening item, not blocking Orders module work.


### D41 — Order module: Order status is COMPLETE (reconfirmed, not PAID).
Reconfirms D19 as-is. No naming change — `status: COMPLETE | CANCELLED` stands.

### D42 — Order Consumption Doc: status enum is `PENDING | IN_PROGRESS | POSTED | CONFLICT`.
Renames D28's `COMPLETED` → `POSTED` for consistency with the DRAFT/COMPLETE/POSTED
lifecycle vocabulary used elsewhere (PurchaseInvoice, PhysicalCount, Waste). No other
naming change — the entry/open status is `PENDING` (not `DRAFT`), matching D28's original
naming. Doc/Line shape (D28) and the 3-step aggregation algorithm (D29) are unaffected.

### D43 — Real-time stock balance excludes IN_PROGRESS doc lines; PENDING only.
Supersedes D32's formula. Reconfirmed scope:
Displayed available qty = Current StockBalance − SUM(qty in PENDING-status
OrderConsumptionDoc lines only)
`IN_PROGRESS` is deliberately excluded from the subtraction — the processing window is
short enough that the resulting stock-balance blip is accepted as a known trade-off,
rather than adding IN_PROGRESS to the query. `StockBalance` itself is still only mutated
when a Doc reaches `POSTED` (D32's second half unchanged).

### D44 — Order Consumption Doc: concurrent order writes require a lock at check-then-create.
Two orders arriving in the same instant on the same warehouse must not each create their
own `PENDING` Doc. The "find PENDING doc for this warehouse, else create one" step must be
guarded — either a unique constraint on `(tenant_id, warehouse_id) WHERE status = 'PENDING'`
or a pessimistic lock (`SELECT ... FOR UPDATE`) around the check-then-create — so concurrent
orders land on the same Doc rather than racing into duplicate Docs.

### D45 — Order Consumption Doc: temporary direct-write recalculate button, bypassing OrderConsumptionEvent.
Testing-phase mechanism only, not the target design. A manual "recalculate" button on the
Doc header writes order-line consumption directly into the existing `PENDING`/`IN_PROGRESS`
`OrderConsumptionDoc` (find-or-create per warehouse, per D44's locking), skipping
`OrderConsumptionEvent` entirely. Button placement and enablement:
- **Now (testing)**: enabled in all Doc states.
- **Later**: enabled only when Doc status = `CONFLICT` (i.e. becomes the retry trigger for
  D31's "fix the cause, then retry" flow).
  Failure/success semantics stay exactly as D30 (material-level `errorDetails`, full-Doc
  `CONFLICT` on any material failure, no per-order or per-line counters). This button and its
  direct-write path are explicitly interim — `OrderConsumptionEvent` + the scheduled
  aggregation job (ROADMAP §1, "Hybrid Ledger") remain the target design and are still to be
  built.

### D46 — Fixed Assets: `Asset` (header) → `AssetLine` (per-purchase-batch) hierarchy; disposal/maintenance target a specific line, chosen manually — never FIFO.
An `Asset` represents a purchased item *type* (e.g. "Wood Chair", "Oven"); each purchase event
is a separate `AssetLine` under it (its own `quantity`/`unitCost`/`purchaseDate`), because the
same asset type is commonly bought at different prices over time and the client needs to track
that distinctly — the same reason `StockBatch` exists under a `Material`. This hierarchy also
covers large single-unit equipment (ovens, grills, AC units): each physical unit is its own
`AssetLine` (typically `quantity = 1`) under a shared `Asset` header (e.g. "Oven"), which gives
aggregate investment totals per asset type for free without a separate entity per physical unit.

Unlike Inventory's FIFO consumption (D10), disposal/maintenance always target an explicit
`assetLineId` chosen by the caller — no automatic oldest/cheapest/average selection logic exists
in the backend. This is a deliberate divergence from the ledger's FIFO pattern, not an
oversight: the user is expected to know and choose which purchase batch/unit is affected, and
bears responsibility for that choice.

`AssetLine` also carries an optional `label` (free text) — primarily useful when a line
represents a single trackable unit (`quantity = 1`, e.g. one specific oven/grill) so it can be
identified distinctly in maintenance/disposal history (e.g. "Oven — North Kitchen", "OVN-01").
Not required, and not tied to any quantity constraint — the backend imposes no rule linking
`label` presence to `quantity`.

### D47 — Fixed Assets: category is a fixed backend enum, not a tenant-configurable table.
`category: FURNITURE | KITCHEN_EQUIPMENT | FINISHING | ELECTRONICS | OTHER` on `Asset`. No
evidence tenants need custom categories (D13) — revisit only if a real need surfaces.

### D48 — Fixed Assets: `AssetDisposal` reduces `AssetLine.remainingQuantity`; status is derived, not stored authoritatively per write.
`AssetLine.status` (ACTIVE | PARTIALLY_DISPOSED | FULLY_DISPOSED) is derived from
`remainingQuantity` vs `quantity`. `Asset.status` (header) is derived from the aggregate state
of its lines. `quantityDisposed` on a single `AssetDisposal` is capped at the target line's
current `remainingQuantity` — never allowed to go negative.

### D49 — Fixed Assets: `AssetMaintenance` is a cost record only; never affects `quantity`/`remainingQuantity`.
Maintenance is scoped to `assetLineId`, expected in practice mostly on large single-unit
equipment (`quantity = 1`) — kitchen equipment, AC units, ovens. Small multi-quantity assets
(chairs, small tools) are, in practice, disposed and replaced rather than repaired; no
per-sub-unit maintenance tracking exists or is needed for V1.

### D50 — Fixed Assets: no delete after first disposal/maintenance; no profit-coverage report in V1.
Delete allowed only when the `AssetLine` has zero `AssetDisposal`/`AssetMaintenance` records
(mirrors D6's dual-check spirit — existence of child records blocks delete, no status gate
needed since there's no DRAFT/POSTED lifecycle here). V1 reporting is limited to total asset
value and a disposal list (date/reason/value) — **no** cost-coverage/ROI percentage, since that
requires the not-yet-built P&L/accounting module. Tracked as **O10** below.

### D51 — Fixed Assets: disposal/maintenance requests carry both `assetId` and `assetLineId`, validated as a pair.
Both `CreateAssetDisposalRequest` and `CreateAssetMaintenanceRequest` include `assetId`
alongside `assetLineId`, even though `assetLineId` alone is technically sufficient to resolve
the record (an `AssetLine` already has a non-nullable `assetId` FK). This mirrors the two-step
selection the UI walks the user through (pick `Asset` → pick `AssetLine`) so the request body
reflects exactly what was chosen, rather than silently dropping the first selection. The service
layer validates `AssetLine.assetId == request.assetId`, rejecting mismatches with a dedicated
`AssetErrorCode` (`LINE_ASSET_MISMATCH`) instead of trusting `assetLineId` alone. Read endpoints
follow the same nesting: `GET /api/assets/{assetId}/lines/{lineId}/disposals` and
`.../maintenance`, not a flatter `/api/assets/lines/{lineId}/...` shape considered earlier.

### D52 — Fixed Assets: `ASSETS_VIEW` and `ASSETS_MANAGE` are separate permissions.
Read endpoints (`GET /api/assets/**`, including the two report endpoints) are gated by
`ASSETS_VIEW`; write endpoints (create/update/delete on `Asset`/`AssetLine`, and creating
`AssetDisposal`/`AssetMaintenance`) remain gated by `ASSETS_MANAGE`, matching the read/write
permission split already used in other modules. This supersedes the initial backend pass, which
temporarily reused `ASSETS_MANAGE` on GETs (a documented judgment call, made because the
implementation prompt only specified writes) — that was a stopgap, not a decision to build on.

> Build note — accepted judgment calls from the first backend pass (see git history /
> `AssetStatusService`, `AssetReportService` for the actual code):
> - Asset-status aggregation rule (all-`ACTIVE`→`ACTIVE`; all-`FULLY_DISPOSED`→`FULLY_DISPOSED`;
    >   otherwise `PARTIALLY_DISPOSED`; a line-less `Asset` defaults to `ACTIVE`) — accepted as-is.
> - `totalCurrentValue = SUM(remainingQuantity × unitCost)` with no depreciation — accepted as
    >   the correct V1 reading of D50's report scope.
> - `TenantAwareEntity` audit columns (`updatedAt`/`updatedBy`) applied uniformly to all four
    >   tables, including `asset_line` (not explicitly listed in the original schema sketch) —
    >   accepted, matches the "tenant-owned rows extend `TenantAwareEntity`" convention.
> - FK target is the actual `branches` table (schema sketch said `branch`, a naming slip, not a
    >   type/nullability mismatch) — accepted.
> - Migration landed as `V16__assets.sql`.

> Full schema/entity/endpoint detail for this module lives in
> [modules/ASSETS.md](modules/ASSETS.md).

### D53 — Loyalty V1 scope: `Customer` is name + phone only; points, offers, change-approval, and reporting are all deferred.
V1 delivers only the base link between a `Customer` and their `Order`s — "I know who bought
what." Everything else discussed for Loyalty (points/earn-redeem, expiry, offers/promotions,
a Change Request/approval workflow for editing customer data, and any spend/visit
reporting) is explicitly **out of scope for this pass** and tracked separately (see
[ROADMAP](ROADMAP.md)). No schema, endpoint, or permission for any of those should be built
now — adding them "for later" would violate D13.

### D54 — Loyalty: `Customer` is tenant-scoped; identified by phone; only `name` + `phone` + audit columns.
`Customer extends TenantAwareEntity` (same pattern as other tenant-owned rows — non-null
`tenantId` + audit). Fields: `name`, `phone`. No email, address, birthdate, or any other
profile field in V1 — add only when a concrete need surfaces (D13). `UNIQUE (tenant_id,
phone)` — enforced at the DB level; a duplicate phone within a tenant always resolves to the
existing `Customer` row, never a second row. Registration happens exclusively from the POS at
first-order time (staff asks for phone, and name if new) — there is no separate
self-registration surface in V1.

### D55 — Loyalty: `Order` carries raw `customerPhone` (+`customerName` if new); backend does find-or-create; `Order.customerId` is nullable.
The order-creation request never sends a `customerId`. It sends `customerPhone` (required
whenever a customer is being linked) and `customerName` (present only the first time that
phone is seen). `OrderService` resolves this via a `CustomerService.findOrCreate(tenantId,
phone, name)` call before persisting the order — mirrors the "raw entered value in, single
resolution point inside the service" shape used by `InventoryLedgerService.record()` (D3),
applied here to customer identity instead of stock quantity.
`find-or-create` concurrency: rely on the `UNIQUE(tenant_id, phone)` constraint as the real
guard — attempt insert, catch `DataIntegrityViolationException`, re-select on conflict (same
idempotency-guard shape as `IdempotencyService`, not a new pattern).
`Order.customerId` is a **nullable** FK. If `customerPhone` is absent, or the find-or-create
call fails for any reason, order creation must still succeed with `customerId = null` — the
order is never blocked by a Loyalty-side failure. The link, when present, is set once at
order-creation time only; nothing retroactively attaches a customer to an already-created
order in V1.
Conflict rule when the same phone is registered concurrently with two different names
(e.g. two offline devices): **first write to reach the server wins the name**; a later
create attempt that hits the unique constraint simply resolves to the existing row and
discards the incoming name. No automatic overwrite — mutating an existing customer's name
is out of scope until the Change Request workflow (D53) is built.

### D56 — Loyalty: POS keeps a full local `(id, name, phone)` customer list, synced at login/shift-open; no live per-keystroke lookup.
To support the intended staff flow (customer states their phone, cashier's screen shows
their name immediately), the POS pulls the tenant's full customer list once at
login/shift-open (`GET /api/loyalty/customers`, mirroring the branch/warehouse caching shape
already established for devices in D33/D41) and matches locally as the phone is typed — no
per-keystroke server round trip. A newly-registered customer (via the in-order-screen
"new customer" popup) is appended to the local list immediately after the create call
succeeds, so it's available for the rest of the shift without a re-sync.
Keeping the list fresh across devices/shifts (periodic refresh vs. next login only) is not
addressed further in V1 — login/shift-open refresh is the only sync point; a real delta-sync
mechanism is deferred (D13 — no abstraction ahead of a second concrete driver).

### D57 — Loyalty: offline customer registration principle (mechanism deferred to the general Offline capability work).
Only the **principle** is decided now, not the mechanism: when the Orders module's offline
capability (ROADMAP §1) is eventually built, customer registrations in the offline queue must
always be synced to the server **before** the orders that reference them, for any given
device's queue. This is recorded now so the ordering constraint isn't lost, but the queue
data structure, retry/backoff, and conflict resolution are explicitly OPEN — see
[ROADMAP](ROADMAP.md) and do not build against this decision until the Offline capability
itself is designed.

> Build note — accepted judgment calls from the V1 backend pass (see
> `loyalty/customer/CustomerService.java`, `CustomerController.java`,
> `V18__loyalty_customer.sql` for the actual code):
> - Two permissions, `LOYALTY_VIEW` (GET) / `LOYALTY_MANAGE` (POST), matching the
    >   read/write split precedent from Fixed Assets (D52) rather than one shared permission.
    >   Granted to `OWNER`, `SYS_ADMIN`, `BRANCH_MANAGER`, `CASHIER` by default — the cashier
    >   needs both, since they drive the in-order-screen new-customer popup.
> - `CustomerService.findOrCreate` runs in `REQUIRES_NEW` — a deliberate strengthening of
    >   the "Loyalty failure must never fail the order" requirement (D55) so it holds even
    >   against an already-poisoned enclosing transaction, not only via the caller's try/catch.
> - Migration landed as `V18__loyalty_customer.sql` (customer table, `orders.customer_id`
    >   nullable FK, permission rows + grants).
> - `Order` integration edits (nullable `customerId` column, `OrderRequest.customerPhone`/
    >   `customerName`, `resolveCustomerId(...)`) live inside the pre-existing, not-yet-committed
    >   `order/` package — those hunks are deliberately **not** bundled into the loyalty commit;
    >   they'll land in git history whenever the Order module itself is first committed, not as
    >   part of this pass.

> Full schema/entity/endpoint detail for this module lives in
> [modules/LOYALTY.md](modules/LOYALTY.md).

---

## OPEN (undecided — do NOT present as decided)

### O1 — Shortfall retroactive COGS correction.
Deferred to the Orders module. Today the shortfall is priced at current average with no
back-correction (D11). Whether Orders will need a retroactive COGS adjustment is **not decided**.

### O2 — Aggregator API / webhook design.
Talabat / Otlob / Noon Food / Fawry ingestion. Manual entry ships first; the automated
API/webhook contract (auth, dedup, mapping to the unified `Order`) is **not decided**.

### O3 — Approval-workflow config surface.
The `ApprovalWorkflow` entity is planned, but *what* is configurable (per document type, per
threshold, per role, per tenant) and the storage/UI shape are **not decided**.

### O4 — Enum-value translation approach.
Enum values need localized labels on the FE, but the mechanism (per-value keys vs a generated
map vs backend-supplied labels) is **not decided**. Partial per-value keys exist today.

### O5 — Order Consumption Doc grouping basis.
What determines how orders get grouped into a Doc in the first place — per-tenant configurable
interval (1h/2h/8h) vs. per-shift vs. something else — is **not decided**. To be resolved after
the Order module foundation (Order/OrderLine entities) ships.

### O6 — POS → system order ingestion transport, and whether the POS can echo `IncomingOrderRequest.id` back.
Exact endpoint/payload shape for the POS → system order ingestion call (single API call vs.
queue/integration layer) is **not decided** — the POS itself hasn't been built/designed yet.
Whether the POS integration can support echoing back the internal reference we send it
(needed for D24's linking mechanism) is an unverified integration requirement.

### O7 — Aggregator branch-selection mechanism.
Whether/how Talabat, Uber Eats, breadFast, etc. communicate which branch an order is for is
**not decided** — depends on each aggregator's actual API, not yet reviewed.

### O8 — Whether third-party payloads arrive pre-normalized or need per-aggregator adapters.
Whether Talabat/Uber Eats/breadFast send a unified payload shape (via some intermediary) or each
requires its own mapping/adapter is **not decided**.

### O9 — Tenant-created custom roles.
Deferred from V1. If built, `Role` will need a nullable `tenantId` column (NULL = global/
default role, non-null = tenant-specific custom role) — same nullable-tenant pattern as
`Uom`. Not a current blocker; schema change is additive whenever it's picked up.

### O10 — Fixed Assets: cost-coverage report against net profit.
Deferred until the P&L/accounting module exists (D50). Whether coverage will be computed
against manually-entered net profit or fully system-derived profit (Orders revenue − COGS −
payroll − other expenses) is **not decided** — revisit once the accounting module's design
starts.

### O11 — Loyalty: points system (earn/redeem rules, expiry, sync timing).
Deferred out of V1 entirely (D53). Earn rule (percentage of invoice vs. flat per-currency-unit
vs. flat per-order), redemption mechanics (minimum balance, conversion to discount), expiry
policy, and whether it's computed synchronously at order time vs. via a batch job are all
**not decided**.

### O12 — Loyalty: offers/promotions design.
Flagged as important and planned for the roadmap (D53), but not designed. Whether it lives
inside the Loyalty module or as a separate module, and whether offers are global-per-tenant or
targeted at a customer segment/tier, are **not decided**.

### O13 — Loyalty: Customer data Change Request / approval workflow.
Principle agreed (staff can request a change to a customer's `name`/`phone`; a user holding a
new `CUSTOMER_DATA_APPROVE`-style permission approves it manually — no automated verification
required, e.g. no forced confirmation call) but not designed. Open: whether the request stores
a diff or a full new snapshot, and whether multiple concurrent pending requests against the
same customer are allowed (leaning yes, to keep it simple) or should be constrained. Not
building any schema/endpoint for this until picked up.

### O14 — Loyalty: customer spend/visit reporting and metrics.
The original motivation for the module (total spent, online vs. in-branch split, cash vs.
card split, visit frequency per customer) is explicitly **deferred until after the base
Customer↔Order link (D53–D57) ships**. Whether this ends up as live queries directly against
`Order` (no new tables — consistent with D13) or denormalized fields on `Customer` (mirroring
the `lastPurchase*`/`lastCount*` pattern in Inventory, D5) is **not decided** — note that
`Order.orderSource` and `Order.paymentMethod` already carry the online/offline and
payment-method dimensions, so no new raw data capture is anticipated, only aggregation.

### O15 — Loyalty offline sync queue mechanism.
Only the ordering principle is decided (D57: customer registrations sync before orders in any
device's offline queue). Queue data structure, retry/backoff, and conflict resolution are
**not decided** — deferred to when the Orders module's general offline capability (ROADMAP §1)
is designed.

## Negative Stock Batches (Order-driven Shortfall) — Deferred Feature

Deferred entirely, not a blocker for the Order module. Current assumption for V1: the user
enters purchase invoices regularly enough that open batches cover consumption; on a rare
shortfall, the system falls back to the existing default behavior (D1 — `StockBalance` allowed
to go negative, D11 — priced at current average, no retroactive correction). No negative-batch
creation, no per-material shortfall ledger, no settlement mechanism — all deferred. If/when
built: a config flag (tenant/warehouse level) to opt in, negative-balance records scoped at
material+warehouse level (not folded into `StockBatch` itself, to avoid overloading its
"consumed from" responsibility), and settlement against new incoming batches handled as an
internal linking/audit table rather than a second `inventory_transaction` entry (no retroactive
backdated ledger rows).