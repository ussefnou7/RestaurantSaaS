# Code Findings from the Doc Drift Audit

> Extracted 2026-08-30 from `claude/DOC_DRIFT_AUDIT.md` (backend `63ff8e7e`, admin-web
> `c0f2155`, POS `03b0e81`).
>
> **These are not documentation problems.** The audit was scoped to verify doc claims, but
> seven findings are defects, security gaps, or plan-vs-code divergences. They are recorded
> here so the Phase 2 doc rewrite does not absorb them into corrected prose and lose them.
>
> **Status as of 2026-08-30:** findings **2, 9, and 10 are resolved** on branch
> `fix/p0-batch-2026-08-30` (`41a434f`, `a3572d6`, `80215f6`), each with tests, suite at
> **701/701**. Finding **1 remains open and is the most consequential in this file** — it needs a
> product decision on which cancellation stages consume stock, tracked as D20 in
> `docs/DECISIONS.md` → OPEN. Findings **3-8 are unchanged**. Entries are marked resolved in
> place, never deleted.
>
> **Items 9 and 10 were added 2026-08-30 and are not from the audit table.** They surfaced
> during the Phase 4 verification pass over `docs/modules/` and `docs/business-flows/`, which
> read code the doc-claim audit had no reason to open. Both are P0. Finding 9 in particular
> means **`main` currently has a failing test** — see below.

---

## P0 — Fix before any pilot restaurant

### 1. Cancelled-after-cooking never becomes waste

**Audit row:** `DECISIONS.md:336-363` (D20), verdict `WRONG`.
**Evidence:** `order/core/OrderService.java:120-138`, `:222-241`.

`DECISIONS.md` records as **DECIDED** that cancellation stages map to waste/consumption
consequences. The code performs **no waste mapping for any cancelled order**. Only a
`COMPLETE` order calls `recordCompletedOrder`. Cancellation stage and reason are validated
and persisted, and nothing else happens to them.

**Consequence:** food that was cooked and then cancelled physically leaves the store and
never leaves the system's stock. Inventory is systematically overstated by exactly that
quantity. It surfaces later as an unexplained physical-count shrinkage with no attribution
and no audit trail — the hardest kind of variance to investigate, months after the event.

**Second consequence — this is the important one.** `docs/LOSS_PREVENTION.md` §1 and §4
both depend on cancel-to-waste being real. The theoretical-vs-actual consumption comparison
is the *only* control that can detect an unrung sale, and cancel-inflation is the *easiest*
way to steal cooked food. Right now the system records the cancellation (good) but never
connects it to stock (bad), so the number that would expose the pattern does not exist.

**Decision needed before the fix:** which cancellation stages consume stock. Cancelled
before firing to the kitchen → no consumption, correctly. Cancelled after firing → the food
was made; it must either post as waste or, if the waiting-list idea lands, transfer to a
re-sale path. The stage enum already exists and is persisted, so the input is there.

> **Status update (Phase 2):** D20 has been moved from DECIDED to OPEN in `docs/DECISIONS.md`
> with an explicit not-implemented note, so the decision no longer reads as ground truth. The
> code gap is unchanged — this finding stays open.

### 2. Missing permission annotations on the ledger-writing transitions

**Audit row:** `CONVENTIONS.md:130-138`, verdict `WRONG`.
**Evidence:** `inventory/uom/UomController.java:27-43`;
`inventory/purchase/PurchaseInvoiceController.java:159-185`;
`inventory/purchase/PurchaseReturnController.java:155-180`.

Three gaps:

- Tenant `UomController` has **no** `@PreAuthorize` on any endpoint.
- `POST /{id}/post` on **PurchaseInvoiceController** has no permission annotation, while
  adjacent transitions on the same controller do.
- Same omission on **PurchaseReturnController**.

Global authentication still applies, so this is not open to the internet. But `post` is the
transition that **writes the inventory ledger and moves money** — it is the single most
consequential action in the module, and it is the one missing its gate. Any authenticated
user in the tenant can post, regardless of role.

The audit was right to refuse to weaken `CONVENTIONS.md` to match. Fix the code.

> **RESOLVED — `a3572d6`, 2026-08-30.** `post` on both purchase controllers now carries
> `INVENTORY_PURCHASE_MANAGE`, matching the adjacent `complete`/`cancel` transitions; no
> `PURCHASE_*_POST` permission exists in any seed, and the dedicated codes are reserved for
> reversals. `UomController` now carries `INVENTORY_SETUP_VIEW` on its three reads and
> `INVENTORY_SETUP_MANAGE` on create/deactivate/delete — no permission was invented: the V2 seed
> describes that pair as "View/Manage inventory setup (materials, categories, **UOMs**)", and the
> sibling setup controllers already use it.
>
> **What this finding understated.** The gap was not an omission the tests failed to catch —
> **three tests asserted it as intended behaviour**, including
> `PurchaseInvoiceControllerContractTest`, which asserted at the reflection level that `post`
> carries *no* `@PreAuthorize`, so adding the annotation broke the build. All three are replaced
> by pairs asserting 403 without the permission and 200 with it, plus a new
> `UomControllerSecurityTest` covering all six UOM endpoints. An assertion that a security control
> is absent needs a stated reason; there was none.

### 9. `recalculate` status guard is commented out — and its test is failing on `main`

**Source:** Phase 4 verification pass, not the audit table.
**Evidence:** `inventory/orderconsumption/OrderConsumptionService.java:179-192` (method at
`:179`, guard commented at `:182-191`); test at
`src/test/java/.../orderconsumption/OrderConsumptionServiceTest.java:421-430`.

The status guard in `OrderConsumptionService.recalculate` is commented out. The method now
sets **any** document to `IN_PROGRESS` and reprocesses it, regardless of current status.
This contradicts D45/D94 and the method's own javadoc three lines above the comment block,
which still states the retry is "enabled for PARTIAL and CONFLICT docs".

**Consequence.** A `POSTED` document has already written its per-material
`CONSUMPTION_SUMMARY` rows to the ledger. Reprocessing it re-enters the posting path with
stock-deducting intent. The per-material ledger idempotency key
(`ORDER_CONSUMPTION_DOC:<docId>:MATERIAL:<materialId>`) is the only thing standing between
this and a double deduction — an idempotency key is a retry-safety net, not an authorization
model, and relying on it to hold back a deliberate reprocess of settled stock is not a
control anyone chose.

**Reachability: confirmed reachable.** It is not dead code behind an internal call. The
method is exposed at `POST /api/inventory/order-consumption-docs/{id}/recalculate`
(`OrderConsumptionController.java:71-72`), gated on `INVENTORY_STOCK_MANAGE`. Worse, that
endpoint's Swagger `@Operation` description still reads "D45/D94 manual retry for PARTIAL or
CONFLICT documents" — so the published API contract actively describes a restriction the
code does not enforce.

**The test already exists and it fails.** `recalculateRejectsNonConflictDoc` builds a
`POSTED` doc and asserts the throw. It carries no `@Disabled`. Full suite on `63ff8e7e`:

```
[ERROR] OrderConsumptionServiceTest.recalculateRejectsNonConflictDoc:427
Expecting code to raise a throwable.
[ERROR] Tests run: 687, Failures: 1, Errors: 0, Skipped: 0
```

So this is not "add a test" — it is **`main` is red and nobody has run it**. That is the
finding underneath the finding, and it is worth more than the guard itself: a commented-out
invariant with a live test asserting it should have been caught by the first `mvn test` after
the edit.

**Confirmed by the suite health check (`claude/SUITE_HEALTH.md`, 2026-08-30).** This is the
**only** failure in 687 tests across 99 test classes; nothing is `@Disabled`, nothing is flaky,
and a clean compile plus the full suite takes 50.827 seconds. The guard was commented out in
**`2ae6132` on 2026-08-08** — a 121-file, 8,249-insertion
`chore: commit pre-existing uncommitted work tree` bulk commit, in which the breaking change is
two lines (`/*` … `*/`). **Local `main` has 33 later commits over 22 days; 10 of them added or
modified tests.** Of those 33 later local commits, 26 were also pushed to `origin/main` and
five of those changed tests; local `main` is seven commits ahead of the remote. The reason nothing
caught it: **there is no CI in any of the six actual Git repositories**. The backend has no
active hook; the admin frontend's only hook lints staged
TypeScript and cannot cover this suite; and the backend's automated deploy build (`nixpacks.toml`)
runs `./mvnw -B -DskipTests package`.

**RESOLVED — `41a434f`, 2026-08-30.** Straight uncomment, no rewrite. All four artefacts
confirmed still in agreement after the restore. `recalculateRejectsNonConflictDoc` passes and the
full suite went **687/687, BUILD SUCCESS** — the first green run since 2026-08-08.

**Why it was commented out: no commit did it deliberately.** It was already commented in an
uncommitted working tree when `2ae6132` swept it in, and that commit's own message records
"Verified: **mvn compile** passes on this tree" — compile, not test. The guard was last confirmed
present on 2026-07-16 in `7d8cc5a`, which updated the `@Operation` text specifically to document
the restriction. Nothing to preserve, so nothing was left as a comment block.

**Fix order matters.** The guard is restored, so CI can now be added green. See
`claude/SUITE_HEALTH.md` §5 for the enforcement recommendation (GitHub Actions + branch
protection, ~20 minutes).

**Fix:** uncomment the guard — `InventoryErrorCode.ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT`
still exists (`InventoryErrorCode.java:37`), so it is a straight restore, not a rewrite. Then
find out why a failing test survived on `main`, because that is the reusable half of this
finding. If the guard was disabled deliberately to unblock something, that reason needs to be
written down and the test updated in the same commit — not left as a comment block.

### 10. Large-variance detection sums signed values, so offsetting variances cancel

**Source:** Phase 4 verification pass, not the audit table.
**Evidence:** `inventory/core/PhysicalCountService.java:567-572`.

```java
BigDecimal totalVarianceValue = lines.stream()
    .map(l -> l.getVarianceValue() != null ? l.getVarianceValue() : BigDecimal.ZERO)
    .reduce(BigDecimal.ZERO, BigDecimal::add);          // signed sum
count.setHasLargeVariance(totalVarianceValue.abs().compareTo(LARGE_VARIANCE_THRESHOLD) > 0);
```

The line values are added **with their signs**, and only the total is made absolute. A count
that is 600 EGP short on one material and 600 EGP over on another nets to zero and is **not
flagged**, even though 1,200 EGP of stock is unaccounted for across two lines.

**Consequence.** This is blind to the most natural concealment pattern there is. Anyone
adjusting a count to hide a shortage does not need to know the formula to defeat it — padding
an unrelated material until the totals look calm is the obvious move, and it is also what an
*honest* pair of miscounts looks like, which is why the control has to see both numbers rather
than choose between them. `docs/business-flows/PHYSICAL_COUNT_FLOW.md` previously documented
the formula as `Σ|variance × cost|`, i.e. the gross version — so the intent on record is gross
and the code is net. Phase 4 corrected the doc to describe the code and marked the behaviour
as an open question rather than asserting either was intended; this finding is where it gets
settled.

**Fix shape — store both, they answer different questions:**

| Value | Meaning | Used for |
|---|---|---|
| **Net** `Σ (variance × unitCostAtFreeze)` | accounting impact — what the count did to inventory value | the P&L number; keep as `largeVarianceValue` |
| **Gross** `Σ \|variance × unitCostAtFreeze\|` | control exposure — how much stock moved unexplained | the flag, the approval gate, the exception report |

Gate approval on **gross**. A count whose lines cancel out is *more* interesting than one that
does not, never less.

**Cross-reference `docs/LOSS_PREVENTION.md` §12**, which calls physical-count manipulation
"the master key" and specifies that variance above a configured threshold requires approval.
That control is only as good as the threshold it fires on: gating on the net figure means the
approval step can be bypassed without ever exceeding it. §12 also wants the threshold
configurable per tenant — worth doing in the same pass, since `LARGE_VARIANCE_THRESHOLD` is
currently a hardcoded `BigDecimal("500")` constant (`PhysicalCountService.java:80`).

> **RESOLVED (gross/net) — `80215f6`, 2026-08-30.** Both figures are now computed and stored: net
> stays in `large_variance_value`, gross lands in the new `gross_variance_value` column (`V51`),
> and `has_large_variance` is derived from **gross**. Test
> `offsettingVariancesAboveThresholdAreFlaggedOnGrossEvenWhenNetIsZero` covers the -600/+600 case
> that is the entire point of the fix. Counts reconciled before `V51` keep a NULL gross —
> deliberately not backfilled, because gross cannot be reconstructed from the stored net total and
> a RECONCILED count is never recomputed on read (D90). NULL reads as "evaluated under the old
> rule", not as zero. This changes which counts trip the threshold.
>
> **STILL OPEN: the per-tenant threshold.** `LARGE_VARIANCE_THRESHOLD` remains a hardcoded
> `BigDecimal("500")`. It was **not** made configurable, and this was reported rather than built:
> there is no tenant configuration surface at all — no `TenantSettings` entity, no
> `tenant_feature` table, and `Tenant` carries only name/code/status/timezone. Adding one lands
> directly on **O40** (*"Tenant-level configuration: split typed settings from feature toggles"*),
> which is explicitly OPEN on whether settings live in typed columns on `Tenant` or a separate
> companion entity. That is a design decision, not a fix — it needs D-numbering before code.

---

## P1 — Broken or divergent, fix before it reaches a customer

### 3. Inventory transfers: a live UI pointing at endpoints that do not exist

**Audit row:** `PROJECT.md:66-67`, verdict `CONFIRMED` (with a discovery).
**Evidence:** `inventory/transfer/InventoryTransfer.java:29` (entity only, no repository,
service, controller, or tests); `restaurant-saas-web/src/app/router.tsx:278-285`;
`restaurant-saas-web/src/services/inventoryTransferService.ts:19-76`.

The backend has two entities and nothing else. The **admin frontend has routed pages and a
service that calls `/api/inventory/transfers`** — routes that do not exist. A user can
navigate to a transfers screen in the shipped admin app and get failures.

Either hide the routes behind a flag until the backend lands, or build the backend. Leaving
a dead screen in a product being demoed to restaurant owners is worse than not having the
feature.

### 4. Cancellation carries no server-side event time

**Audit row:** user-suspect cancellation boundary, verdict `CONFIRMED`.
**Evidence:** `restaurant-pos/src/pos/usePos.tsx:951-987`;
`order/core/dto/OrderRequest.java:30-48`.

There is no dedicated `cancelledAt`. On cancellation the POS generates a **fresh
client-local `orderDate`**, which becomes the only event time the backend receives. So for
a cancelled order the stored time is the cancel time, not the order time, and both are on a
clock the operator controls.

This is `docs/LOSS_PREVENTION.md` §18 (dual timestamps) and it also breaks §1's "elapsed time
between order creation and void" control — the creation time is overwritten.

**Fix shape:** persist `orderDate` and `cancelledAt` separately, and stamp a server receive
time on arrival. Cheap now; expensive after there is history to migrate.

### 5. Availability subtraction is centralized, then duplicated at the last step

**Audit row:** `DECISIONS.md` D43/D94, verdict `CONFIRMED` (with a caveat).
**Evidence:** `inventory/orderconsumption/OrderConsumptionAvailabilityService.java:17-69`;
`inventory/core/StockBalanceService.java:354-364` and `:367-372`.

The outstanding-quantity logic is properly centralized in one service, and only
`StockBalanceService` consumes it — that part is well built. But the **final subtraction is
written twice**, in the list-mapping path and the single-balance mapping path.

Today they agree. They are one careless edit away from the list screen and the detail screen
showing different available quantities for the same material, which is the "two sources of
truth" failure. Collapse them into one private method.

---

## P2 — Divergence from plan; decide, then document

### 6. Consumption batching thresholds are global, not per tenant

**Audit row:** `ROADMAP.md:16-18`, verdict `STALE`.
**Evidence:** `inventory/orderconsumption/OrderConsumptionBatchingProperties.java:8-29`;
`OrderConsumptionBatchingScheduler.java:48-85`; `application.yml:49-57`.

The scheduler is built and is better than documented — it flushes on **line count ≥ 50 OR
oldest pending line ≥ 8 hours**, polled every 60s. The age trigger closes the "quiet day
never posts" gap.

But count, age, poll interval, and enablement are **application properties**. There is no
per-tenant configuration entity. The stated intent was a per-client threshold scaled to
restaurant size. Either build the per-tenant setting or drop the intent — but the docs
should stop implying it exists.

**Related open question, not audited:** an 8-hour age trigger means a slow branch can carry
unposted consumption across a shift boundary. If shift close is meant to be a reconciliation
point, consumption should flush on shift close regardless of thresholds.

### 7. Wired but untested surfaces

Three surfaces are live and permission-protected with **zero test references**:

| Surface | Evidence |
|---|---|
| `SalaryService` | `hr/service/SalaryService.java:20-70` — not referenced in `HrServiceTest` |
| `SalaryAdjustmentService` | `hr/service/SalaryAdjustmentService.java:18-69` — same |
| `IncomingOrderRequest` intake | `order/intake/*`; zero references under `src/test/java` |

The intake one matters most: it mutates order linkage through a permission-protected
`PATCH /api/order-requests/{id}/link` with no test covering it.

> Finding 9 is the counterpoint to this item and should be read alongside it: order
> consumption *is* well covered by tests, and a covered invariant still regressed because the
> failing test was not run. Coverage and enforcement are two different problems.

### 8. `DocumentHistory` violates two conventions before it is even wired

**Audit row:** `CONVENTIONS.md:90-100`, verdict `WRONG`.
**Evidence:** `inventory/core/DocumentHistory.java:51-55`.

The dormant entity has an `@PrePersist` hook calling server-zone `LocalDateTime.now()` —
both explicitly forbidden. Harmless while unwired; a timezone bug the day it is wired. Fix
it in place now, while it costs nothing.

---

## Not audited — follow-up passes needed

These were outside the audit's scope and remain unverified:

- **Tenant isolation.** No systematic check that every tenant-scoped query filters on
  `tenant_id`. One missing filter is a cross-tenant data leak. To be covered by
  `claude/PROMPT_TENANT_ISOLATION_AUDIT.md` (not yet written).
- **Customer resolution by phone.** The backend resolves customers by phone/name rather
  than accepting a POS-local id (`OrderRequest.java:20-69`). Confirm that lookup is
  tenant-scoped; a global phone index would cross tenants.
- **Order-number gap detection.** The POS sends a display order number and an idempotency
  key, and an offline outbox already exists. Whether the backend detects **gaps** in
  per-device sequences is unverified — this is `docs/LOSS_PREVENTION.md` §15, and the outbox
  being built already makes it cheap to add now.
- **Outbox drain failure.** What happens to queued orders if a device is lost, wiped, or
  never reconnects. Unverified.
- **Loss-prevention section A is not computable server-side today — and that is a roadmap
  decision, not a bug.** Verified in Phase 4: the POS sends **only the settled order**. One
  `POST /api/orders` call carries a final `COMPLETE` or `CANCELLED` status; everything before
  it — ticket opened, item fired, item removed, price overridden, discount applied — never
  reaches the backend. Per D19 that is deliberate, and the POS owns the in-progress
  lifecycle by design.

  The consequence is structural: **`docs/LOSS_PREVENTION.md` §16 (event-stream sync) is a
  precondition for section A, not an enhancement of it.** Void rate, void-after-kitchen-print,
  post-payment discount, deletion-after-fire, price-override rate — every one of those ranks
  events the server has never seen. They cannot be built, partially built, or approximated
  until the POS uploads an append-only event stream.

  File this as a **roadmap-level scope decision**, not a defect. Nothing is broken; the data
  was never designed to exist. What needs deciding is whether §16 moves into the Orders/POS
  roadmap ahead of the section-A controls that depend on it, or whether section A is deferred
  wholesale until it does. `docs/LOSS_PREVENTION.md` already lists §16 as "deferred, design
  the schema in V1" — that ordering should be revisited now that the dependency is confirmed
  rather than assumed.

---

## What the audit found in the project's favour

Worth recording, because the drift ran in both directions:

- **Exception migration is nearly done**, not barely started. Actual counts: Inventory
  **0** (doc said 7), RBAC **0** (doc said 4), Auth **1** (doc said 3), Tenant **3**
  (doc said 3, correct). Roadmap item 4 is one small pass from complete.
- **Recipe versioning is built** — immutable versions, prior version deactivated on create,
  history reads. The roadmap had it as backlog. This also closes the "recipe changes
  mid-day corrupt deferred consumption" risk.
- **Product variants and add-ons are built** and projected into the cashier menu.
- **The offline outbox is real** — SQLite-backed, with one idempotency key generated before
  the fast attempt and reused on retry, covering both completion and cancellation. Three
  DECISIONS entries (D57, D65, O18) understate this.
- **Modules missing from the map entirely**: `menu/`, `loyalty/` (customers), `device/`,
  `pos/` (cashier shifts), `table/`. All built, tested, permission-protected.
- **52 inventory test files**, plus real integration tests. The roadmap's integration-suite
  item should narrow to "Testcontainers-isolated datasource", not "integration tests do not
  exist".
- **Order consumption is genuinely well built** — a claim/process/aggregate/post service with
  per-material failure classification (`INSUFFICIENT_STOCK` → PARTIAL, `TECHNICAL_FAILURE` →
  CONFLICT), a dual-trigger scheduler that respects each tenant's own wall clock, and unit,
  integration, and security coverage. Finding 9 is a single commented-out guard inside an
  otherwise careful module, which is exactly why it is worth restoring rather than redesigning.
