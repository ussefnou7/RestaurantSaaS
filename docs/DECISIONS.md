# DECISIONS

> Two strictly separated sections. **DECIDED** items are ground truth: do not reopen them,
> and any code that contradicts one is a bug to be fixed (not a reason to change the decision).
> **OPEN** items are undecided — never present them as settled or build irreversible code on a
> guessed answer.
>
> Each DECIDED item was verified against the real code in this pass; the verification note and
> `file:line` anchor follow it. Status legend: ✅ holds · ⚠️ holds with a wording nuance ·
> ❌ code currently violates it · 🕓 decided but not built (design stands, no code yet).
>
> A decision that has been superseded keeps its number and its text, with a supersede pointer
> added directly under its heading — never delete or renumber. Build notes and known-limitation
> blocks belong under the decision they describe.

---

## DECIDED (ground truth)

### D1 — `StockBalance.quantity` is the signed ledger delta only, not batch-derived; negative is allowed. ✅

Quantity moves by the ledger’s signed delta (positive on IN, negative on OUT) and is permitted to go negative on a FIFO
shortfall; it is **not** re-derived from batches.
`StockBalanceService.applyMovement` — `inventory/core/StockBalanceService.java:240-244`
(and the class javadoc, lines 40-47).
> **Wording refined by D87.** The delta is converted from stock UOM to `material.displayUom`
> before being applied; "signed ledger delta only" omitted that step. Everything else
> (not batch-derived, negative allowed) is unchanged.
> **Narrowed by D94.** This no longer applies to order consumption: a material whose open
> batches cannot cover the requested quantity is skipped entirely (doc → `PARTIAL`), so order
> consumption never drives the balance negative and never prices a shortfall at the average.
> The behaviour described here remains reachable only through paths that call `consumeFifo`
> directly without a pre-check — verify before relying on it.

### D2 — Average cost is derived from OPEN batches only (`remainingQuantity > 0`); no running incremental formula. ✅

`averageCost = Σ(remainingQty × unitCost) / Σ(remainingQty)` over OPEN batches, recomputed after every batch mutation;
there is no incremental `(oldQty·oldAvg + Δ·cost)/newQty` formula and no “cost-bearing transaction” classification.
`StockBalanceService.deriveAverageFromOpenBatches` — `inventory/core/StockBalanceService.java:290-304`;
`StockBatchRepository.sumOpenBatchTotals`.
> Note: the legacy `docs/PROJECT_SKILL.md` still documents the old running weighted-average
> formula. That doc is stale; **this decision + the current code are authoritative.**
> **Unit clarification (D87).** The derived average is per **display** UOM, consistent with
> `StockBalance.quantity`'s unit.

### D3 — Entered→stock UOM conversion for a ledger entry happens once, inside

`InventoryLedgerService.record()`; callers never pre-convert. ⚠️

Callers build a `LedgerCommand` with the **raw entered** quantity/UOM/unit-cost; `record()`
performs the single `convertToStockUom(...)` — `inventory/core/InventoryLedgerService.java:80`.
> Wording nuance: `UomConversionService` **is** used elsewhere (`StockBalanceService`,
> `StockBatchService`, `PurchaseReturnService`, `WasteService`) for *downstream* display-UOM
> math and guard checks — that is legitimate and not “caller-side pre-conversion.” Read this
> invariant as: **the ledger entry’s entered→stock conversion is done exactly once in
> `record()`; no caller pre-converts the entered qty/cost it passes in.**
> **Generalized by D87.** The entered→stock conversion in `record()` is one of exactly two
> conversion boundaries; the other is the ledger's stock-UOM delta → display-UOM balance
> conversion in `StockBalanceService`. Read D87 for the full model.

### D4 — `inventory_transaction` is written only via `InventoryLedgerService`. ✅

Sole `transactionRepo.save(...)` is `InventoryLedgerService.java:260` (inside
`saveWithIdempotencyGuard`). No other class writes the table.

### D5 — `stock_balance` is written only via

`StockBalanceService`. ⚠️ (holds for qty/avg; violated for denormalized fields)

The ledger-owned fields — `quantity` and `averageCost` — are written **only** by
`StockBalanceService` (`applyMovement`, `recalculateFromOpenBatches`). ✅ However the **denormalized display fields** are
`saveAll`’d directly by operation services:

- `lastPurchasePrice` / `lastPurchaseDate` — `PurchaseInvoiceService.java:250`,
  `PurchaseReturnService.java:364`
- `lastCountDate` / `lastCountQuantity` — `PhysicalCountService.java:387`
- (`StockBalanceAverageCostBackfill.java:83` — one-off backfill utility)

> Recommended precise wording: **`stock_balance.quantity` and `averageCost` are written only via
> `StockBalanceService`; the denormalized last-purchase / last-count fields may be written by the
> owning operation service.** With that refinement the code holds. As literally worded (“only via
> StockBalanceService”), the three sites above are violations — see [REVIEW](REVIEW.md) → invariants.

### D6 — Delete is allowed only when status is DRAFT

**and** there are no ledger transactions (dual-check, not status alone). ✅

`PurchaseInvoiceService.delete` — status==DRAFT (`:340`) **and** `!existsByReference(...)`
(`:346`); `PurchaseReturnService.delete` — same dual-check (`:446`, `:452`).

### D7 — Waste has no Unpost and no reversal of any kind. ✅

`WasteService` exposes no `unpost`/`reverse`; once POSTED a waste document is terminal (`cancel` is rejected from
POSTED, allowed only from DRAFT/COMPLETE; `uncomplete` only moves COMPLETE→DRAFT, i.e. pre-posting).
`inventory/core/WasteService.java:279-294`.

### D8 — Purchase Invoice Unpost checks the return-existence guard **first**, then the batch-consumption guard. ✅

`PurchaseInvoiceService.unpost` — `assertNoPurchaseReturns(...)` (`:274`) runs before
`assertNoConsumedBatches(...)` (`:280`), so “unpost the return first” wins over the vaguer batch message. A third
independent `assertBatchesReversible(...)` runs immediately before the batches are hard-deleted (`:288`).

### D9 — Purchase Return Unpost needs no batch-consumption guard (it is additive). ✅

`PurchaseReturnService.unpost` guards only `assertOriginalInvoiceStillPosted(...)` (`:386`), then reverses the ledger
and `restoreSourceBatch(...)` (additive, capped at the batch’s original quantity). No consumption guard.
`inventory/core/PurchaseReturnService.java:376-418`.

### D10 — Batch consumption order is `movementDate ASC, id ASC`. ⚠️ (revised)

> **Revised during the D87–D92 audit.** The original rule — creation order (`id` ascending)
> only — is superseded. `movementDate` (the user-entered receipt date) now leads, with `id` as
> tiebreaker. Original wording preserved at the end.

FIFO consumption and batch listing order by `movementDate ASC, id ASC`. The `id` tiebreaker is
load-bearing, not decorative: purchase invoice movements are stamped
`receiptDate.atStartOfDay()`, so every batch received on the same day carries an identical
timestamp and would otherwise have no deterministic order.

**Why `movementDate` leads.** FIFO here models physical stock rotation — the oldest goods on the
shelf are used first. Ordering by registration sequence models when a clerk had time to type,
which is not a property of the stock. A delivery received on the 25th but entered on the 1st sat
in the freezer before goods received on the 31st, and must be consumed first.

**The rule governs future selection only; it never re-derives the past.** A batch registered
retroactively does not change consumption already recorded. If a newer batch was consumed before
an older one was entered, that consumption stands as posted, at the cost it was posted at. From
the moment the older batch exists, subsequent consumption draws from it first.

> **No reprocessing mechanism exists, and none is planned.** Recomputing a period's consumption
> would require reversing and replaying posted ledger rows, contradicting D4's append-only
> guarantee, D11's no-retroactive-COGS rule, and D89's "an error is corrected by counting again,
> never by erasing." It would also mutate the profit of a closed period, for a difference that
> is one of *timing* of cost recognition, not of total cost — and which self-cancels within days
> at restaurant turnover. The out-of-order consumption is a **data-entry consequence the user
> owns**; the remedy is entering receipts promptly. Explicitly rejected, not deferred. A
> non-blocking warning on the Purchase Invoice screen surfaces the situation at entry time.

**Consequence in reads (expected, not a defect).** The *set* of open batches is unchanged by this
rule — only their order is. But that order no longer matches registration sequence, so a newer
batch may sit partially consumed while an older, later-registered batch is still untouched.
Nothing downstream depends on a sequence assumption: average cost (D2) sums over the set, unpost
guards (D8) test one named batch's `remaining == original`, and purchase returns (D9) target an
explicit `sourceInvoiceLineId`. The batch list UI should surface `movementDate` alongside
quantity so the ordering reads as intentional rather than as a data error.

> **Prerequisite — null `movementDate` is a silent failure.** In PostgreSQL `ORDER BY ... ASC`
> places NULLs last, so a batch with no `movementDate` would become the last one ever consumed
> with no error raised. Every batch-opening path (purchase invoice post, physical count surplus,
> opening balance, purchase return restore) must populate it. Verify before relying on this
> ordering.

> **Original wording (superseded):** batches were ordered by creation order (`id` ascending)
> only, explicitly *not* `movementDate` — `StockBatchService.consumeFifo` →
> `findByStockBalanceIdAndStatusOrderByIdAsc`; `StockBalanceService.findBatchesForBalance` →
> `findByStockBalanceIdOrderByIdAsc`. The rationale was determinism and simplicity; the flaw was
> that it modelled entry sequence rather than physical rotation.

> **Build note.** Commits `085d3f0` (ordering + index), `1aa1930` (tests).
> FIFO consumption and batch listing both order `movementDate ASC, id ASC`
> (`StockBatchRepository`, `StockBatchService`, `StockBalanceService`). The invoice-unpost query
> deliberately keeps `id ASC` — it orders guard processing, not FIFO selection.
> `V36__stock_batch_movement_date_fifo.sql` replaces the FIFO index with
> `(stock_balance_id, status, movement_date, id)` and adds `(stock_balance_id, movement_date, id)`
> for listing.
>
> **Null risk confirmed closed.** `movement_date` is `NOT NULL`; the single batch-creating path
> copies the ledger movement date. Purchases use `receiptDate.atStartOfDay()`, count surpluses
> use the guarded non-null `countedAt`, purchase-return unpost reopens the original batch
> preserving its date. Live check: 18 rows, 0 nulls. `TRANSFER_IN` is recognized in the enum but
> has no implemented service path.
>
> **Opening balance has no true receipt date** and falls back to ledger record time. An opening
> balance entered today is therefore ordered as received today, and any invoice backdated before
> it consumes first. Accepted — no truer date exists — but recorded so it is not later
> mistaken for a defect.
>
> `StockBatchOrderingIntegrationTest` pins inverse date/id ordering, same-date id tiebreaking,
> retroactive-batch future-only consumption, and listing parity. 104 tests, 0 failures; no
> existing assertion needed changing.

### D11 — A FIFO shortfall is priced at the current (pre-movement) average cost; no retroactive COGS correction. ✅

When requested quantity exceeds total open-batch remaining, the unmatched remainder is valued at
`balance.getAverageCost()` (read before `applyMovement` re-derives the average).
`StockBatchService.consumeFifo` — `inventory/core/StockBatchService.java:206-214`. Nothing retroactively corrects prior
COGS.
> **Narrowed by D94.** This no longer applies to order consumption: a material whose open
> batches cannot cover the requested quantity is skipped entirely (doc → `PARTIAL`), so order
> consumption never drives the balance negative and never prices a shortfall at the average.
> The behaviour described here remains reachable only through paths that call `consumeFifo`
> directly without a pre-check — verify before relying on it.

### D12 — The backend emits `errorCode` + `params`; the

`message` field is logs-only and never shown to the user. ❌ (partial — migration incomplete)

The structured hierarchy is correct: `AppException(errorCode, debugMessage, params)` with status derived from the code
(`common/AppException.java`, `common/ErrorCode.java`); the FE
`translateApiError` renders from `errorCode` + `params` and treats `message` as “logs only, never rendered”
(`restaurant-saas-web/src/utils/errors.ts`). **But 7 inventory services still throw legacy
`ApiException(HttpStatus, message)` / deprecated `BusinessException(String)` with no `errorCode`/`params`** — a
user-facing message with nothing structured to translate:
`WasteService`, `UomService`, `InvoiceSequenceService`, `MaterialService`,
`MaterialCategoryService`, `SupplierService`, `WarehouseService`. Tracked in
[ROADMAP](ROADMAP.md) §4; listed as violations in [REVIEW](REVIEW.md).

### D13 — No premature abstraction (§1.4). ✅ (principle — no violations found)

The code consistently prefers the concrete: hand-written mappers (no MapStruct), one combined request DTO per resource
(no split Create*/Update* DTOs), no separate UOM-conversion table (`factorToBase` + `baseUom` self-ref only), and the
single documented `jsonb` exception (`waste_document` warnings) explicitly flagged as *not a precedent*. Reviewers
should block new abstractions introduced “for the future” without a second concrete caller.

### D14 — Menu module: product structure (standalone / parent-shell / variant-child); every

*orderable* product requires a Recipe.

Products fall into three roles, distinguished by a new nullable self-referencing
`Product.parentProductId`:

- **Standalone** (`parentProductId IS NULL`, no children point at it) — has its own `Recipe`, directly orderable. The
  default case; all existing products are this.
- **Variant child** (`parentProductId` set) — has its own `Recipe`, orderable. Carries
  `variantLabel`/`variantLabelAr` (nullable bilingual free text — "Large"/"كبير",
  "Coleslaw"/"كول سلو") shown as the selection chip.
- **Parent / variant-group shell** (`parentProductId IS NULL`, has children) — **has NO Recipe and is never orderable
  directly** (see carve-out below). Exists only to group its variants in the menu; tapping it in the cashier forces a
  variant pick.

**Recipe requirement (narrowed from the original blanket rule):** every product that can become an `OrderLine` —
standalone or variant child — must have a `Recipe` (BOM), even single-ingredient ones (bottled water, soda). This keeps
consumption/costing (D29) branch-free. The **only** exception is a parent shell, which is unreachable by the consumption
engine because it can never become an `OrderLine`. So the invariant the engine actually relies on —
"every product that becomes an OrderLine has a Recipe" — still holds with zero exceptions.
"Has a Recipe" and "is a parent" are therefore **mutually exclusive**.

**Parenthood is derived, never stored.** A product is a parent iff another product references it via `parentProductId`,
computed on the fly from the loaded product list — no stored
`isParent` column (a stored flag would need maintenance on every child create/delete/reassign and could drift; same
derive-don't-store rationale as D2). Variant delete and parent-reassign become non-events — the child rows are the sole
truth.

**Single-axis constraint (design boundary):** exactly ONE recipe-defining axis per parent (almost always size).
Multi-axis combinations are NOT enumerated as combination SKUs — an *additive* second choice (spicy, extra cheese) is an
add-on product on its own OrderLine (see below); a genuinely *different base recipe* (Chocolate vs Vanilla milkshake) is
a separate base product with its own size variants. This avoids the combinatorial explosion (3 sizes × 2 spice = N SKUs)
and the attribute-matrix model that would manage it. Rejected as premature (D13): the second recipe-defining axis was
searched for and does not exist as a real need.

**Add-ons.** An add-on (Extra Cheese, Extra Mushroom, Spicy, Jalapeño) is a normal product with its own `Recipe` — not a
modifier. A menu-side-only linking table `ProductAddOn (productId →
addOnProductId)` surfaces suggested add-ons as quick-add chips when `productId` is in the ticket; it has no runtime
effect on ordering/consumption. Add-ons attach to parent-eligible products only (`parentProductId IS NULL`). When
selected, an add-on becomes an ordinary new
`OrderLine` — own `recipeId`/`unitPrice`/`qty`, frozen like any other line (D21) — with **no**
`parentOrderLineId`/host-line link (confirmed not needed for receipt/kitchen). Zero change to
`OrderLine` schema or D28–D31/D29. Rejected alternative: a `ModifierGroup`/`ModifierOption`
structure with per-option price deltas and inline consumption — premature (D13).

**Enforcement (service layer):**

- Adding a parent product to an Order → `MenuErrorCode.PARENT_PRODUCT_NOT_ORDERABLE`.
- Creating/editing a Recipe on a product that has variant children →
  `MenuErrorCode.PARENT_PRODUCT_HAS_NO_RECIPE`.
- Linking a child to a product that already has its own Recipe →
  `MenuErrorCode.PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT` (the two roles are mutually exclusive; the transition must be
  explicit, not silent).

**Product editor — dynamic tabs (FE):** Standalone → *Recipe · Add-Ons*; Parent → *Variants · Add-Ons* (no Recipe tab);
Variant child → *Recipe* only. The Variants tab renders each child as an **inline accordion** row (label + price +
summary); expanding reveals that variant's recipe editor in place — no navigation, siblings editable in sequence. Tab
visibility driven by
`parentProductId` + the derived child-existence check.

### D15 — Menu module: no `Menu` entity in V1; tenant has a single implicit menu.

`MenuCategory` links directly to the tenant with no `Menu` layer on top. Revisit only when multi-menu (e.g. breakfast vs
dinner) becomes an actual requirement.

### D16 — Menu module: `Product`/`MenuCategory` are tenant-level only in V1; no `branch_id`.

Per-branch menu customization (different products per branch, different pricing per branch, or entirely separate branch
menus) is a real, expected future need — not hypothetical — but is explicitly deferred. Chosen approach when it's built:
an additive availability layer (e.g.
`product_branch_availability` table) on top of the existing tenant-level entities, not a structural change to `Product`/
`MenuCategory` themselves.

### D17 — Menu module: per-channel visibility (`isPOS`/`isDelivery`) deferred; `isMenu` (grid visibility) ships in V1.

Per- **channel** availability (`isPOS`, `isDelivery` — which sales channel may sell an item)
stays deferred; when built, boolean flags directly on `Product`, defaulting `true`.

Distinct from those and shipping now: `Product.isMenu` (boolean, default `true`) governs **main-menu-grid visibility**
(web catalog + cashier grid), not channel. Standalone and parent products default `true`; add-on products are `false`
(reachable only via a host's Add-Ons tab). **Hard rule (service layer):** a product with `parentProductId != NULL` must
have
`isMenu = false`; any attempt to set it `true` on a product with a parent is **rejected** with
`MenuErrorCode.VARIANT_CANNOT_BE_MENU_ITEM` — not silently coerced (explicit-error-over-silent-fix, per D6/D35). This is
the only stored constraint in the variant model; it lives on the child row, so no cross-row sync/drift. A product may be
both grid-visible and an add-on for another product (e.g. Coke): `isMenu = true` AND linked via `ProductAddOn` — the two
are independent.

### D18 — Menu module: `Recipe` is versioned and immutable; `RecipeItem` belongs to a `Recipe`, not directly to a

`Product`.

A `Product` has a history of `Recipe` versions; only one `Recipe` per product may be
`isActive = true` at a time, enforced at the service layer (not a DB partial unique index). Once created, a `Recipe` is
never edited — changing a recipe means deactivating the current one and creating a new active one. This exists
specifically so `OrderLine.recipeId` can freeze a reference at order-completion time that always resolves to the exact
ingredient list that was true at that moment, regardless of later recipe edits.

### D19 — Order module: the system never tracks the internal kitchen/cashier status cycle.

The system does not track Ordered → In Progress → Done. That cycle (and, internally, kitchen-display/cashier
communication) is fully owned by the POS. We only ever receive an order in a final state: `status: COMPLETE | CANCELLED`
(renamed from "PAID" to stay consistent with the DRAFT/COMPLETE/POSTED lifecycle used elsewhere). All
kitchen-performance metrics (time from order to done, etc.) are the POS's own responsibility — out of scope for us
entirely.

### D20 — Order module: cancellation carries a POS-supplied `cancellationStage`, never inferred.

Populated only when `status = CANCELLED`, sent as-is by the POS:
`BEFORE_KITCHEN | IN_KITCHEN_COOKED | IN_KITCHEN_NOT_COOKED | AFTER_DONE`. No separate audit field for "last kitchen
stage before cancel" — rejected as unnecessary complexity;
`cancellationStage` alone is sufficient since it's already the POS's authoritative decision. Maps directly to
consumption behavior: `COMPLETE` → sale consumption. `IN_KITCHEN_COOKED` /
`AFTER_DONE` → waste consumption. `BEFORE_KITCHEN` / `IN_KITCHEN_NOT_COOKED` → no consumption, order excluded from
`OrderConsumptionDoc` entirely.

### D21 — Order module: `OrderLine.recipeId` and `unitPrice` are frozen at "Complete Order on System" time.

Not at consumption time. `recipeId` freezing is what makes the versioned/immutable Recipe model (D18) actually work for
consumption accuracy. `unitPrice` is frozen at sale time so historical invoices stay correct even if the product's
selling price changes later.
> **Error code note**: order creation must reject a product with no active recipe using a
> **dedicated** error code (`PRODUCT_HAS_NO_ACTIVE_RECIPE`), not the Menu module's
> `RECIPE_NOT_FOUND`. The two situations need different user-facing treatment: in the Menu
> module, "no active recipe yet" is a normal, low-key empty state for a newly created product;
> in the Order module, it's a hard blocker preventing order creation. Sharing one error code
> would force one generic message to serve both an informational empty-state and an urgent
> validation failure — reject that; keep them as two separate codes even though the underlying
> condition (`Recipe` lookup returns none) is the same.

### D22 — Order module: dine-in + linked takeaway ("same customer, two invoices") is deferred.

Real-world need confirmed (e.g. service-charge differs between dine-in and takeaway, so they can't share one invoice),
but deferred as a Backlog item. V1: every order is fully independent — its own `Order`, its own invoice, no linking
mechanism between orders.

### D23 — Order module: `orderSource` distinguishes origin; every `Order` row is ultimately POS-completed. 🕓

> **Status: decided, not built — V2.** POS-sourced orders are live today. The ONLINE and
> AGGREGATOR branches of `orderSource`, `aggregatorName`, and the confirmation-step flow described
> below are design-complete but unimplemented, deferred to V2 alongside D24. The enum values exist;
> nothing produces them yet.

`orderSource: POS | ONLINE | AGGREGATOR`, plus a nullable `aggregatorName` populated only when
`orderSource = AGGREGATOR`. Direction of flow differs by source: POS-sourced orders originate at the POS itself (we only
receive the final result). ONLINE and AGGREGATOR orders originate outside the POS — received first as an
`IncomingOrderRequest`, forwarded to the POS (the
"Confirmation Step"), and the POS runs its full internal cycle before handing back a final order — exactly like a
POS-native order. This keeps `Order` pure and uniform regardless of origin.

### D24 — Order module: `IncomingOrderRequest` is a separate table with a one-directional link to `Order`. 🕓

> **Status: decided, not built — V2.** `IncomingOrderRequest` has no table, entity, or endpoint
> today. Deferred with D23. Design below stands as-is for whenever V2 intake is picked up.

Used only for online/aggregator intake before the POS has produced a final order. Keeps `Order`
uniform (no in-progress/intermediate rows) and avoids one row meaning two different things. The link is one-directional,
`IncomingOrderRequest → Order`, via a nullable `completedOrderId` FK —
`Order` has zero awareness of `IncomingOrderRequest`. `externalReferenceId` on
`IncomingOrderRequest` is for a *different* purpose (matching the aggregator's own order numbering for future
settlement/reconciliation) and is *not* used for the internal POS-linking mechanism. Internal linking mechanism: we
generate our own reference (`IncomingOrderRequest.id`), send it to the POS when forwarding (`SENT_TO_POS`), and require
the POS integration to echo it back with the final completed order — see O6.

### D25 — Order module: `paymentMethod` on `Order`; aggregator orders are treated as instantly settled for now.

`paymentMethod: CASH | CARD | WALLET | AGGREGATOR`. Aggregator orders are `status = COMPLETE`,
`paymentMethod = AGGREGATOR` immediately, even though real-world payout is batched/delayed (e.g. weekly). The gap
between "order complete" and "aggregator actually pays out" is deferred to a future separate accounting document
(Accounts Receivable per aggregator) — not modeled in the Order module itself.

### D26 — Order module: table management is entirely out of scope; `Order.tableNo` is a plain field.

> **Partially superseded by D76 (entity) and D81 (`table_id` FK).** The "no `RestaurantTable`
> entity / plain `tableNo` field" clauses no longer hold. The clause that still holds: no table
> status, reservation, merging, or real-time table map on our side — that stays POS-local.

Same principle as D19: table status/reservations/merging/real-time table map is a POS-native operational concern, not
something the system manages. `Order` just carries a simple `tableNo`
(nullable string/number, populated only when `orderType = DINE_IN`), sent as-is by the POS. No
`RestaurantTable` entity, no table status, no table-level business logic on our side.

### D27 — Order module: branch scoping — `Order.branchId` is direct; `OrderLine` inherits it;

`IncomingOrderRequest` differs by source.

`Order` carries `branchId` directly (in addition to inherited `tenantId`) — this determines which warehouse the order's
consumption is drawn from. `OrderLine` has no own `branchId`, it inherits from its parent `Order`. For
`IncomingOrderRequest`: **Online** (client has an integrated online store/POS) — branch is known upfront, `branchId`
populated at intake. **Aggregator** — not yet confirmed how their API communicates branch selection (see O7);
`branchId` may need to stay nullable at intake for this source until resolved.

### D28 — Order Consumption Doc: batching, locking, and the Doc/Line shape.

One `OrderConsumptionDoc` (header) per Scheduler run, per tenant. When the Scheduler picks up a
`PENDING` doc, it locks it (`status = IN_PROGRESS`) so no new orders attach mid-processing; any order arriving during
processing goes into a new/next `PENDING` doc, never one that's
`IN_PROGRESS`.

```
OrderConsumptionDoc
├── id
├── tenantId
├── status: PENDING | IN_PROGRESS | POSTED | CONFLICT   (COMPLETED renamed → POSTED by D42)
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
   WHERE doc_id = :docId GROUP BY recipe_id` — one row per distinct recipe actually sold in this run (bounded by
   distinct recipes, not order count), instead of loading every line into application memory.
2. **In-memory resolve**: fetch the distinct `Recipe`s from step 1 in one `IN (...)` query, loop over the small
   recipe-totals list (not the lines) to produce `materialId -> totalQty`.
3. **Per-material consumption**: loop over the aggregated materials (bounded by distinct materials touched, not order
   count), each wrapped in its own try/catch — FIFO batch consumption, `StockBalance` update, and `InventoryTransaction`
   recording happen here.

### D30 — Order Consumption Doc: failure handling is "full conflict", not partial.

`Material` is non-deletable and `Recipe` is immutable with a frozen `recipeId` per `OrderLine` — so failures at this
stage are expected to be systemic/technical (DB timeout, deadlock, constraint violation, unexpected negative-balance
edge case), not missing-reference data issues. Because of this, partial-success tracking per line isn't worth the added
complexity. If **any**
material fails to consume: the whole Doc goes to `CONFLICT`, **all** lines stay
`isConsumed = false` (no partial success), and `errorDetails` on the Doc header lists every material that failed and
why. If all materials consume successfully: Doc → `POSTED`, all lines → `isConsumed = true` in one bulk update.
> **Amended by D94, and again after it.** D30 was written on the assumption that a technical
> failure means nothing posted. The code has never worked that way: each material consumes in its
> own `REQUIRES_NEW` transaction and commits independently, so materials processed before a
> failure are already committed — and the loop continues past a failure, so materials after it
> commit too. Flipping every line to `isConsumed = false` therefore records a state that is
> simply false: the doc claims nothing moved while stock has moved.
>
> The rule is corrected, not relaxed. **What stays:** a doc with any technical failure is
> `CONFLICT`, `errorDetails` lists every failed material, and retry is a full re-run (D31).
> **What changes:** lines are marked by outcome — committed materials `true`, failed and
> unattempted materials `false` — exactly as `PARTIAL` already does (D94). The two paths share
> one marking mechanism.
>
> "No partial success" was never about line marking. It was about **retry granularity**: there is
> no selective re-processing of specific lines, and no per-line success counter driving one. That
> holds unchanged. Marking a line truthfully is bookkeeping, not partial retry — the per-material
> idempotency key is what makes the full re-run safe (D58), and it works off the ledger, not off
> `isConsumed`.

> **Build note (D30 amendment).** Commits `53659c1`, `dfd21a2`. The blanket
> `updateConsumedByDocId(docId, false)` is replaced by an `unconsumedMaterialIds` set that starts
> with every material and has successful **and idempotent-short-circuited** materials removed from
> it — so materials never attempted stay marked `false` without special handling, and a retry does
> not re-mark previously committed materials as unconsumed. `PARTIAL` and `CONFLICT` share one
> reset-and-mark block and one repository query; no logic was duplicated. 113 tests across
> order-consumption, waste, and physical count; existing `PARTIAL`, waste, and physical-count
> tests untouched.

### D31 — Order Consumption Doc: retry is a full re-run, not selective, and is a deliberate operator action.

The `errorDetails` array tells the operator exactly which material (s) failed and why (e.g. negative balance) — the
expected flow is: inspect the error, fix the underlying cause (e.g. enter the missing purchase invoice), *then* retry.
Retry re-runs the full D29 algorithm from scratch on the same Doc — no partial/selective re-processing of specific
lines, consistent with D30.

### D32 — Real-time stock display formula (reconfirmed across Inventory and Orders).

> **Superseded by D43** — the formula below subtracts both PENDING and IN_PROGRESS doc lines.
> D43 narrows the subtraction to PENDING only. The second half (`StockBalance` mutates only at
> POSTED) is unchanged and still current. Read D43 for the live rule.

`Displayed available qty = Current StockBalance − SUM(qty in all PENDING/IN_PROGRESS
OrderConsumptionDoc lines)`, computed on the fly. `StockBalance` itself is only updated when a Doc reaches `COMPLETED`.

### D33 — Device auth model: POS devices authenticate separately from cashier users via a one-time secret exchange.

A `Device` (tenant-owned, `device/` package) represents a physical POS terminal and is tied to exactly **one branch** at
creation (`Device.branch`, `@ManyToOne(optional = false)`). This is deliberately decoupled from `User`/`Employee` —
branch identity belongs to the **device**, not the cashier logging into it, so the same cashier can work any
device/branch without any User-side branch field, and no `Employee.branchId` duplication is introduced.

**Secret handling**: on `POST /api/devices`, a high-entropy random secret is generated and returned to the caller
**exactly once** in the create response; only its SHA-256 hash is persisted (`secretKeyHash`, unique-indexed) —
deterministic hashing is accepted here (unlike BCrypt for user passwords) because the secret is a generated high-entropy
token, not a guessable password.

**Login flow (MVP, not yet cryptographically bound)**: the device calls `POST
/api/devices/login` (no JWT — the secret itself is the credential) **once**, gets back `{
branchId, tenantId }`, and caches `branchId` locally. Every subsequent order-creation request sends the cached
`branchId` as a plain `X-Branch-Id` header — **not re-validated against the secret per request**. This is an accepted
MVP trade-off (see note below), not a full signed device-token design.
> Trade-off explicitly accepted: `X-Branch-Id` is trusted like `X-Tenant-Id` already is,
> without per-request cryptographic proof. Upgrading to a signed device JWT (branchId as a
> verified claim) is deferred until a real production POS integration needs it — tracked as a
> future hardening item, not blocking Orders module work. **This is the single home for that
> upgrade item — amend it here, not in D41.**

**Warehouse resolution**: `warehouseId` is never sent by the client — it's resolved server-side from `branchId` at
order-creation time (one warehouse per branch currently, no DB constraint enforcing it yet — see Roadmap follow-up).
Zero or multiple warehouses for a branch must fail loudly with a dedicated error code, never silently pick one. Full
detail in D41.

### D33b — RBAC: `UserRole` removed; `User` holds `roleId` and `branchId` directly.

Confirmed 1:1 user-to-role in V1 (no evidence of a real multi-role need), so the join table was unnecessary indirection.
`User.roleId` (FK, NOT NULL) and `User.branchId` (FK, NULLABLE)
replace it. `user_roles` table dropped via `V14__rbac_role_scoping.sql` (post-squash numbering).

### D34 — RBAC: `Role` is a global entity (`BaseEntity`, not `TenantAwareEntity`).

Same pattern as `Uom`/`MaterialCategory`. Tenants cannot create custom roles in V1 — only the sysadmin panel can
create/edit `Role` and `RolePermission`. All write endpoints for
`Role`/`RolePermission` live exclusively in the panel controllers, gated by
`@securityService.isSysAdmin()`. The tenant-facing RBAC controller exposes only
`GET /api/rbac/roles` (read-only, for the user-creation dropdown) — no write route exists there at all, not even
permission-gated.
> Deferred (not built): tenant-created custom roles. See O9.

### D35 — RBAC: `Role.isBranchScoped` gates whether `User.branchId` is required.

`Role` has `isBranchScoped: boolean` (default false), set only via the sysadmin panel. Validated at user create/update:
if the selected role has `isBranchScoped = true`,
`branchId` is required (`RbacErrorCode.BRANCH_REQUIRED_FOR_ROLE`); if false, `branchId`
must be null (`RbacErrorCode.BRANCH_NOT_ALLOWED_FOR_ROLE`). Roles are shared across branches (global), but a given
user's assignment to a branch-scoped role is tied to one specific branch.

### D36 — RBAC: `UserPermission` is a materialized snapshot, not a live composition with `RolePermission`.

At user creation, the new user's `RolePermission` set (for their assigned role) is copied into `UserPermission`. From
then on, `UserPermission` is the sole source of truth checked at runtime (`hasPermission()` queries `UserPermission`
directly, live, on every request — no JWT-embedded permission cache exists). Editing a user's permissions via the FE
checklist is a hard-delete-all + bulk-insert of the new full list — not a merge, not a grant/deny overlay. No
`type: GRANT|DENY` column exists or is needed, since there is no live role↔user composition to override.
> Consequence (accepted, confirmed): editing a role's default permissions from the sysadmin
> panel does NOT retroactively affect already-created users. Each user's permissions are
> independent once created. `POST /api/rbac/users/{id}/permissions/reset-to-role-defaults`
> (D37) is the explicit, manual mechanism to re-sync a user back to current role defaults.

### D37 — RBAC: explicit reset-to-role-defaults endpoint.

`POST /api/rbac/users/{id}/permissions/reset-to-role-defaults` re-reads current
`RolePermission` rows for the user's role and performs the same hard-delete + bulk-insert as the checklist edit,
sourcing from the role instead of the request body. Exists because of D36's snapshot behavior — this is the only way to
pull a drifted user back in sync.

### D38 — RBAC: default roles and their default permissions are seeded, not left empty.

A tenant's first users (e.g. the initial Cashier) must be usable immediately after signup without requiring sysadmin
panel intervention first. Seeded via dedicated, standalone migration files (not squashed into module migrations): a
permissions seed script, a default role→permission mapping seed script, and a sysadmin user seed script (password hash
preserved byte-for-byte across any migration squash — never regenerated). The seed is a baseline only — the sysadmin can
still edit `RolePermission` afterward via the panel; seeding does not lock the defaults.

### D40 — POS device login: gated by existing `SHIFTS_OPEN` permission + device/user branch match.

Login request accepts an optional `deviceId` (nullable — populated only by the POS client; web client login is
completely unaffected). When `deviceId` is present: (1) the authenticating user must hold the existing `SHIFTS_OPEN`
permission — used as a proxy for
"qualified to operate a POS device," not as an actual shift-open action, and deliberately reuses an existing permission
rather than adding a new one; (2) `Device` (looked up by
`deviceId`, never trusted from a raw client-supplied `branchId`) must have a `branch` that matches `user.branchId`
exactly, including the case where `user.branchId` is null (e.g. Owner/Accountant — non-branch-scoped roles are rejected
by the same mismatch check, no special-case exemption needed). Failing either check rejects the login before JWT
issuance, with structured `AuthErrorCode` (`DEVICE_NOT_FOUND` / `DEVICE_BRANCH_MISMATCH` / permission failure). When
`deviceId` is absent, login proceeds exactly as before with zero new checks.

### D41 — Order-time warehouse resolution from `X-Branch-Id` (complements D33/D35/D40, does not replace them).

Device identity, the one-branch binding, and the one-time secret exchange (generation, SHA-256
`secretKeyHash`, deterministic-hash rationale, `POST /api/devices/login`) are defined in **D33**
and are not restated here. D40 governs **who may log in on which device**; this decision governs **which warehouse an
order's consumption is drawn from** once that login has already succeeded.

**Resolution rule.** `warehouseId` is never sent by the client. Every order-creation request (`POST /api/orders`)
carries the device's cached `branchId` as a plain `X-Branch-Id` header, and
`OrderService.resolveWarehouseForBranch` resolves the warehouse server-side from it at order-creation time. One active
warehouse per branch is assumed (no DB constraint yet — see ROADMAP). Zero or multiple matches fail loudly with
`WAREHOUSE_NOT_FOUND` /
`AMBIGUOUS_WAREHOUSE_FOR_BRANCH` — never silently pick one.

**Independent of the user's own branch.** This resolution reads `X-Branch-Id` only. It does not consult the
authenticated user's `branchId`, and does not re-run D40's device/user branch-match check — that check already ran once,
at login, and is not repeated per request.

> Trade-off inherited from D33: `X-Branch-Id` is trusted like `X-Tenant-Id` already is, without
> per-request cryptographic proof. The signed-device-JWT upgrade is tracked once, in D33 — amend
> it there, not here.

### D41b — Order module: Order status is COMPLETE (reconfirmed, not PAID).

Reconfirms D19 as-is. No naming change — `status: COMPLETE | CANCELLED` stands.

### D42 — Order Consumption Doc: status enum is `PENDING | IN_PROGRESS | POSTED | CONFLICT`.

Renames D28's `COMPLETED` → `POSTED` for consistency with the DRAFT/COMPLETE/POSTED lifecycle vocabulary used elsewhere
(PurchaseInvoice, PhysicalCount, Waste). No other naming change — the entry/open status is `PENDING` (not `DRAFT`),
matching D28's original naming. Doc/Line shape (D28) and the 3-step aggregation algorithm (D29) are unaffected.

### D43 — Real-time stock balance excludes IN_PROGRESS doc lines; PENDING only.

Supersedes D32's formula. Reconfirmed scope:
Displayed available qty = Current StockBalance − SUM (qty in PENDING-status OrderConsumptionDoc lines only)
`IN_PROGRESS` is deliberately excluded from the subtraction — the processing window is short enough that the resulting
stock-balance blip is accepted as a known trade-off, rather than adding IN_PROGRESS to the query. `StockBalance` itself
is still only mutated when a Doc reaches `POSTED` (D32's second half unchanged).

### D44 — Order Consumption Doc: concurrent order writes require a lock at check-then-create.

Two orders arriving in the same instant on the same warehouse must not each create their own `PENDING` Doc. The "find
PENDING doc for this warehouse, else create one" step must be guarded — either a unique constraint on
`(tenant_id, warehouse_id) WHERE status = 'PENDING'`
or a pessimistic lock (`SELECT ... FOR UPDATE`) around the check-then-create — so concurrent orders land on the same Doc
rather than racing into duplicate Docs.

### D45 — Order Consumption Doc: temporary direct-write recalculate button, bypassing OrderConsumptionEvent.

> **Superseded by D58** — the OrderConsumptionEvent-based design described below was replaced
> before implementation. See D58 for the design actually built. Testing-phase mechanism only, not the target design. A
> manual "recalculate" button on the Doc header writes order-line consumption directly into the existing `PENDING`/
> `IN_PROGRESS`
> `OrderConsumptionDoc` (find-or-create per warehouse, per D44's locking), skipping
> `OrderConsumptionEvent` entirely. Button placement and enablement:

- **Now (testing)**: enabled in all Doc states.
- **Later**: enabled only when Doc status = `CONFLICT` (i.e. becomes the retry trigger for D31's "fix the cause, then
  retry" flow). Failure/success semantics stay exactly as D30 (material-level `errorDetails`, full-Doc
  `CONFLICT` on any material failure, no per-order or per-line counters). This button and its direct-write path are
  explicitly interim — `OrderConsumptionEvent` + the scheduled aggregation job (ROADMAP §1, "Hybrid Ledger") remain the
  target design and are still to be built.

### D46 — Fixed Assets: `Asset` (header) →

`AssetLine` (per-purchase-batch) hierarchy; disposal/maintenance target a specific line, chosen manually — never FIFO.

An `Asset` represents a purchased item *type* (e.g. "Wood Chair", "Oven"); each purchase event is a separate `AssetLine`
under it (its own `quantity`/`unitCost`/`purchaseDate`), because the same asset type is commonly bought at different
prices over time and the client needs to track that distinctly — the same reason `StockBatch` exists under a `Material`.
This hierarchy also covers large single-unit equipment (ovens, grills, AC units): each physical unit is its own
`AssetLine` (typically `quantity = 1`) under a shared `Asset` header (e.g. "Oven"), which gives aggregate investment
totals per asset type for free without a separate entity per physical unit.

Unlike Inventory's FIFO consumption (D10), disposal/maintenance always target an explicit
`assetLineId` chosen by the caller — no automatic oldest/cheapest/average selection logic exists in the backend. This is
a deliberate divergence from the ledger's FIFO pattern, not an oversight: the user is expected to know and choose which
purchase batch/unit is affected, and bears responsibility for that choice.

`AssetLine` also carries an optional `label` (free text) — primarily useful when a line represents a single trackable
unit (`quantity = 1`, e.g. one specific oven/grill) so it can be identified distinctly in maintenance/disposal history
(e.g. "Oven — North Kitchen", "OVN-01"). Not required, and not tied to any quantity constraint — the backend imposes no
rule linking
`label` presence to `quantity`.

### D47 — Fixed Assets: category is a fixed backend enum, not a tenant-configurable table.

`category: FURNITURE | KITCHEN_EQUIPMENT | FINISHING | ELECTRONICS | OTHER` on `Asset`. No evidence tenants need custom
categories (D13) — revisit only if a real need surfaces.

### D48 — Fixed Assets: `AssetDisposal` reduces

`AssetLine.remainingQuantity`; status is derived, not stored authoritatively per write.

`AssetLine.status` (ACTIVE | PARTIALLY_DISPOSED | FULLY_DISPOSED) is derived from
`remainingQuantity` vs `quantity`. `Asset.status` (header) is derived from the aggregate state of its lines.
`quantityDisposed` on a single `AssetDisposal` is capped at the target line's current `remainingQuantity` — never
allowed to go negative.

### D49 — Fixed Assets: `AssetMaintenance` is a cost record only; never affects `quantity`/`remainingQuantity`.

Maintenance is scoped to `assetLineId`, expected in practice mostly on large single-unit equipment (`quantity = 1`) —
kitchen equipment, AC units, ovens. Small multi-quantity assets (chairs, small tools) are, in practice, disposed and
replaced rather than repaired; no per-sub-unit maintenance tracking exists or is needed for V1.

### D50 — Fixed Assets: no delete after first disposal/maintenance; no profit-coverage report in V1.

Delete allowed only when the `AssetLine` has zero `AssetDisposal`/`AssetMaintenance` records (mirrors D6's dual-check
spirit — existence of child records blocks delete, no status gate needed since there's no DRAFT/POSTED lifecycle here).
V1 reporting is limited to total asset value and a disposal list (date/reason/value) — **no** cost-coverage/ROI
percentage, since that requires the not-yet-built P&L/accounting module. Tracked as **O10** below.

### D51 — Fixed Assets: disposal/maintenance requests carry both `assetId` and `assetLineId`, validated as a pair.

Both `CreateAssetDisposalRequest` and `CreateAssetMaintenanceRequest` include `assetId`
alongside `assetLineId`, even though `assetLineId` alone is technically sufficient to resolve the record (an `AssetLine`
already has a non-nullable `assetId` FK). This mirrors the two-step selection the UI walks the user through (pick
`Asset` → pick `AssetLine`) so the request body reflects exactly what was chosen, rather than silently dropping the
first selection. The service layer validates `AssetLine.assetId == request.assetId`, rejecting mismatches with a
dedicated
`AssetErrorCode` (`LINE_ASSET_MISMATCH`) instead of trusting `assetLineId` alone. Read endpoints follow the same
nesting: `GET /api/assets/{assetId}/lines/{lineId}/disposals` and
`.../maintenance`, not a flatter `/api/assets/lines/{lineId}/...` shape considered earlier.

### D52 — Fixed Assets: `ASSETS_VIEW` and `ASSETS_MANAGE` are separate permissions.

Read endpoints (`GET /api/assets/**`, including the two report endpoints) are gated by
`ASSETS_VIEW`; write endpoints (create/update/delete on `Asset`/`AssetLine`, and creating
`AssetDisposal`/`AssetMaintenance`) remain gated by `ASSETS_MANAGE`, matching the read/write permission split already
used in other modules. This supersedes the initial backend pass, which temporarily reused `ASSETS_MANAGE` on GETs (a
documented judgment call, made because the implementation prompt only specified writes) — that was a stopgap, not a
decision to build on.

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

### D53 — Loyalty V1 scope:

`Customer` is name + phone only; points, offers, change-approval, and reporting are all deferred.

V1 delivers only the base link between a `Customer` and their `Order`s — "I know who bought what." Everything else
discussed for Loyalty (points/earn-redeem, expiry, offers/promotions, a Change Request/approval workflow for editing
customer data, and any spend/visit reporting) is explicitly **out of scope for this pass** and tracked separately (see
[ROADMAP](ROADMAP.md)). No schema, endpoint, or permission for any of those should be built now — adding them "for
later" would violate D13.

### D54 — Loyalty: `Customer` is tenant-scoped; identified by phone; only `name` + `phone` + audit columns.

`Customer extends TenantAwareEntity` (same pattern as other tenant-owned rows — non-null
`tenantId` + audit). Fields: `name`, `phone`. No email, address, birthdate, or any other profile field in V1 — add only
when a concrete need surfaces (D13). `UNIQUE (tenant_id,
phone)` — enforced at the DB level; a duplicate phone within a tenant always resolves to the existing `Customer` row,
never a second row. Registration happens exclusively from the POS at first-order time (staff asks for phone, and name if
new) — there is no separate self-registration surface in V1.

### D55 — Loyalty: `Order` carries raw `customerPhone` (+`customerName` if new); backend does find-or-create;

`Order.customerId` is nullable.

The order-creation request never sends a `customerId`. It sends `customerPhone` (required whenever a customer is being
linked) and `customerName` (present only the first time that phone is seen). `OrderService` resolves this via a `CustomerService.findOrCreate(tenantId,
phone, name)` call before persisting the order — mirrors the "raw entered value in, single resolution point inside the
service" shape used by `InventoryLedgerService.record()` (D3), applied here to customer identity instead of stock
quantity.
`find-or-create` concurrency: rely on the `UNIQUE(tenant_id, phone)` constraint as the real guard — attempt insert,
catch `DataIntegrityViolationException`, re-select on conflict (same idempotency-guard shape as `IdempotencyService`,
not a new pattern).
`Order.customerId` is a **nullable** FK. If `customerPhone` is absent, or the find-or-create call fails for any reason,
order creation must still succeed with `customerId = null` — the order is never blocked by a Loyalty-side failure. The
link, when present, is set once at order-creation time only; nothing retroactively attaches a customer to an
already-created order in V1. Conflict rule when the same phone is registered concurrently with two different names (e.g.
two offline devices): **first write to reach the server wins the name**; a later create attempt that hits the unique
constraint simply resolves to the existing row and discards the incoming name. No automatic overwrite — mutating an
existing customer's name is out of scope until the Change Request workflow (D53) is built.

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

### D56 — Loyalty: POS keeps a full local

`(id, name, phone)` customer list, synced at login/shift-open; no live per-keystroke lookup.

To support the intended staff flow (customer states their phone, cashier's screen shows their name immediately), the POS
pulls the tenant's full customer list once at login/shift-open (`GET /api/loyalty/customers`, mirroring the
branch/warehouse caching shape already established for devices in D33/D41) and matches locally as the phone is typed —
no per-keystroke server round trip. A newly-registered customer (via the in-order-screen
"new customer" popup) is appended to the local list immediately after the create call succeeds, so it's available for
the rest of the shift without a re-sync. Keeping the list fresh across devices/shifts (periodic refresh vs. next login
only) is not addressed further in V1 — login/shift-open refresh is the only sync point; a real delta-sync mechanism is
deferred (D13 — no abstraction ahead of a second concrete driver).

### D57 — Loyalty: offline customer registration principle (mechanism deferred to the general Offline capability work).

Only the **principle** is decided now, not the mechanism: when the Orders module's offline capability (ROADMAP §1) is
eventually built, customer registrations in the offline queue must always be synced to the server **before** the orders
that reference them, for any given device's queue. This is recorded now so the ordering constraint isn't lost, but the
queue data structure, retry/backoff, and conflict resolution are explicitly OPEN — see
[ROADMAP](ROADMAP.md) and do not build against this decision until the Offline capability itself is designed.

### D58 — Order Consumption: dual-trigger batching scheduler (count + age), no

`OrderConsumptionEvent` layer; per-doc locking; system-attributed batches.

Supersedes D45's "target design" framing (OrderConsumptionEvent + scheduled aggregation job). Implemented instead: the
existing `OrderConsumptionDoc`/`OrderConsumptionDocLine` shape (D28)
**is** the staging layer — no separate event/staging table. `OrderConsumptionEvent` and
`IdempotencyScope.ORDER_CONSUMPTION_EVENT` are deleted (unused scaffolding, D13 — dead abstractions are removed once
confirmed unnecessary, not left in place "for later").

**Trigger (resolves the prior grouping question, pragmatically, without depending on a POS shift-close signal):**
`OrderConsumptionBatchingScheduler` polls on a short interval (default 60s, configurable) and selects PENDING docs ready
for batching, firing per warehouse when **either**:

- accumulated unprocessed `OrderConsumptionDocLine` count reaches **50**, OR
- the oldest unprocessed line for that warehouse exceeds **8 hours** old

whichever comes first. Both thresholds are externalized config (`order-consumption.batching.threshold-count` /
`.max-age`), not hardcoded — tunable operationally, not an architectural constant. The 8-hour ceiling approximates a
shift-length batch without requiring an actual POS "shift closed" signal, which doesn't exist and isn't designed (O6
still open). A fixed-interval-only cron was explicitly rejected: it would run during high-server-load periods regardless
of whether there's anything to do, adding load exactly when it's least wanted; the count/age dual trigger means run
frequency scales with actual order volume instead.

**Multi-instance safety — ShedLock, not a bare `@Scheduled`.** The scheduler is annotated with ShedLock
(`@SchedulerLock`), not plain Spring `@Scheduled`, because more than one app instance may run this poll loop
concurrently and a bare `@Scheduled` would double-fire across instances. **Locking is per-doc, not one lock for the
entire poll cycle** — a single global lock risks
`lockAtMostFor` expiring mid-batch if several warehouses cross threshold in the same tick or one doc's consumption is
heavy (consumption is expensive by design, per D29's per-material FIFO work), which would let a second instance
double-process. Per-doc locking uses ShedLock's programmatic `LockingTaskExecutor` API with a dynamic lock name (`"orderConsumptionBatching:" +
docId`), not the static-string `@SchedulerLock` annotation, since the annotation's `name` isn't suited to a
per-iteration dynamic value.

**Two-transaction split (the correctness-critical part).** Claiming a doc — find-or-create per warehouse (D44's existing
lock/constraint), bulk-insert `OrderConsumptionDocLine` rows, set
`status = IN_PROGRESS` — **commits in its own short transaction**, separate from and *before*
the D29 3-step consumption run. Reason: if both happened in one transaction, a new order arriving mid-processing
wouldn't see `IN_PROGRESS` yet (not committed) and could wrongly attach to the doc currently being processed — exactly
what D28's locking exists to prevent. D29's processing transaction is otherwise unchanged.

**Duplicate-line guard: DB unique constraint, not an idempotency key.** Since
`OrderConsumptionDocLine.orderLineId` is already a natural unique key (one `OrderLine` can only ever produce one doc
line), no generated idempotency key or `IdempotencyService` involvement is needed — a plain `UNIQUE(order_line_id)`
constraint (`uk_order_consumption_doc_line_order_line_id`)
is the actual guard, with `DataIntegrityViolationException` caught on insert as the retry-safety net, matching the
"constraint is the real guard" pattern used elsewhere (`IdempotencyService`, D9's return-batch restore).

**No `userId` on system-batched docs.** A batching run spans many orders from potentially many different users/shifts —
there's no single accountable actor to attribute the doc or its
`updatedBy` to, so it's `null`. This is acceptable because traceability is preserved at the source: each original
`Order` retains its own creator. (Requires `updatedBy` — or whichever audit column `OrderConsumptionDoc` uses — to be
nullable at the DB level; confirmed as part of this pass, not assumed.)

**`tenantId` sourced from the doc, not request context.** The scheduler is inherently cross-tenant (it batches every
tenant's warehouses in one poll cycle, unlike a normal request which is scoped by `X-Tenant-Id`).
`processDocConsumption(...)` reads `tenantId =
doc.getTenantId()` explicitly and threads it through every downstream call (`aggregateMaterialConsumptions`,
`recordConsumption` → `LedgerCommand`) rather than relying on any request-scoped/ThreadLocal tenant context, which would
not exist on a scheduler thread.

**D45's manual button, final scope.** Confirmed as originally specified in D45's "Later" bullet:
enabled **only when Doc status = CONFLICT**, running a full D29 re-run per D31 (no partial/selective retry). No
auto-retry exists or is planned for CONFLICT docs (D31 stands unchanged) — the scheduler above only ever
creates/advances PENDING docs, never touches a CONFLICT one.

### D58b — Cashier POS: platform architecture is one shared React/TS core with two native shells; not a web app/PWA.

Environment doesn't support a plain browser-based deployment. One shared core (React/TS, same stack as the rest of the
frontend) targets two build outputs: **Electron** for Windows PC, **Capacitor** for Android tablet. Chosen over React
Native specifically to avoid maintaining a second UI codebase (D13) — Capacitor wraps the existing React/TS UI in a
native shell rather than requiring a rewrite. The only platform-specific code is the printer adapter (D59); all screens,
business logic, and styling are shared.

### D59 — Cashier POS: printing via an

`IPrinterAdapter` interface, two independent print jobs (kitchen + receipt), single kitchen printer for V1.

Neither Electron nor Capacitor can share one printing implementation — Windows needs USB/Serial access
(`node-thermal-printer`/`escpos`-style), Android needs native Bluetooth/USB. One interface (`printKitchenTicket()` /
`printReceipt()`), two platform implementations swapped at build time, not runtime feature-detection. Kitchen ticket
fires on
`SENT_TO_KITCHEN` (D61); receipt fires on `PAID/COMPLETE`. No per-station kitchen routing in V1 (single kitchen
printer) — revisit only if multi-station printing becomes a real need.

### D60 — Cashier POS: independent design system, separate repo, no shared tokens with `restaurant-saas-web`.

New standalone repo. Own `--color-*` token set (from the Claude Design output), not inherited from the admin app's
palette — justified because the two apps solve different problems (glanceable fast-recognition status UI vs. a calm
admin dashboard) and never render inside the same runtime, so no visual-consistency risk. Org-wide conventions still
apply regardless of palette: React/TS, plain CSS + BEM, Lucide outline icons, `useTranslation()`, RTL-safe layout.

### D61 — Cashier POS: local ticket lifecycle is POS-owned; backend never sees intermediate states. (Reaffirms D19.)

The POS owns the entire operational cycle of a ticket end to end: open, add/void lines, send to kitchen, merge/split,
hold, pay or cancel. None of these intermediate states are transmitted, mirrored, or queryable on the backend. The
backend's only view of a ticket is the single final
`Order` payload submitted at completion or cancellation (`status: COMPLETE | CANCELLED`, D19/D41b), carrying
`cancellationStage` when cancelled (D20). No polling, no partial-state endpoint, no kitchen-status column exists or is
planned on our side.

**Table lifecycle follows the same split — identity is ours, operations are the POS's.** Table identity and layout are
backend master data (`RestaurantTable` / `TableSection`, D76–D78): created and edited in the admin web app, synced down
to the POS with `name`, `capacity`, `sectionId`,
`shape`, `posX`/`posY`/`rotation`. Everything operational on top of that data — occupancy, seating, merge (D63),
reservation — is POS-local, derived from its own `open_ticket` state (D80), never written back. The backend learns which
table an order belonged to exactly once, as `Order.tableId`
on the final payload (D81).

### D62 — Cashier POS: table *identity* is backend master data; table *occupancy/actions* stay POS-local. (Refines D26.)

> **Superseded by D76/D77.** This decision's `Table` entity was decided but never built. The
> RestaurantTable/TableSection design (D76–D80: branch+section master data, layout canvas,
> POS grid + spatial view) subsumes it — same split (identity is backend master data,
> occupancy stays POS-local) at a level of detail D62 didn't specify (no section concept, no
> layout canvas existed at D62's time). Read D76–D80 as the design actually built; D62 stands
> only as the historical record of the identity/occupancy split being agreed first.

### D63 — Cashier POS: table merge produces one combined `Order` at completion; backend has no concept of a merge.

> **Note**: this decision's wording ("tagged with the primary table's `tableNo`") predates
> D81's `Order.tableId` FK. Read `tableNo` here as `tableId` — the merge behavior (combine into
> one Order payload, tagged with the primary table) is unchanged, only the field type is.

Merging Table A + Table B combines their local tickets into one before send/complete — the secondary ticket is absorbed
and locally closed. Only one `Order` payload ever reaches the backend, tagged with the primary table. No merge-aware
backend logic exists or is needed.

### D63b — Cashier POS: exactly one cashier device operates per branch at any time.

Confirmed assumption underlying D62 and the lack of any concurrent-device coordination layer. No multi-device
table/order-state sync is designed or built. If a branch ever needs two simultaneous devices, D62's POS-local occupancy
state and D61's local ticket lifecycle both need to be revisited.

### D64 — Cashier POS: `Shift` is scoped per cashier user (not device, not branch); new backend entity + X/Z reporting.

Distinct from any HR scheduling concept — confirmed no HR `Shift` exists, no naming collision. New entity: `Shift(id, tenantId, branchId, cashierUserId, openedAt, closedAt, openingCash,
closingCashCounted, status: OPEN | CLOSED)`. `Order.shiftId` is an explicit FK set at order-creation time (not inferred
from a time window), so reporting stays correct once offline sync (D65) can introduce late-arriving orders later. **X
report**: live, non-destructive aggregation over the current `OPEN` shift's orders. **Z report**: closes the shift,
records
`closingCashCounted` vs. expected, immutable after close. Reuses the existing `SHIFTS_OPEN`
permission (already referenced by D40) rather than adding a new one — confirmed that permission was seeded for exactly
this purpose.

### D65 — Cashier POS: offline order creation is deferred; this implementation pass is online-only.

Order creation is a normal synchronous `POST` with standard error handling — no local queue, no retry/dedup logic yet.
Offline capability (local queue, idempotent backend intake, conflict resolution against table/shift state) remains an
open ROADMAP item, to be scoped as its own follow-up once the online path is stable.

### D66 — Cashier POS: layout is landscape-only, responsive from a 1280×800 floor, no breakpoint-specific components.

No portrait variant. Same component tree scales from the 1280×800 floor up through wider desktop monitors via CSS Grid
`auto-fill`/`minmax()` — no platform-detection branching or separate tablet/desktop UI variants. Electron window
enforces a locked minimum size of 1280×800 so the app can never fall below the tested floor.

### D67 — Cashier POS: minimum touch target 48px (56–64px on primary actions), uniform across PC and tablet.

Confirmed PC stations use mouse + keyboard, not touchscreen — sizing is kept uniform anyway for a consistent feel and
because momentum/speed matters on both. Applies to all interactive elements: buttons, cards, status transitions, reprint
actions.

### D68 — Cashier POS: numeric-only high-frequency inputs use a custom keypad component; free-text low-frequency inputs use the OS keyboard.

Customer phone lookup and order-number search (History) use a shared custom numeric keypad component (same visual
language as the X/Z cash-counting field) — identical behavior on PC and tablet, not dependent on an OS on-screen
keyboard. Product search (New Order screen) stays a standard text input relying on the native OS/on-screen keyboard —
occasional use doesn't justify a custom full keyboard build (D13).

### D69 — Cashier POS: History screen uses a card-list, not a data table.

Matches the Orders board's card visual pattern instead of a dense multi-column table, so the reprint action has an
adequately-sized, touch-friendly tap zone. Same component reflows wider on desktop rather than switching to a different
table-based layout at larger widths (D66).

### D70 — Cashier POS: rebuilt from the existing POS Simulator repo via an audit-first infra/UI split.

`apiClient.ts`, `storage.ts`, `tickets.ts`, `types.ts` retained as the infrastructure/service layer (device/cashier
auth, backend calls, persistence) — confirmed clean, no rendering logic mixed in (`DeviceSetup.tsx`/`CashierLogin.tsx`
are thin form wrappers only). `App.tsx`, all CSS,
`screens/*`, `Icons.tsx` replaced wholesale via the Claude Design handoff. Confirmed
`warehouseId` has no client-side resolution/caching anywhere in the repo — this is correct per D41 (server-resolved
per-request, only ever echoed back in `OrderResponse`), not a gap to fill.

### D71 — Cashier POS: UI ported faithfully first (mock data, local state, no backend calls); backend wiring is a separate second pass.

Chosen over wiring to `apiClient.ts` during the same pass so the port could first preserve the cashier workflow, visual
hierarchy, and interaction model without mixing UI replacement with integration risk. Backend wiring is intentionally a
separate pass against the retained
`apiClient.ts`/service layer.

### D72 — Order submission idempotency & POS local sync. ✅

**Idempotency**: `orders.idempotency_key` (unique constraint, migration `V24`) — NOT routed through Inventory's
`IdempotencyService`/`IdempotencyScope`, deliberately: that service lives in
`inventory/core/`, package-coupled to inventory concerns, not a generic cross-module utility. Reusing it from Orders
would be the wrong kind of coupling (D13) — a direct column + constraint on `orders` is the correct concrete solution
for this module.

Key is generated **client-side, once, at local order-completion time**, resent unchanged on every retry — load-bearing,
not incidental: the backend cannot distinguish a first submission from a retry-after-timeout, only the client knows
which attempt this is.

Race handling: persistence must happen inside the `DataIntegrityViolationException` catch boundary (for example
`saveAndFlush`, or `save` plus an explicit flush) so a deferred unique constraint violation is caught before transaction
commit. On conflict, the service re-resolves via `findByTenantIdAndIdempotencyKey(...)` and returns the winning order's
response. The DB constraint is the real guard, not check-then-insert (same spirit as D44).

### D73 — Shift-resume client-side. ✅

Backend shift-open enrichment remains unchanged. Client-side, confirmed built:

- **Local `shift` table** — single-row (`CHECK(id=1)` upsert, same pattern as `device_auth`),
  `shiftRepo.ts` (`getShift`/`saveShift`/`clearShift`), wired through `worker.ts`'s registry and a `dbShift` proxy in
  `client.ts`.
- **`SHIFT_ALREADY_OPEN` handling** — `shiftResume.ts`'s `parseShiftAlreadyOpen` checks
  `err instanceof ApiError && err.errorCode === 'SHIFT_ALREADY_OPEN'` against the existing
  `ApiError`/`params` shape (no new error-parsing path). `openShift()` branches on a match:
  writes the local `shift` row, sets `currentShiftId`/`screen: 'order'`/`loggedIn: true` (reuses existing
  `currentShiftId`, no parallel field), returns without touching `shiftError`. Any other error falls through to the
  original generic path unchanged.
- **Single entry point, confirmed not two**: `initialState()` resets `session: null,
  loggedIn: false` on every reload regardless of cached state, so `openShift()`'s catch block is the only path that ever
  needs `SHIFT_ALREADY_OPEN` handling — no separate boot-time local-shift check was added, since nothing in the app
  would reach it.
- **Unsynced-order visibility**: `getOrdersByShiftId` (already-shipped) queried on resume, exposed as
  `unsyncedOrderCount`, surfaced in the X-Report card on the existing `ShiftClose.tsx`
  screen — the only existing session/shift UI surface; no dedicated resume screen was invented. **Explicitly decided (
  not a gap)**: this count is not shown immediately on resume/order-screen, only at shift-close — acceptable for now,
  revisit only if a real need surfaces (D13).
- **Boundary respected**: `shift_id` usage here is strictly read-only display; sync eligibility/retry logic untouched.

16 new tests (45 total): `shiftResume.test.ts`, `shiftRepo.test.ts`,
`usePos.shiftResume.test.ts` (`@testing-library/react`'s `renderHook`, new devDependency, first hook-level test in the
repo). `tsc -b && vite build` clean.

> **Known limitation, not a bug in this work**: `unsyncedOrderCount` will read 0 for most crashes
> today, because live order creation (`confirmPay`/`cancelOrder`) does not yet write to
> `local_order` — see the in-progress outbox-wiring task. This resume mechanism is correct and
> fully tested against the table it queries; the table simply isn't populated by production
> traffic yet. Do not treat a 0 count as a regression until that lands.

### D74 — POS: `orderNo` is a locally-generated, per-device incrementing counter stored in SQLite.

Each POS device maintains its own local counter in its SQLite store and increments it to produce `orderNo` — no
coordination with the backend or other devices. This is a deliberate consequence of the POS being designed as a
standalone system that only communicates with the main system on order completion (ROADMAP §1); a shared/branch-level
counter would require the device to be online and call the backend for the next sequence value, which contradicts that
design.

**Uniqueness scope**: `orderNo` is guaranteed unique per device only, not per branch or tenant. Today this holds in
practice because exactly one POS device is provisioned per branch — not because of any dedup mechanism. If a branch is
ever given a second device, two devices will independently produce colliding `orderNo` values (e.g. both emitting
"1044"), since each counter starts and increments in isolation.

**Format**: plain incrementing integer (no device prefix), per D13 — the multi-device collision case is not a real need
today, so no prefixing scheme is built preemptively.

**Known future migration cost (accepted trade-off)**: if/when a branch gets a second POS device, `orderNo` uniqueness
will need to be revisited — most likely a device-prefixed format (e.g. `D1-1044`). At that point, historical orders
already using the plain-integer scheme stay as-is; only new orders shift to the new format. Not blocking current work —
tracked here so it isn't rediscovered as a surprise bug later.

**Backend implication**: the backend does not validate, correct, or dedupe `orderNo` — it is stored and displayed as
received from the POS, exactly like `orderSource`/`cancellationStage`
and other POS-authoritative fields (D19, D20).

### D75 — Entity codes for tenant Material, MaterialCategory, Supplier, Warehouse, Employee, and Job are backend-generated.

Entity `code` values for tenant-created `Material`, tenant-created `MaterialCategory`,
`Supplier`, `Warehouse`, `Employee`, and HR job-title `Job` are assigned by the backend at creation time. Clients do not
provide codes on create. Update endpoints treat `code` as a read-only transition field: if a client still sends it, the
backend ignores it and preserves the existing stored code.

**Scope boundaries:**

- Tenant-created `MaterialCategory` only (`tenant_id IS NOT NULL`). Global material categories (`tenant_id IS NULL`) and
  their sysadmin-panel code entry remain unchanged.
- `MaterialCatalog` is global catalog data and is not renumbered or backfilled.
- Existing rows keep their current codes; this decision does not require a backfill.
- The affected `Job` is the tenant HR job-title entity exposed by `job/`, not background job scheduling support.

**Format:** `{PREFIX}-{NNNN}`, with a 4-digit zero-padded sequence such as `EDR-MAT-0001`,
`EDR-CAT-0001`, `EDR-SUP-0001`, `EDR-WH-0001`, `EDR-EMP-0001`, and `EDR-JOB-0001`. The sequence grows naturally past
four digits (`10000`) and is never clamped or wrapped.

**Counter scope:** independent per tenant and per entity type. Each entity type has its own counter for a tenant, so
Material and MaterialCategory numbers never share or interleave.

**Mechanism:** reuse the existing per-tenant sequence-counter pattern used by purchase invoice numbers rather than
introducing a separate counter mechanism. Prefix resolution mirrors
`InvoiceSequenceService` via the tenant code plus the fixed `TenantEntityPrefix`.

**Uniqueness:** tenant-owned tables enforce tenant/code uniqueness. Nullable-tenant
`MaterialCategory` keeps separate partial uniqueness for global (`tenant_id IS NULL`) and tenant
(`tenant_id IS NOT NULL`) rows, matching the existing UOM/material-category pattern.

### D76 — Table module: `RestaurantTable` is a real backend entity; narrows D26, supersedes D62's unbuilt

`Table` entity.

`RestaurantTable` (tenant-owned, `table/` feature package, mirrors `Warehouse`'s master-data pattern): `id`, `tenantId`,
`branchId` (FK), `name`, `sectionId` (nullable FK, see D78),
`capacity` (nullable int), `isActive`.

This supersedes D26's "no `RestaurantTable` entity" clause and D62's unbuilt `Table` entity (D62 was decided but never
implemented — see the note added there). D26's other clause — no table status/reservation/occupancy logic on our side —
still holds, consistent with D62's identity/occupancy split. Occupancy stays a POS-local, real-time concern (D19-style),
no
`status` column here.

`Order.tableNo` (plain string per D26) becomes `Order.tableId` (FK -> `RestaurantTable.id`)
whenever the Orders module is built — **done in D81**; `table_no` is dropped, `table_id` is the only link. See the note
added to D63 for the wording implication there.

New permissions: `TABLES_VIEW` (read), `TABLES_MANAGE` (write) — split mirrors D52's Assets precedent.

### D77 — Table layout: position fields on `RestaurantTable`; blank-grid canvas; no decorative elements in V1.

Added columns on `RestaurantTable`: `shape` (`ROUND | SQUARE | RECTANGLE`, `core/enums`,
`EnumType.STRING`), `posX`, `posY` (`NUMERIC`, nullable — null means "not yet placed"),
`rotation` (nullable int, degrees).

Canvas = a client-side render, not a stored entity. All `RestaurantTable` rows sharing a
`branchId` + `sectionId` (D78) are the canvas's contents; no stored canvas size/background. No `LayoutElement`
/decorative-object concept (walls, counters, plants) — deferred; table-only placement is V1's definition of "simulate
the hall." Revisit only on a concrete need (D13).

Layout writes are a dedicated endpoint, separate from general table CRUD:
`PATCH /api/tables/{id}/layout` — body `{ posX, posY, rotation, shape }`, saved on each drag-drop. Plain data update,
not a state transition — no POST sub-resource semantics.

Any future consumer (waiter app, POS grid/spatial view per D80, kitchen display, etc.) reads the same `RestaurantTable`
rows read-only, positioned by the stored `posX`/`posY` — zero backend change required to support a new renderer.

### D78 — Table Section: `TableSection` is a real entity (not a plain string), branch-scoped.

`TableSection` (tenant-owned, `table/section/` sub-package, mirrors `Warehouse`'s master-data shape): `id`, `tenantId`,
`branchId` (FK, not null — a section always belongs to exactly one branch), `name`, `nameAr` (bilingual per convention),
`isActive`.

`RestaurantTable.sectionId` (D76) is the FK -> `TableSection.id`, nullable — a table can be unassigned. Service layer
validates `TableSection.branchId == RestaurantTable.branchId` on create/update, rejecting a mismatch with
`SECTION_BRANCH_MISMATCH` — same validation shape as D51's `assetId`/`assetLineId` pairing check, applied to
branch/section instead.

**Delete guard** (revised by D81): deleting a section now **cascades** to its tables rather than being blocked by them —
but is blocked (`SECTION_HAS_ORDERS`, 409) when any of those tables is referenced by an order, since orders are
permanent (see D81). Deactivating is unguarded (soft, always allowed) — deactivated sections stay assignable-but-hidden
from filters/pickers.

No new permission set — reuses `TABLES_VIEW`/`TABLES_MANAGE` (D76). No confirmed need for a finer split yet (D13).

### D79 — Tables list (admin web): Branch is a required primary filter; Section filter is dependent on it.

The Tables list screen's branch filter is required — no "all branches" option. Section filter is a second, dependent
control: hidden/empty until a branch is selected, then populated from that branch's active `TableSection` rows (D78).
Mirrors the Layout editor's existing branch-then-section flow, bringing the List page's filter model in line with it now
that sections are a real per-branch entity rather than an unscoped free-text field.

### D80 — POS Tables screen: default view shows all sections' tables simultaneously (status-colored grid); full spatial layout is an optional per-section drill-in.

Default screen on the cashier POS app shows every section for the device's branch at once, each as a labeled group with
a compact status-colored grid of its tables (name + status color) — no positioning, optimized for fast at-a-glance
scanning. Scrolls vertically; sections are not hidden behind tabs — seeing every table's status simultaneously is the
explicit requirement.

A "Full layout" action, scoped **per section**, switches to the same spatial canvas rendering used by the admin layout
editor / any future waiter app (D77's `posX`/`posY`/`shape` data), read-only, colored by the same status legend. Zero
new backend endpoints — reuses data already synced for the grid.

**Status source stays POS-local**, per D19/D62: derived from the POS's own `open_ticket`
state (an unclosed local ticket referencing a table = occupied), not a backend `Order`/ table-status field — this stays
true even though `Order.tableId` now exists (D81): occupancy is still POS-local, not derived from backend orders.

**Open item, not solved by this decision**: the existing POS status legend includes RESERVED and MERGED alongside
AVAILABLE/OCCUPIED. Neither reservation nor table-merging (beyond D63's Cashier-POS-specific merge-at-completion
behavior) has a design decision covering this screen's legend. This decision wires AVAILABLE/OCCUPIED only;
RESERVED/MERGED stay defined in the legend/UI but unwired.

### D81 — Order → table is a real FK (`table_id`), replacing `tableNo`; table/section deletes are order-guarded. ✅

Completes D76's deferred transition. `Order.tableNo` (plain string, D26) is **replaced** by
`Order.table` → `RestaurantTable` FK (`table_id`, nullable, DINE_IN-only per the swapped
`chk_orders_table_id_type` guard). `table_no` is dropped from `orders`; V33 best-effort backfills `table_id` by matching
the old free-text value to a table `name` within the same tenant + branch, then drops the column. `OrderRequest.tableId`
replaces `tableNo`;
`OrderResponse` exposes `tableId` + `tableName`. Resolution mirrors D41/D78: the table must be tenant-owned and in the
order's branch (`TABLE_NOT_FOUND` / `TABLE_BRANCH_MISMATCH`).

The FK is **RESTRICT** (`fk_orders_table`, no cascade) — orders are permanent records and are never deleted to make room
for a table delete. Two new delete endpoints follow from this:

- `DELETE /api/tables/{id}` — blocked with `TABLE_HAS_ORDERS` (409) when any order references the table; otherwise
  hard-deletes. (`TABLES_MANAGE`.)
- `DELETE /api/table-sections/{id}` (behavior revised from D78) — now **cascade-deletes the section's tables**, but is
  blocked with `SECTION_HAS_ORDERS` (409) if any of those tables is referenced by an order. The old "blocked whenever
  tables exist" guard (`SECTION_HAS_TABLES`)
  is removed.

Both guards are service-layer pre-checks; the RESTRICT FK is the DB backstop, surfaced as a generic 409 by the existing
`DataIntegrityViolationException` handler.

### D82 — Admin web: Customers screen is read-only (list + search + order-history drill-in) for V1; no create/edit surface here.

Under the existing Orders/Sales nav group. Lists `Customer` (name, phone, plus any existing audit/created fields) with
search by name/phone, paginated. No add/edit form — registration stays exclusively POS-driven at first-order time (D54),
and editing existing customer data stays blocked until the Change Request/approval workflow (O13) is designed. Clicking
a customer drills into a filtered view of their orders — reuses the existing Orders list screen/endpoint filtered by
`customerId`, not a new component; this is a plain filtered display, not an aggregated report, so it doesn't trip the
O14 reporting deferral (O14's
"report" test is aggregation/time-dimension/export — a filtered list of raw rows isn't that). Gated by `LOYALTY_VIEW`
(already-seeded permission, D53 build note) — no new permission.

### D83 — Fixed Assets: tenant-wide flat list endpoints for disposals and maintenance; Assets becomes a 3-screen hub.

D51's nested read endpoints (`GET /api/assets/{assetId}/lines/{lineId}/disposals` and
`.../maintenance`) serve the drill-in-from-an-asset flow only. They cannot back a standalone
"all disposals" / "all maintenance" screen, so two new tenant-wide flat list endpoints are added:

- `GET /api/assets/disposals`
- `GET /api/assets/maintenance`

Both paginated, gated by `ASSETS_VIEW` (D52), filters: `assetId`, `assetLineId`, `category`,
`branchId`, `dateFrom`, `dateTo`. Response rows are denormalized for display (asset name, line label, quantity/cost,
date, reason/notes) so the list needs no N+1 follow-up fetches.

**Both endpoints added even though only maintenance strictly lacks a source.** The V1 Disposal History report (D50)
could technically back a disposal list, but a report and an operational list are different artifacts
(aggregated/time-dimensioned/export-oriented vs. raw filtered rows) — reusing the report would conflate them and leave
the two hub screens structurally asymmetric. The nested D51 endpoints stay as-is; nothing is removed.

**No shared generic query abstraction** between the two (no `AssetChildRecordQueryService`, no generic filter/spec
framework) — two concrete methods on the existing services, per D13.

**FE shape:** Assets becomes a hub of 3 sidebar sub-items (`/assets`, `/assets/disposals`,
`/assets/maintenance`), not tabs — each screen carries its own filters/state and stays deep-linkable. Create flows for
both disposal and maintenance are a two-step modal (pick
`Asset` → pick `AssetLine`), matching D51's paired-request requirement.

### D84 — Reports module: config-driven generic shell; one

`useReportData` hook parameterized by report config; CSV export in V1, PDF deferred.

A single generic shell in `components/reports/` — `ReportFilterBar`, `ReportTable`,
`ReportExportButton`, `ReportSummaryCards` — driven by **one** `useReportData(reportConfig)`
hook, not a bespoke hook per report. `reportConfig` carries the endpoint, filter schema, and column/summary definitions
for a given report; the hook itself contains no report-specific logic. This is the correct read of D13 here (not a
violation of it): the abstraction is justified by the ≥2-caller threshold being met on day one — 6 concrete reports ship
in the same pass (4 Inventory + 2 Fixed Assets), so a per-report hook would be near-identical boilerplate copy-pasted 6
times.

**Scope for this pass**: 4 Inventory reports (Stock Valuation, Purchase History, Waste Report, Physical Count
Variance) + 2 Fixed Assets reports (Total Asset Value, Disposal History). Orders/COGS/Revenue and any cross-module
report are explicitly **out of scope** — deferred until
`OrderConsumptionDoc` is stable in production and the P&L module (O16) exists to consume them.

**Export**: CSV only in V1. PDF export is a real anticipated need but explicitly deferred — pulls in the `pdf` skill and
adds a rendering-layout concern the CSV path doesn't have; not worth building until a report specifically needs a
printable/shareable format (D13).

**Access control**: gated by per-module RBAC permissions already established elsewhere (`INVENTORY_REPORTS_VIEW`,
`ASSETS_VIEW` per D52), not a new report-specific permission set. Tenant-specific one-off reports (a custom report for a
single tenant) are handled with a plain
`tenantId` equality check inside the controller — no new table, no report-registry mechanism.

**Reports value-add test** (reaffirmed, already in use as a design filter): a report must differ from the operational UI
by being aggregated, time-dimensioned, or export-oriented — a re-display of existing rows with a different wrapper is
not a report and doesn't belong in this shell.

### D85 — Document lines: visible/editable whenever status is DRAFT; header auto-persists on first "Add Item" interaction. ✅ (shipped)

Applies uniformly to Purchase Invoice, Purchase Return, and Waste Document. The lines section is shown and editable for
the entire time a document sits in DRAFT — not gated behind a separate
"start editing lines" step. The first "Add Item" interaction on a still-unsaved document auto-persists the header
(creating the DRAFT row) before the line is added, so the user never hits a dead-end trying to add a line to a document
that doesn't exist yet server-side.

Physical Count does **not** follow this pattern — it has its own lifecycle (freeze/reconcile, D28-adjacent) and is
explicitly out of scope for this decision and for the shared hook in O17.

**Known duplication (tracked, not yet resolved)**: the auto-persist state machine described here is implemented three
times — once per screen (Purchase Invoice, Purchase Return, Waste) — with no shared abstraction. This has now crossed
D13's ≥2-concrete-callers threshold (it's at 3), so a shared `useDocumentDraftForm` hook is justified in principle.
Whether/when to actually extract it is **not decided** — see **O17**.


### D86 — Reports engine: concrete per-report queries, one generic FE shell, type discriminator for future renderers. ✅

Backend — concrete, not generic. Each report is its own hand-written query + its own row DTO in the inventory/reports/
package (controller + one service per report). No generic query builder, no shared projection base type, no
report-metadata table — what varies between reports is domain logic (joins, semantics, valuation rules), which is
exactly what shouldn't be abstracted (D13). Queries live in the existing feature repositories (StockBalanceRepository),
not a new report-repository type.

One permission for all reports: INVENTORY_REPORTS_VIEW (migration V34), granted to OWNER / SYS_ADMIN / BRANCH_MANAGER —
matches the TABLES_VIEW (V21) precedent rather than ASSETS_VIEW's narrower grant, since BRANCH_MANAGER already holds
INVENTORY_VIEW. No per-report permission split until a real need appears. BigDecimal fields serialize as String in all
report DTOs (scale-6 toPlainString ()). This deviates from the rest of the codebase, where DTOs expose raw BigDecimal
(e.g. StockBalanceResponse) — the deviation is deliberate and is now the house style for reports specifically, so the FE
parses one consistent shape across every report. Not to be re-litigated per report; not to be retrofitted onto
non-report DTOs. Active-only filtering: report rows require material.active = true AND warehouse.active = true (field is
active, not isActive). A row is only meaningful when both sides are live. Branch join is LEFT JOIN (Warehouse.branch is
nullable) — an implicit/inner join would silently drop branch-less warehouses and understate totals. Asserted by test,
not just convention. Low Stock semantics: minimumQuantity is NOT NULL DEFAULT 0, so "no minimum configured" is stored as
0, never null. The condition is minimumQuantity > 0 AND quantity < minimumQuantity — no COALESCE. A material with no
real threshold never appears as low stock; quantity = 0, minimum = 0 is explicitly excluded (pinned by test). The DTO
field is named minQuantity per the FE contract even though the entity field is minimumQuantity. No aggregation/summary
field in report responses — rows are the only payload (see O19).

> **Scoped by the shrinkage/waste pass — active-only filtering applies to current-state reports
> only.** Not a reversal: the original rule and its rationale stand unchanged for the reports it was
> written for. What was missing is that it was stated as a property of the *module* when it is
> actually a property of *the question the report asks*.
>
> - **Current-state reports** — Stock Valuation, Low Stock. They answer "what is my position now?"
>   A deactivated material has no position worth acting on, so `material.active = true AND
>   warehouse.active = true` stays, exactly as originally specified and asserted by test.
> - **Historical reports** — Shrinkage, Waste Analysis, and everything that follows which reads the
>   ledger over a date range. They answer "what happened?" The past does not change because a flag
>   flipped today, so **no `active` filter is applied to either side**.
>
> **Why the distinction is load-bearing, not stylistic.** On a historical report the filter silently
> deletes evidence: the row vanishes with no counter and no indication anything was omitted, so the
> reader sees a report that looks complete. The failure mode is not hypothetical — steal a material,
> then deactivate it, and the filter becomes the cover-up, erasing the shortage from the one report
> built to surface it. Same shape for waste: write stock off, retire the material, and the write-off
> never happened.
>
> **What historical reports do instead.** The row carries `materialActive`, so an inactive material
> never silently looks like any other — a material no longer in service is itself a lead when
> investigating a discrepancy. There is **no parameter to include or exclude inactive rows**: they
> are always present, because one more optional filter is one more way to hide the evidence, and the
> frontend can filter what it renders. No warehouse-level flag, because these rows are grouped by
> material and span every warehouse in scope, so a row has no single warehouse identity to describe;
> movements from deactivated warehouses are included and fold into the material's figures.
>
> Record this as the rule, not as a per-report exception — without it, every future report re-opens
> the same argument. Anchors: `InventoryTransactionRepository.aggregateShrinkage` /
> `aggregateWaste`; pinned by `ShrinkageReportServiceIntegrationTest` and
> `WasteAnalysisReportServiceIntegrationTest` (a deactivated material appears with the flag false,
> and its quantity/value/count are identical to an active material given the same movements, so the
> flag can never act as a hidden filter or a weighting). Commit `5ec637f`.

Frontend — generic shell, config-driven. One loader for all reports, not one per
report: <GenericReportPage config={...} /> owns filter state, builds query params from declared filters, fetches, and
renders. Nothing report-specific lives in the shell — what varies per report is data (ReportConfig), not code. Shared:
ReportFilterBar (renders declared filters; warehouse dependent on branch per D79's pattern), ReportTable (column-driven,
ColumnMeta-formatted), reportExport.ts (generic CSV + PDF off columns + rows).

ReportConfig.type is a renderer discriminator, currently 'flat' only, selected by a local single-case switch in
GenericReportPage. This exists so a future archetype is an added case rather than a refactor of the flat path. No
registry, strategy object, dynamic import, or scaffolding for unimplemented archetypes — the union widens only when a
real report needs it (O21).

Entry point: /inventory/reports hub (mirrors the Assets hub pattern, D83) with per-report sibling routes, reached from a
"Reports" card on the Inventory hub landing page.

Justified now, not premature (D13): two concrete reports (Stock Valuation, Low Stock) exist as real callers of the shell
before it was generalized.

### D87 — Two-UOM-layer model: the ledger is stock-UOM; balances, batches, and count lines are display-UOM. ✅

Clarifies the wording of D1/D2/D3, which each described one side of this split without
naming it. No behavior change — this is the model the code already implements.

**Layer 1 — canonical (stock UOM).** `inventory_transaction` stores quantity and unit cost
in the material's **stock UOM**, always. This is the immutable, append-only record; nothing
downstream may reinterpret it. `InventoryLedgerService.record()` performs the single
entered→stock conversion (D3) and writes this layer.

**Layer 2 — operational/display (display UOM).** `StockBalance.quantity`, `StockBalance.averageCost`,
`StockBatch.quantity`/`unitCost`, and PhysicalCount line quantities are all expressed in
`material.displayUom`. `StockBalanceService` converts the ledger's stock-UOM signed delta into
display UOM **before** applying it — this conversion is the part D1's "signed ledger delta only"
wording omitted.

**Consequences that must hold everywhere:**
- Any comparison, sum, or arithmetic mixing the two layers is a bug. Same-layer only.
- Derived average cost (D2) is display-UOM per-unit cost, consistent with display-UOM quantity —
  so `quantity × averageCost` is a valid value figure.
- FIFO shortfall pricing (D11) reads `balance.averageCost`, i.e. display UOM — matching the
  display-UOM quantity it prices. Consistent.
- Any endpoint returning stock-UOM aggregates **must** carry an explicit UOM field on the
  response; a bare number is ambiguous and will be misread as display UOM (see O22).
- Rounding at every conversion boundary is `scale = 6`, `HALF_UP` (CONVENTIONS), applied after
  the fold, inside the conversion, and after the final calculation.

**Build note — Physical Count reconcile (the pass that surfaced this):**
Count-time reconciliation now fetches one widest stock-UOM movement window, uses boundaries
`> frozenAt` and `<= maxCountedAt`, folds each material through its own `countedAt`, converts
the single non-zero net into the line's display UOM, persists `adjustedExpectedQuantity`, and
dates corrections at `line.countedAt`. Conversion unavailability fails with a structured
error carrying material + UOM params — never a silent skip. `PhysicalCountService` freezes both
`balance.uom` and `balance.quantity`, so the frozen figure is self-describing.
`unitCostAtFreeze` is cost per **display** UOM, matching the variance unit — verified, no
conversion-factor defect.
Anchor: `PhysicalCountReconcileIntegrationTest` proves 5 KG in the ledger surfaces as 1 BAG in
`StockBalance` and freezes as 1 BAG on the count line. Focused suite: 46 passed, 0 failures.
Commit `2b96fdc`.


### D88 — Ledger-sourced quantities crossing an API boundary must be converted to the display layer **and** carry an explicit UOM field. ✅

Generalizes the fix applied to the post-freeze movements endpoint. This is the API-boundary
corollary of D87's "same-layer only" rule: D87 governs arithmetic, D88 governs what leaves the
backend.

**The rule.** Any endpoint returning a quantity aggregated from `inventory_transaction` (the
stock-UOM layer, D87) must do **both**:

1. **Convert** it into the display-UOM layer the consumer is rendering it beside, and
2. **Carry an explicit UOM field** on the response row (`uomId` + `uomSymbol`, matching the
   convention already used by adjacent physical-count DTOs).
   Neither alone is sufficient, and this is not belt-and-braces. Conversion without a unit field
   leaves a bare number the next consumer will misread — and the next consumer will not have read
   the pull request. A unit field without conversion leaves one screen speaking two units, which
   is correct but unreadable. The pair is the requirement.

**Conversion targets the frozen unit, not the current one.** For physical counts the target is
`line.uom` — the UOM captured at freeze — not `material.displayUom` read fresh. A material's
display UOM is mutable; the figure on screen beside it is not. Same principle wherever a
document freezes a unit.

**Failure is loud.** No conversion path → `UOM_CONVERSION_FAILED` (400) with `materialId`,
`materialName`, `materialCode`, `fromUom`, `toUom`. The unconverted number is never returned as
a fallback — a silently wrong quantity is worse than a failed request.

**The canonical aggregate stays canonical.** Repository-level summing continues in stock UOM;
conversion happens above it, in the service. Nothing reinterprets the ledger (D87 layer 1).

> **Build note — post-freeze movements endpoint (the pass that established this):**
> `GET /api/inventory/physical-counts/{id}/post-freeze-movements` previously returned raw
> stock-UOM aggregates with no unit descriptor, rendered by the FE banner directly beside
> display-UOM count-table rows — a live wrong-comparison bug, not a latent one. Now converts
> per material to `line.uom` and exposes `uomId`/`uomSymbol`. `movementCount` /
> `totalMovementCount` / `affectedMaterialCount` are counts, not quantities, and stay unitless.
> The warehouse-wide totals vs. document-scoped `materials[]` asymmetry is unchanged and
> intentional. No new error enum — reuses `UOM_CONVERSION_FAILED`. 49 tests, 0 failures.
> Commit `b47247c`.
>
> **Known nuance — two conversion orders coexist, deliberately.** This endpoint converts the
> gross `IN` and `OUT` totals separately and subtracts them; the reconcile path folds in stock
> UOM first and converts the single net once. The endpoint returns both gross figures, so it has
> no single value to convert. Maximum divergence is one unit at `scale = 6` (`1e-6`) — below any
> rendered precision. Recorded so it is not later mistaken for an inconsistency.

> **Conversion-order note.** Where a caller needs only a net figure, fold in stock UOM and
> convert once (fewer rounding boundaries). Where a caller must return gross directional
> figures, convert each and derive the net from the converted values. Both are correct; they can
> differ by `1e-6`. See D88's build note.


### D89 — Physical Count: freeze is a settlement boundary; a count produces one movement type; reconcile is terminal. ✅

Seven changes made before the Variance report could be trusted. The report reads
`reference_type = 'PHYSICAL_COUNT'` rows straight from the ledger, so every defect in how a
count writes them would have surfaced as a wrong number in front of a client.

**Freeze settles outstanding consumption first.** Before the snapshot is taken, the warehouse's
outstanding order consumption must be resolved. A `PENDING` doc is claimed and processed
(reusing `OrderConsumptionService`'s existing `claimDoc` / `processClaimedDoc` pair, each
`REQUIRES_NEW` so the `IN_PROGRESS` flip commits before D29 runs) and the snapshot is taken
immediately after. A doc still unsettled after the attempt refuses the freeze as retryable
(`FREEZE_CONSUMPTION_NOT_SETTLED`).

A **`CONFLICT` doc blocks the freeze outright** (`FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT`,
carrying the failing materials plus a pre-joined `materialNames` string capped at 5 names for
display). A CONFLICT means consumption failed to post; letting the count proceed would surface
that consumption as an unexplained shortage and read as theft. The user must fix the underlying
cause — usually a missing purchase invoice — and retry.

Orders completing *after* the snapshot are unaffected and flow to the next doc. **Order intake
is never blocked, paused, or queued by a count.** The restaurant does not stop trading because
someone is counting.

**One movement type, both directions.** A count produces `COUNT_ADJUSTMENT` only; the direction
carries the meaning (shortage = OUT, FIFO-consumed at open-batch cost; surplus = IN, opening a
batch at the current average per D2). The per-line "adjustment vs waste" choice is removed
end to end — request DTO, `waste_transaction_id` column (dropped in V35), and service branching.

Rationale, recorded because it will be re-proposed: a shortage found at a count is a gap with an
**unknown cause** — it may be theft, over-portioning, short delivery, or unrecorded waste.
Waste is a **known, observed** cause with a reason code. Classifying the unknown as waste both
claims a cause nobody established and mathematically destroys the report: waste is subtracted on
the expected side of the equation, so booking the gap as waste drives the variance to zero by
construction. Letting the user choose per line was worse still — the same physical event would
be classified differently by different clients, making the data incomparable. If a cause *is*
known, the correct workflow is to record a waste document **before** reconciling; the remaining
gap stays honestly unexplained.

The transaction type was deliberately **not renamed** to `SHRINKAGE`. `reference_type =
'PHYSICAL_COUNT'` already distinguishes count movements for reporting, and renaming would mean
an enum change, a CHECK constraint migration, and a backfill for no functional gain. "Inventory
shrinkage" is a **display and P&L label**, not an enum value. Note that opening balance also
writes `ADJUSTMENT`-class rows with a null `referenceType` — filtering reports by transaction
type alone would swallow initial stock setup as a massive shortage. **Filter by
`reference_type`.**

**Reconcile is terminal.** No unpost, no reverse, no reopen — same reasoning as Waste (D7): a
count records a physical observation, not a bookkeeping entry. An error is corrected by counting
again, never by erasing. This is what makes the Variance report auditable: it reads an
append-only table whose rows have no reversal path, so a figure produced last month reads the
same a year later.

Editing and deleting stay available **before** reconcile under the existing DRAFT + no-ledger
dual check (D6). The review screen carries an explicit confirmation stating the total, the
number of lines that will move, and that the action is final.

**Partial counts** generate movements only for counted lines. A material absent from the
document produces no row — not a zero-quantity one — and a counted line with zero variance
produces no row either.

**Post-freeze movements are reported, not hidden.** `GET /{id}/post-freeze-movements` exposes
what moved since the snapshot. Informational only: it never blocks, and after D90 it explains
*why* expected differs from the frozen figure rather than warning that it might be wrong. Per
D88 it returns display-UOM quantities with an explicit UOM field.

> **Addendum (D91).** D91's warehouse row lock in `PhysicalCountService.start()` is the same row
> lock `OrderConsumptionService.findOrCreatePendingDoc` takes, so an order completing in that
> warehouse waits for the duration of the freeze transaction — including its settle step, whose
> per-material FIFO work is expensive by design (D29). D89's "order intake is never blocked,
> paused, or queued by a count" remains true of the **count's lifetime**: an order arriving
> mid-count still flows to the next doc and is never rejected. It is no longer true of the
> **freeze instant**, where an order completion blocks until the freeze commits.
>
> Accepted because freezing is rare, human-initiated, and bounded by the settle it already waits
> on; and because POS order submission is asynchronous and retry-safe (D72), so the visible
> effect is a slower response, never a lost order. Revisit if freeze duration grows — the settle
> step is the part that scales with pending volume.

> **Known limitation — historical data.** Counts reconciled before this change may have booked
> shortages as waste, depending on what the user selected per line. For any period before it,
> the Variance report **understates** the gap and the Waste report **overstates** it. This is
> not repairable retroactively: nothing in the data distinguishes a genuine waste record from a
> shortage classified as one. Do not attempt a backfill.

> **Build note.** `CountLineAction.WASTE` is retained in the enum, never written — removing it
> would break `@Enumerated(STRING)` reads of pre-V35 rows, and no backfill was performed.
> `adjusted_expected_quantity` was likewise not dropped: unlike `waste_transaction_id`, that
> figure is unrecoverable and is the only explanation of a historical row's variance.
> `lastCountDate` is stamped at the reconcile instant, not the cutoff — an audit field, nothing
> reads it as a business date. 400 tests, 7 commits.

> **Aggregate vs row scope (clarified during the D95 pass).** The post-freeze movements endpoint
> exposes two things with deliberately different scopes. The warehouse-wide totals are
> **open-ended** — `createdAt > frozenAt` with no upper bound — and cover every material in the
> warehouse. The `materials[]` breakdown and the individual movement rows are **document-scoped
> and windowed**: only the count's own materials, only `movementDate <= countedAt` (D93).
>
> The totals can therefore exceed the sum of the rows, by movements on materials absent from the
> count and by movements recorded after it. This is intentional — the totals answer "what has
> moved in this warehouse since the snapshot", the rows answer "what affected this document's
> variance". **Any test asserting equality between them is asserting the wrong relationship**;
> the correct assertion is that the rows are a subset of what the totals cover. This was
> mis-stated in an implementation prompt during the audit and caught by the implementing agent
> before any code was written — recorded here so it is not repeated.
---

### D90 — Physical Count variance is measured at each line's own count time, and every correction is dated there. ✅

The correctness core of the module. Governs *what* the variance number means; D89 governs the
document lifecycle around it.

**The problem.** A count freezes an expected quantity, but counting is physical work that takes
hours or days while the restaurant keeps trading. Measuring the counted quantity against the
**freeze-time** figure double-counts every movement in between:

```
freeze     expected 100, balance 100
sale of 5  balance 95            ← already in the ledger
count      shelf holds 95        ← nothing is missing
 
against frozen:   95 − 100 = −5  → balance 95 − 5 = 90   ✗ shelf holds 95
against netted:   expected 100 − 5 = 95;  95 − 95 = 0    ✓ no movement at all
```

**The rule.** Per line:

```
expectedAtCount = expectedQuantity + netMovements(material, > frozenAt … <= countedAt)
variance        = countedQuantity − expectedAtCount
movementDate    = line.countedAt
```

**The window closes at the count, not at reconcile.** Movements after the count are deliberately
ignored: the variance is a **delta**, and a delta applies correctly on top of whatever the
balance has since become. Netting them too would double-count them in the opposite direction —
the mirror image of the original bug. A concrete consequence: reconcile can be delayed for days
without affecting the result.

**The window and the movement date must agree.** An earlier implementation netted the movements
but dated the correction at the freeze instant — genuinely inconsistent, and correctly removed.
Dating at `countedAt` resolves it: both describe the same moment. A single document may
therefore produce movements bearing **different dates**, one per material. This is intended and
breaks nothing — FIFO ordering is id-based (D10), so back-dating cannot reorder batch
consumption.

**Per line, because counts are partial.** A count may cover a subset of materials, and each is
counted at its own moment. There is no single document-level period; the window is per line.
`countedAt` is refreshed on every update of a counted quantity — a correction is a recount — and
cleared when the quantity is cleared.

**One computation, two callers.** The same code serves reconcile (write) and the detail read.
The read path exists because without it the review screen showed the un-netted figure while
reconcile recorded the netted one: the user confirmed an irreversible action against numbers
that were not the numbers being written, including a wrong total and a wrong count of affected
lines in the confirmation dialog. Extraction is justified under D13 by two concrete callers —
and kept concrete (a shared method returning per-line values, no interface, no framework).

**A reconciled count returns persisted values — never recomputed.** This is the load-bearing
part. Recomputation would net in every movement since, so a finalized document would show
different numbers each time it is opened, and those numbers would no longer match the ledger
rows it actually produced. Guard it explicitly and comment it: this is precisely the branch a
later refactor "simplifies" away, and no test written against the recomputed value would fail.

**Uncounted lines are provisional.** A line with no `countedAt` has no measurement instant, so
its expected figure is computed as of *now* and legitimately changes between refreshes. The
response marks it (`adjustedExpectedQuantityProvisional`) and the UI labels it, so a settled
number is never confused with a moving one.

**UOM.** The movement fold happens in stock UOM and converts **once per line** into the line's
frozen UOM (D87) — not per movement row, since repeated rounding at `scale = 6` accumulates real
drift. The line's frozen `uom` is the target, not `material.displayUom` read fresh, since a
material's display unit is mutable while the figure on screen beside it is not. No conversion
path → structured failure with both units; never a silent fall back to the unconverted number.

**Valuation is unchanged and remains an estimate.** `varianceValue` uses `unitCostAtFreeze`
(verified to be per display UOM, matching the variance unit), while the ledger values the
movement itself — FIFO for a shortage, current average for a surplus. The two can diverge
substantially: a batch at 3.00 consumed against a frozen average of 5.00 is 40% off on that
line. The response carries `varianceValueIsEstimate` and the UI marks it, including on the
confirmation dialog. **The quantity is exact; the value is indicative.**

> **Deferred.** Persisting the *actual* posted variance value (available from
> `ledgerService.record`'s returned transaction) needs a new column. Doing so would make
> `varianceValueIsEstimate` genuinely dynamic rather than true whenever a value is present, and
> would let the Variance report read the exact figure off the line instead of re-deriving it.
> Not built.

> **Build note.** One widest-window query per detail read, folded in memory per line; no
> per-line queries. Covered by `idx_inv_tx_tenant_wh_material_date`. DRAFT and RECONCILED skip
> the computation entirely. Incidentally removed the pre-existing lazy-loading N+1 on the detail
> read (`3 + 2N` queries → 2). A conversion failure on read fails the whole request (400,
> `UOM_CONVERSION_FAILED`) — consistent with D88, at the cost of one misconfigured material
> taking down the review screen for otherwise-healthy lines. Accepted, recorded.
> Commits `9b106fa`, `aad91ac`. 59 tests.

### D91 — Physical Count concurrency: the material-level freeze guard is the only guard. ✅

Established during the D87–D91 audit, after a live test showed a count on one material blocking
an unrelated count on a different material in the same warehouse.

**What was already correct (verified, unchanged).** `PhysicalCountService.start()` — the freeze
point — already rejects a freeze when any material on the count is held by a different
`IN_PROGRESS` count in the same tenant + warehouse, via the single-query
`PhysicalCountRepository.findFreezeConflicts`. Its semantics were already the correct ones: only
`IN_PROGRESS` blocks, so `RECONCILED` and `CANCELLED` counts never block a recount — keeping
D89's "correct by counting again" intact — and any overlap rejects the **whole** freeze rather
than freezing a subset, since a half-frozen document is a lifecycle state nothing else in the
module is designed for.

**What was wrong and is removed.** `create()` additionally rejected a second DRAFT/IN_PROGRESS
count for the same warehouse on the same `scheduledDate`, **regardless of materials**. Wrong on
every axis: it fired at creation, where a DRAFT count has no snapshot, no netting window (D90),
and no effect on anything; it keyed on a user-entered scheduled date rather than document state;
and being material-blind it blocked two staff counting the freezer and the dry shelf on the same
day — ordinary restaurant work. It carried no test. `DUPLICATE_OPERATION` survives its removal,
being shared by six other services.

Creation and DRAFT line editing are now unrestricted. The freeze is the only gate.

**The hazard the freeze guard exists for.** Two live snapshots over one material produce two
netting windows (D90) over the same movements. D90's netting absorbs the common case on its own —
a reconciled count's `COUNT_ADJUSTMENT` rows fall inside a later count's window and self-correct
its expected figure. The guard exists for the inverted-order edge case netting cannot absorb:
where the two counts' `countedAt` order is reversed relative to their reconcile order, the first
count's adjustment rows fall outside the second's window and the same correction is applied
twice. Second line of defence, not the only one.

**Enforcement is a warehouse row lock, not a unique constraint.** The pre-check is
check-then-act: two concurrent `start()` calls can both pass `findFreezeConflicts`. The partial
unique index used elsewhere for this class of race (D44, D72) is **not available here** —
`status` and `warehouse_id` live on `physical_count` while `material_id` lives on
`physical_count_line`, so no single table holds the tuple. `start()` therefore takes
`WarehouseRepository.findByIdAndTenantIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) before the
check, reusing the exact pattern in `OrderConsumptionService.findOrCreatePendingDoc`. Freezing
is rare and human-initiated; serializing it per warehouse costs nothing.

> **A `physical_count_freeze_hold` table was considered and rejected.** It would give a real
> unique constraint, but only by denormalizing state that must then be kept in sync across five
> lifecycle transitions (freeze, reconcile, cancel, revert, delete). One missed deletion locks a
> material indefinitely with no visible cause. D13: the lock solves the actual race with no new
> state.

**Error stays `FREEZE_CONFLICT`, with extended params.** Not renamed — same reasoning as D89's
refusal to rename `COUNT_ADJUSTMENT` to `SHRINKAGE`: renaming a tested error code for no
functional gain is churn. Params now carry `blockingCountId` (the oldest blocker; others remain
identifiable via the list) alongside the existing `conflicts` list of
`{materialName, conflictingCountCode}`, plus a `materialNames` string capped at 5 with an
`… +N` tail, rendering identically to `FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT`. Without a named
blocker, a forgotten frozen count locks a material with no route out.

> **Build note.** Commits `b129958` (guard removal), `5ae8b5b` (warehouse row lock + ordering
> test), `70ab9a3` (params, `MaterialConflictProjection.getCountId()`, deterministic
> `ORDER BY pc.id, l.material.id` on `findFreezeConflicts`, `formatMaterialNames` generalized to
> `List<String>` so both freeze blockers share it). 44 unit + 3 integration tests green,
> including `disjointMaterialCountsOnSameWarehouseAndDayBothCreateAndFreeze` — the case the
> removed guard wrongly blocked.

> **Verified non-deadlock — and why it is fragile.** `start()` holds the warehouse lock while
> D89's `settleOutstandingConsumption` runs its `REQUIRES_NEW` steps on separate connections. An
> inner request for that same warehouse row would hang **forever without being reported**: the
> outer session is blocked in Java, not on a DB lock, so PostgreSQL's deadlock detector never
> fires. Verified that no such request exists — `claimDoc`/`processClaimedDoc` lock only the
> `order_consumption` root row (Hibernate emits `FOR NO KEY UPDATE OF oc1_0`, scoped despite the
> `JOIN FETCH doc.warehouse`), and `InventoryLedgerService.loadWarehouse` is a plain `findById`.
> `findOrCreatePendingDoc` — the one warehouse `FOR UPDATE` in `OrderConsumptionService` — is
> reachable only from `recordCompletedOrder`. **This safety is a property of current
> implementation details, not of the design.** Treat any new warehouse-row lock inside the
> consumption path as a blocking review finding against this decision.

> **Exposed defect (fixed separately — commits `6d40e88`, `fbed4b6`).** Removing the creation
> guard revealed that `physical_count.code` was generated from warehouse + scheduled date only
> (`PC-<warehouse>-<date>`), so a second count on the same warehouse and date collided on
> `uk_physical_count_tenant_code`. The user saw a raw Hibernate stack trace — the global handler
> returned a generic 409 and logged the trace rather than an inventory-specific error (a D12
> gap). The guard had been masking this since the constraint was introduced.
>
> Codes now carry a zero-padded sequence: `PC-<warehouse>-<date>-0001`. The counter is scoped by
> `(tenant_id, warehouse_id, scheduled_date)` (`V38`) and allocated by
> `PhysicalCountCodeSequenceService` using a single atomic
> `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` — no check-then-act, so there is no first-row
> race to recover from. Pinned by a test allocating eight concurrent first sequences and
> asserting exactly 1..8. Named collisions now translate to structured `DUPLICATE_CODE`. Existing
> rows keep their codes; no backfill.
>
> **`InvoiceSequenceService` was deliberately not reused.** Its scope and formatting are
> invoice-specific, and it guards first-row concurrency with the unique constraint alone, with no
> recovery path — reusing it would have propagated that gap into a second module. See F7.

### D92 — A global `lock_timeout` is set on every pooled connection; `statement_timeout` is not. ✅

Introduced alongside D91's warehouse row lock. No connection-level settings mechanism existed
previously — no `connection-init-sql`, no custom `DataSource` bean — so one was created:
`spring.datasource.hikari.connection-init-sql: SET lock_timeout = ${SPRING_DATASOURCE_LOCK_TIMEOUT_MS:5000}`
in `application.yml`, externalized as an env-var placeholder matching how every other tunable in
that file is configured.

**Why.** D91's freeze holds a warehouse row lock across `REQUIRES_NEW` inner transactions. If an
inner transaction ever requests that same row, the wait is indefinite and unreported — the outer
session blocks in Java, so PostgreSQL's deadlock detector never fires. Without a timeout the
failure mode is a permanently stuck transaction holding a lock, order completions for that
warehouse queuing behind it, connections leaking from the pool, and an application restart as
the only remedy. `lock_timeout` converts that — and any comparable lock wait anywhere in the
application — into a failed request that releases everything.

**`statement_timeout` is deliberately excluded.** Different concern, different blast radius: it
would kill legitimate long-running work such as reports and D29's per-material FIFO consumption
loop, which is expensive by design. This setting is scoped to lock acquisition only.

**5000ms.** Far longer than any legitimate lock in this system, far shorter than any user's
patience.

> **Build note.** `DataSourceLockTimeoutIntegrationTest` asserts `SHOW lock_timeout` returns
> `5s` through a real pooled connection, and is permanent rather than a one-off check — a later
> removal or typo in the init SQL would otherwise fail silently, which is the exact failure this
> decision exists to prevent. Externalization proven end-to-end: the same test with
> `SPRING_DATASOURCE_LOCK_TIMEOUT_MS=250` fails with `expected "5s" but was "250ms"`.

### D93 — The netting window's lower bound is the transaction's creation time, not its movement date. ✅

Fixes a live defect found by testing during the D87–D92 audit. D90's netting rule is unchanged
in intent; only the field the lower bound compares against is corrected.

**The defect.** D90 nets movements over `movementDate > frozenAt AND movementDate <= countedAt`.
But `frozenAt` is a real system instant (`2026-07-31 17:16:50`) while `movementDate` is a
user-entered business date — and for purchase invoices it is `receiptDate.atStartOfDay()`, i.e.
midnight. Comparing them means **any movement registered after the freeze but carrying a same-day
or earlier receipt date is excluded from the window**, even though the balance did not contain it
at snapshot time. The stock is on the shelf, absent from the frozen figure, and absent from the
adjustment — it falls between the two and surfaces as a phantom surplus.

Reproduced end to end: balance 100, frozen at 17:00, invoice for 20 units dated the previous day
entered at 17:20, shelf counted at 120. The system reported `+20` surplus and posted it, opening
a batch for 20 units that do not exist. The correct variance is zero.

Re-running the same query later does not help and is not the fix: the excluded row's
`movementDate` is still older than `frozenAt` at every subsequent evaluation. The bound compares
the wrong field, not at the wrong time.

**The rule.**

netMovements = rows where createdAt > frozenAt (was in the snapshot?)
AND movementDate <= countedAt (had it happened by the count?)


Each bound uses the field that answers its own question. `createdAt` is a system instant, so it
compares meaningfully against `frozenAt`, and it is the only field that can distinguish "already
in the snapshot" from "registered afterwards." `movementDate` stays on the upper bound: whether a
movement had occurred by the moment of counting is a question about business reality, and the
user-entered date is the right answer to it.

This also closes the mirror defect: a movement dated in the future but registered **before** the
freeze is already inside the frozen figure, and is now correctly excluded (`createdAt < frozenAt`)
rather than counted a second time.

**`movementDate` keeps its other roles unchanged.** It remains the FIFO ordering key (D10), the
date stamped on count adjustments (D90), and the reporting date throughout. This decision narrows
to one comparison: the netting window's lower bound.

**Movements before the count are netted silently; movements after it are surfaced, not blocked.**
Stock arriving between freeze and count was physically on the shelf when the user counted, and
they counted it — netting it is ordinary operation and needs no notice. Stock arriving **after**
`countedAt` was not on the shelf, is correctly excluded by D90's upper bound, and stays on the
balance after reconcile. But the user sees a live balance that no longer matches the count and
has no explanation, so the confirmation screen names those movements and states that they are
deliberately excluded, offering a recount as the remedy.

**Not blocking, deliberately.** A blocking rule cannot terminate in a trading restaurant: every
recount can be invalidated by the next movement. The count measures the shelf **at the moment of
counting**, and the resulting variance is a delta that applies correctly on top of whatever the
balance has since become (D90).

> **Known limitation — `countedAt` is entry time, not observation time.** `countedAt` records
> when the figure was typed, not when the shelf was looked at. A movement landing in that gap is
> invisible to every rule here: it is on the shelf but not in the count, or counted but timed
> after. Nothing in the data can detect this. Count accuracy therefore depends on entering
> quantities promptly after counting; a long gap is an accuracy gap the system cannot see and
> does not claim to correct.

> **Data note — the reproduction run.** The test that surfaced this posted a real `+20`
> `COUNT_ADJUSTMENT` on Baladi Bread and opened a batch for 20 non-existent units. The ledger
> rows stand (D4, append-only); the balance was corrected forward by a waste document, not by
> deletion.

> **Build note.** Commits `24ffc92` (fix + index), `46627b8` (tests).
> Two queries carried the defective lower bound, not one: the bounded netting query and an
> independent open-ended summary query in `InventoryTransactionRepository`. Both now use
> `createdAt > frozenAt`; `movementDate <= maxCutoff` and FIFO ordering are untouched.
> `calculateAdjustedExpectedQuantities` remains the single shared computation for the reconcile
> write and the detail read (D90), and the RECONCILED persisted-value guard is intact.
>
> `V37` adds `(tenant_id, warehouse_id, created_at)` — no existing index covered the audit
> timestamp. `created_at` is `NOT NULL DEFAULT CURRENT_TIMESTAMP`, JPA-non-null, and
> pre-persist-populated; live check found 71 rows, 0 nulls, so no row can silently drop out of
> the window.
>
> 68 tests across five physical-count classes, 0 failures. Pinned:
> `movementRegisteredAfterFreezeButDatedBeforeFreezeIsIncludedWithoutVariance` (the reproduction
> case), `...DatedAtStartOfFreezeDayIsIncluded` (the `atStartOfDay` case),
> `movementRegisteredBeforeFreezeButDatedAfterFreezeIsExcluded` (the mirror defect),
> `...DatedAfterCountIsExcluded` (upper bound intact),
> `detailReadMatchesReconcileWithoutPersistingBeforeTheWrite`,
> `reconciledDetailKeepsStoredExpectationAfterLaterWarehouseMovement`, and
> `countWindowRowsUseRegistrationLowerAndMovementUpperBoundsWhileExcludingOwnMovement` — the last
> exists specifically so a later refactor collapsing both bounds onto one field fails a test
> named for the distinction. Existing fixtures were updated only to give pre-freeze seed rows
> realistic pre-freeze `created_at` values.

### D94 — Insufficient stock stops consumption for that material only; the doc goes PARTIAL, not CONFLICT. ✅

Replaces the shortfall behaviour found during the D87–D93 audit, where a FIFO shortfall was
priced at the balance's current average cost and allowed to drive `stock_balance.quantity`
negative (D1, D11). That path is closed for order consumption.

**Why estimated pricing is rejected.** Valuing an unmatched remainder at the current average
produces a cost that is knowably wrong: the invoice that will eventually cover it carries a real
price, and a batch at 125 costed at an average of 120 is 4% off, permanently, with no retroactive
correction (D11). Worse, the deficit was never settled against the incoming batch, so the batch
opened at full quantity while its stock had already been sold — phantom stock that inflated open
batches, corrupted the derived average (D2), and charged the same goods twice. A physical count
could not detect it, since counts compare against `stock_balance`, not against the batch layer.
Waiting for the real invoice costs a delay; estimating costs a permanently wrong number.

**The rule.** A material whose open batches cannot cover the requested quantity is **not
consumed**. No ledger row, no batch mutation, no balance movement, no estimated price. Its doc
lines stay `isConsumed = false`.

Every other material in the same doc consumes normally and is marked `isConsumed = true`.

**The doc status is `PARTIAL`** — a new value alongside `PENDING | IN_PROGRESS | POSTED |
CONFLICT` (D42). `errorDetails` carries one entry per unconsumed material: `materialId`,
`materialName`, quantity required, quantity available, and the warehouse. A `PARTIAL` doc is
**closed** to new lines; orders completing afterwards attach to a new `PENDING` doc, exactly as
they do for `IN_PROGRESS` (D28). When every material eventually consumes, the doc becomes
`POSTED`.

**`PARTIAL` is not `CONFLICT`; the two failure classes are distinguished by status, not by how
lines are marked.** D30's all-or-nothing rule was written for *systemic* failures — deadlock,
timeout, constraint violation — where the fault is not material-specific and retry must be a full
re-run. That reasoning stands: `CONFLICT` keeps whole-doc retry semantics (D31). Insufficient
stock is a different class — expected, per-material, caused by missing data rather than a
technical fault — and gets its own status so the user sees a fixable condition rather than a
system error.

**Both statuses mark lines by outcome.** A line is `isConsumed = true` when its material
committed and `false` otherwise, regardless of which status the doc carries. Anything else
records a state the `REQUIRES_NEW` transaction boundary makes impossible — see D30's amendment.

> **D30 amended.** Its "no partial success" clause now applies to `CONFLICT` only. It also
> described a state the code never produced: because each material commits in its own
> `REQUIRES_NEW` transaction, materials that consumed before a later failure were already
> committed while all lines were flipped to `isConsumed = false` — the doc claimed nothing was
> consumed while stock had in fact moved. `PARTIAL` makes the real behaviour explicit and
> truthful rather than introducing it.

**Retry is per-material and already safe.** The manual recalculate (D45/D58) re-runs the doc; the
per-material idempotency key `ORDER_CONSUMPTION_DOC:{docId}:MATERIAL:{materialId}` short-circuits
already-posted materials, so only the outstanding ones are attempted. No new mechanism is needed.

**Visibility is part of the decision, not a follow-up.** A `PARTIAL` doc means the food was sold
and the revenue recorded while its cost was not — profit is overstated until it resolves. It must
therefore surface where someone will act on it, not only on a screen that has to be opened:
- the on-the-fly available-quantity figure (D43) already subtracts `PENDING` doc lines; it now
  also accounts for unconsumed `PARTIAL` lines, so the shortfall is visible on the stock screen
  before anyone investigates;
- the consumption screen names the blocked materials with required vs available quantities;
- an alert persists until the doc reaches `POSTED`.

Reporting treatment of an open `PARTIAL` doc — whether P&L flags or excludes the period — is
**not decided here**; see O16's COGS-timing question.

**Order intake is never blocked.** The POS does not query backend stock before selling (D61) and
never will under this decision. Stock that has been sold is gone regardless of what the ledger
knows; `PARTIAL` records that the cost is not yet knowable, it does not attempt to prevent the
sale.

> **Manual documents keep their own guard.** `WasteService` rejects a shortfall at post time with
> `INSUFFICIENT_STOCK` before any ledger call, and that is correct and unchanged: a waste
> document records an intended write-off, so refusing it costs nothing. An order records a
> completed sale — refusing it changes nothing that already happened. The asymmetry is
> deliberate.
> **Build note.** Commits `8f6643f` (detection), `83dde49` (flow), `e19b3a2` (availability),
> `a020609` (tests).
>
> Shortfall is detected by comparing the display-UOM requirement against the existing
> `sumOpenBatchTotals` aggregate — **inside the material's own `REQUIRES_NEW` transaction**, not
> before the loop, so check and consume share one transactional boundary and two concurrent
> materials cannot both pass on the same stock. Insufficient materials never reach the ledger.
> `StockBatchService.consumeFifo` is **unchanged**: waste, physical count, and manual document
> shortfall behaviour is untouched, and their suites passed without modification (74/74).
>
> `V39` adds `PARTIAL` to `chk_order_consumption_status`
> (`PENDING | IN_PROGRESS | PARTIAL | POSTED | CONFLICT`). `OrderConsumptionErrorDetail` carries
> `materialId`, `materialName`, `requiredQuantity`, `availableQuantity`, `uomId`, `uomSymbol`
> (D88), `warehouseId`, `warehouseName`, plus the existing `exceptionClass`/`message` for the
> CONFLICT case. The existing `GET /{id}` endpoint serves both.
>
> Technical failure takes precedence: a run with both a short material and a technical failure
> produces `CONFLICT`, pinned by `technicalFailureWinsWhenAnotherMaterialIsShort`. Find-or-create
> searches `PENDING` only, so a `PARTIAL` doc receives no new orders. **D89 interaction:**
> physical-count freeze treats `PARTIAL` as unsettled consumption and refuses — without this, a
> count could freeze over stopped consumption and surface it as an unexplained shortage.
>
> On retry, the per-material idempotency key short-circuits **before** the availability check, so
> an already-posted material is not re-tested against a balance it has already reduced.
> Availability (D43) now subtracts `PENDING` lines plus unconsumed `PARTIAL` lines;
> `IN_PROGRESS` stays excluded.
>
> `oneShortMaterialStaysUntouchedAndRetryPostsOnlyOutstandingMaterial` pins the whole path in one
> test: no ledger row, no batch mutation, no balance movement for the short material; stored
> balance never negative; no zero-cost COGS row; detail fields present; shortfall reflected in the
> displayed quantity; `PARTIAL` closed to new lines; retry idempotent. Focused order/core suite
> 85/85.

### D95 — `varianceValue` carries the sign of its variance. ✅

Corrects a defect found during the D87–D94 audit: `PhysicalCountService` computes
`varianceValue` as `variance.abs() × unitCostAtFreeze`, so a shortage of 0.5 units at 80 is
returned as `+40.00`. The UI renders it in the gain colour with a `+` prefix, on the screen that
confirms an irreversible action — a manager approves a loss presented as a gain.

**The rule.** `varianceValue = variance × unitCostAtFreeze`, sign preserved. A shortage is
negative, a surplus positive, and the document total is the arithmetic sum of the line values.

**Why absolute value is wrong here, not merely inconvenient.** The same row already exposes
`variance` with its sign, so an absolute `varianceValue` makes two fields describe one event with
opposite signs, and forces any consumer to infer the sign from a sibling field. Worse, the
document total becomes meaningless: a 40 shortage and a 40 surplus sum to 80 rather than
netting to zero, so every mixed count overstates its total. The Variance report (D84) reads the
same fields and would inherit the error into every aggregate.

**Unchanged.** `varianceValue` remains an estimate — it uses `unitCostAtFreeze` while the ledger
values the movement itself (FIFO for a shortage, current average for a surplus), and the response
continues to carry `varianceValueIsEstimate` (D90). This decision changes the sign, not the
valuation basis. The quantity stays exact; the value stays indicative.

**A backend test currently pins the absolute value** (a shortage of `-2.000000` expected to
return `10.000000`). That expectation is the defect, not a contract to preserve; it is updated,
and a mixed-sign document total is pinned so the netting behaviour cannot silently regress.

> **Frontend follows, and only after this lands.** Negative renders with the loss colour, positive
> with the gain colour, zero neutral — identically on the line, the document total, and the
> reconcile confirmation dialog. The `تقديري` / estimated marker is unchanged. A UI that negates
> a positive value by reading `variance`'s sign was explicitly rejected: it would leave the API
> lying and put the fix out of reach of every other consumer.

> **Build note.** Commits `f25f74b` (fix), `02529c7` (tests).
> `PhysicalCountService:653` computes `variance × unitCostAtFreeze` signed at `scale = 6`,
> `HALF_UP`; the document total at `:516` is the arithmetic sum of signed line values.
>
> **Large-variance detection stays magnitude-based** (`:519`): the signed total is persisted, but
> `abs(total)` is compared against the threshold, so a large shortage and a large surplus both
> trigger review. Comparing the signed value would have made a large shortage a large negative
> number that never crosses the threshold.
>
> The ledger quantity's own `.abs()` is deliberately unchanged — there, direction (IN/OUT) carries
> the sign, so the magnitude is correct.
>
> The existing test expecting `+10.000000` for a variance of `-2.000000` was the defect and was
> corrected. `mixedVarianceValuesNetDocumentTotalToZero` pins the netting behaviour so a future
> reintroduction of `.abs()` fails a test named for the distinction. API descriptions corrected in
> `PhysicalCountController:66`, `PhysicalCountLineResponse:29`, and `Inventory.md:185`.
> 77 physical-count tests, including the real-Postgres suites.

### D96 — Order consumption docs carry a persisted per-material tab; consumed state moves off the line. ✅

Fixes a live defect found during the post-D95 verification pass: after a `PARTIAL` doc, the
displayed balance for a material that **had** consumed showed **−8** while its batches summed to
**7** — the full requirement was being subtracted a second time from an already-reduced balance.

**The cause is a units mismatch in the state model.** Consumption executes **per material**
(after D29 aggregation), but `isConsumed` is stored **per line**, and one line can require several
materials. A shawarma line requires chicken and bread; when chicken consumes and bread does not,
the line has no truthful value — it is neither consumed nor unconsumed. It stays `false`, and
`OrderConsumptionAvailabilityService`, which subtracts the materials of unconsumed lines,
subtracts the already-consumed chicken again. A line-level flag cannot represent a
material-level outcome, so no amount of care in setting it would have been correct.

**The rule.** An `OrderConsumptionDoc` gains a second tab: one row per material, carrying the
aggregated required quantity, its UOM, `isConsumed`, and — when not consumed — the available
quantity and the reason. `isConsumed` on the line is removed; the material row is the only
record of what did and did not consume.

`OrderConsumptionDocLine` keeps its existing `orderLineId` reference. Pointing it at the order
instead was considered and dropped — it changes no behaviour and the extra churn is not worth it.

**Material rows are written by the processing pass, not by order arrival.** They are created in
the **same transaction as the D29 aggregation, before any consumption is attempted**, all at
`isConsumed = false`. Writing them per arriving order was rejected: it would take a row lock per
material on the order-completion path, so five cashiers selling the same product contend on the
same few rows, hundreds of times a day, on a hot path that D72 keeps deliberately append-only.

**Availability (D43/D94) reads whichever source matches the doc's state:**

| Doc state | Source of the outstanding figure |
|---|---|
| `PENDING` | on-the-fly aggregation over lines (unchanged — no material rows exist yet) |
| `PARTIAL` | material rows where `isConsumed = false` |
| `POSTED` | nothing outstanding |

The two never overlap: a doc is in one state, and the material rows exist from the instant it
leaves `PENDING`. Because they are written from that same aggregation, the figure is identical
across the transition — the displayed number does not jump.

**Recalculate reuses the existing material rows; it does not re-aggregate.** A `PARTIAL` doc is
closed to new lines (D94), so no new quantity can enter and re-aggregation could only produce a
*different* answer, not a better one — a recipe edited in the meantime would yield quantities
that do not match what was already posted. The rows are a record, not a repeatable computation.
Already-consumed materials continue to short-circuit on the per-material idempotency key
`ORDER_CONSUMPTION_DOC:{docId}:MATERIAL:{materialId}`, which is unchanged.

**Doc status is derived from the material rows**, not stored independently: all consumed →
`POSTED`; any unconsumed for insufficient stock → `PARTIAL`; any technical failure → `CONFLICT`
(which still takes precedence, D94). One source of truth, no flag to fall out of sync.

**`errorDetails` JSONB is removed.** Its contents — material, required, available, UOM, reason —
become real columns on the material row, so they are queryable, indexable, and renderable without
parsing. The technical-failure detail (exception class and message) stays on the material row that
failed, alongside the same shape used for a shortfall.

> **Blast radius — display only, confirmed.** An earlier reading of this defect assumed the
> corrupted figure fed D94's own shortfall pre-check, compounding across docs. It does not: the
> pre-check reads `stockBatchRepository.sumOpenBatchTotals` directly, inside each material's own
> `REQUIRES_NEW` transaction, and never touches the availability service. Every doc's shortfall
> decision was taken against real open-batch totals.
>
> `stock_balance.quantity` and the batches were never corrupted either. The **−8** was `7 − 15`
> computed at render time in `StockBalanceService.mapWithOutstandingConsumption`. The stock engine
> was intact throughout; only the presentation layer lied. Had the pre-check gone through the
> availability service, a phantom **−8** would have made every subsequent doc believe it was short
> and cascaded `PARTIAL` docs indefinitely — worth recording as the failure this architecture
> happened to avoid.

**Both UOM layers are persisted, each naming its own unit.** `requiredQuantity` and
`availableQuantity` are **display** UOM (D87 layer 2) — the layer balances and open batches live
in — so availability can subtract them and the shortfall check can compare them without a
conversion. `enteredQuantity` carries the recipe item's own UOM, the ledger's entered layer,
because recalculate must rebuild the ledger command **without re-aggregating**. Routing the
display quantity through the ledger instead would add a second conversion boundary and change
posted numbers.

**Availability (D43) subtracts every known unposted consumption, regardless of why it is
unposted.**

| Doc state | Source of the outstanding figure |
|---|---|
| `PENDING` | on-the-fly recipe expansion — material rows do not exist yet |
| `IN_PROGRESS` | excluded — rows exist but are mid-mutation |
| `PARTIAL` | material rows where `isConsumed = false` |
| `CONFLICT` | material rows where `isConsumed = false` |
| `POSTED` | nothing outstanding |

> **Build note.** `OrderConsumptionAvailabilityService.OUTSTANDING_ROW_STATUSES` names the two
> states whose rows are written and final. The query groups by material across both, so a material
> outstanding on a `PARTIAL` and a `CONFLICT` doc in the same warehouse returns one summed row —
> double-counting is impossible by construction, not by guard. `PENDING` runs through a separate
> query on a separate repository, untouched. Pinned by
> `partialAndConflictDocsInOneWarehouseBothCountAndSumPerMaterial`, with negative checks confirmed:
> removing `CONFLICT` from the set, or removing the consumed filter, each fails a named test.

`PARTIAL` and `CONFLICT` are treated identically. They differ only in *why* the consumption has
not posted — a `PARTIAL` waits on a purchase invoice, a `CONFLICT` on a retry — and that
distinction says nothing about the stock. In both cases the food was sold and left the kitchen;
the material is committed either way. A rule keyed on the failure's cause would report a
`CONFLICT` doc's 15 KG as available while it is physically gone.

The sources never overlap: a doc is in exactly one state, and the material rows are written in
the same transaction that moves it out of `PENDING`. Because they are written from that same
aggregation, the figure is identical across the transition and the displayed number does not
jump.

**`IN_PROGRESS` stays excluded** (unchanged from D43). It is the one state where the rows exist
but are mid-mutation: some materials have posted and others are still being attempted, so any
figure read during it is stale before it renders. The pass completes in seconds and the doc lands
in a state that is counted correctly.

> **No migration.** Existing consumption docs are development data and are discarded.

### D97 — Document action buttons: one component set, one vocabulary, no split-button. ✅

Surfaced by a frontend audit of Purchase Invoice, Purchase Return, Waste, Physical Count, and
Order Consumption.

**Two parallel button systems exist.** Purchase Invoice, Purchase Return, and Waste render raw
`<button>` elements with ad-hoc `.pi-form-actions__*` CSS in a header topbar. Physical Count uses
the design-system `<Button>` in a bottom footer bar. Cancel is an icon-only `IconActionButton` on
two screens and a text `<Button variant="ghost">` on a third. This is not a styling
inconsistency — it is a second component system that has to be maintained alongside the first.

**All document screens use the design-system `<Button>`.** The `.pi-form-actions__*` classes are
removed. Destructive actions use `variant="danger"` with a confirmation, not an icon whose
meaning depends on recognising a glyph.

**One vocabulary per transition**, since the same lifecycle step is currently named three ways:

| Transition | Label |
|---|---|
| DRAFT → COMPLETE | **Complete** |
| COMPLETE → DRAFT | **Uncomplete** |
| COMPLETE → POSTED | **Post** |
| POSTED → COMPLETE | **Unpost** |

Document type is not repeated in the label — "Post", not "Post Invoice"; the screen already says
what document it is.

> **"Approve" is rejected, deliberately.** Purchase Invoice used it for DRAFT → COMPLETE. The
> backend status is `COMPLETE`, and "approve" belongs to the approvals workflow that is designed
> but not built (O20). Spending the word now on a transition that is not an approval would force
> a rename when approvals land, or leave two meanings for one word.

Physical Count keeps `Revert to Draft` — its lifecycle is genuinely different (freeze/reconcile,
D89) and forcing it into the same vocabulary would obscure that.

**No split-button.** These screens have one primary action and one or two secondary ones; a
dropdown adds a click to daily work and hides a step behind a control the user has to discover.
Revisit only if a screen reaches four or more concurrent actions.

**Placement is per screen and stays as it is.** Header topbar for form screens, footer bar for
Physical Count's stepped flow. Both are defensible for their layout, and moving them is churn
disguised as consistency.


### D98 — Loss reports: shrinkage, waste, comparison, and price drift. ✅

Four reports over the ledger and the batch table. Together they answer where stock is being
lost and where it is getting more expensive — the two questions that recover money.

**Shrinkage** (`/api/inventory/reports/shrinkage`) — `reference_type = 'PHYSICAL_COUNT'`,
grouped by material. The gap has no recorded cause by definition (D89); that is precisely what
makes it worth reporting, since nothing else in the system explains it.

**Waste analysis** (`/waste-analysis`) — `reference_type = 'WASTE_DOCUMENT'`, grouped by
**(material, reason)**. The reason is what makes it actionable: "80 kg wasted" prompts nothing,
"60 of it expired" prompts a purchasing change. `reason_code` is copied onto the ledger row at
write time, so this needs **zero joins**. Flat, not nested — O22 reserves the `grouped` archetype
for a report that genuinely cannot flatten, and this one can.

**Loss comparison** (`/loss-comparison`) — both losses side by side per material. The **ratio is
the diagnosis**: high waste with near-zero shrinkage is a storage or purchasing problem; high
shrinkage with near-zero waste is a control problem; both high means the waste figure is
probably masking part of the shrinkage.

Driven from `Material` with a LEFT JOIN to the ledger, so materials with **no** losses still
appear — a clean result is an answer, not an absence. Consequence: every window/warehouse/type
predicate must sit in the `ON` clause; moving any to `WHERE` silently reverts it to an inner
join and drops exactly the rows the report exists to show. Pinned by a test asserting the
warehouse filter *zeroes* a row rather than removing it.

Rows with zero on both sides sort **last**, via an explicit partition — a lone
`ORDER BY ABS(...)` puts zeros in the middle, between the negatives and the positives.

**Purchase price drift** (`/purchase-price-drift`) — first vs last purchase price within one
range, per material, sorted by absolute percentage change. One period, not two: simpler to ask
for and it compares prices that actually occurred rather than computed averages.

**Cross-cutting rules established here:**

**Signs follow the data, not a house style.** Waste is always an outflow, so a minus on every
row adds nothing — positive magnitudes. Shrinkage keeps its sign, since a surplus reveals a
wrong recipe or a rushed count. The comparison report therefore carries **both conventions in
one row**, documented in the field javadoc, the class javadoc, and the OpenAPI description,
because a renderer applying one formatter to all four columns would turn a surplus into a loss.

`totalValue = wasteValue − shrinkageValue` and may go **negative** when a surplus exceeds the
waste. Arithmetically right; the UI must render it as a net gain rather than a loss with a minus.

**Price drift applies no UOM conversion, and that is correct.** `StockBatch.unitCost` is already
display-UOM (D87 layer 2), unlike ledger quantities. Copying the conversion logic from the
ledger reports would multiply every price by the conversion factor — pinned by a test using a
material with a 1000× gram/kilogram gap.

**Price drift reads purchase-origin batches only** (`source_invoice_id IS NOT NULL`). Batches are
also opened by count surpluses at the balance's running average (D89), opening balances, and
transfers — each would register as a phantom price movement. **Reversed purchases are excluded
too**: a cancelled invoice depletes its batch to zero but leaves `unitCost` intact, so a
mistyped-then-cancelled price would otherwise survive as a genuine price point.

**First and last are resolved by insertion id, not date**, so two purchases on the same day at
different prices order deterministically — consistent with FIFO (D10).

**`purchaseCount` replaces a single-purchase flag.** One purchase in range shows first == last
and 0%; the count says why without a badge. It also gates interpretation: +37.5% across two
purchases is noise, across twelve it is a trend.

**Purchase returns need no handling.** A return depletes the source batch's remaining quantity
and never touches `unitCost` — buy 10 at 100, return 3 at 30, and 7 at 70 is the same unit cost.

> **Build note.** Indexes: V41 `(tenant_id, reference_type, movement_date)` — measured 6.7ms →
> 3.3ms at 400k rows; V42 partial `(tenant_id, movement_date) WHERE source_invoice_id IS NOT
> NULL` — 20.1ms seq scan → 1.9ms index scan at 400k batches. `PhysicalCountService.REFERENCE_TYPE`
> extracted; three repository JPQL literals left in place (parameterising them would churn ~20
> mock call sites for no behavioural gain — coupling recorded in the constant's javadoc).
>
> **Known limitation.** A batch carries no unit of its own, so a historical price is labelled
> with the material's *current* `displayUom`. Changing a material's display UOM relabels past
> prices. Rare, documented in the query javadoc, not worth a schema change.
>
> **Scale note.** Loss comparison returns one row per material in the tenant when no category
> filter is given, since clean rows are included by design. The frontend collapses them behind a
> count ("740 materials with no losses"); making `categoryId` required was rejected — the most
> valuable use is "show me everything" and forcing eight passes to get it defeats the report.
 
---

### D99 — Report presentation: answer first, filter-gated, per-warehouse. ✅

The first four report screens read as **pages, not reports** — a filter bar, a table, and nothing
telling the user what the answer was. Stock Valuation opened with nine columns including three
raw ID columns, no total anywhere, and horizontal scrolling to reach the value.

**A report answers its question before the table. The table is the evidence, not the answer.**

**Header block, three lines:** title plus a stable report code; a **method line** (what the
figures mean and as of when, with tenant and scope) so an exported PDF is attributable; and a
**filter sentence** — the applied filters as readable prose, not a row of controls.

**Summary strip** of 2–4 figures above the table. **`12 of 14` is mandatory wherever rows can be
excluded or degraded** — it is what makes a shrinking number visible instead of silent.

**Totals are computed client-side**, from the rendered rows. The API returns none, per D86. This
is not a compromise: a server-computed header contradicts the visible rows the moment the user
filters or re-sorts, leaving two disagreeing numbers on one screen. Every total on a flat report
is fully derivable from what is displayed.

**Table:** raw ID columns dropped (kept in CSV export); sorted by value descending; the value
column carries visual weight; numbers right-aligned with tabular figures; units secondary to the
number; a totals row; empty categories labelled explicitly rather than left as a dash.

**Filter-first flow, for date-ranged reports only.** No filter params in the URL → a filter
screen, nothing fetched. Params present → the report. A report that opens on a default range
makes the user read a number they did not ask for.

**Filters live in query params, not component state** — load-bearing, not stylistic. It makes the
report survive a refresh, shareable as a link, and reduces "Edit filters" to navigating back to
the paramless URL, which makes the browser back button correct for free.

Quick ranges (this month, last month, last 30 days) beside manual inputs; choosing one still
writes explicit dates to the URL. A repeat user passes the filter screen every time, by design —
the value is that they saw and confirmed the range. If that becomes tiresome the fix is
**pre-filling with last-used values, never skipping the screen**.

**Current-state reports (Stock Valuation, Low Stock) are exempt** — they are as-of-now, take no
range, and a gate screen in front of them buys nothing.

**Filter by warehouse, never by branch.** Not every warehouse belongs to a branch — a central
warehouse feeds the branches and is attached to none, and it is typically the largest single
stock value in the tenant. A branch filter makes it unreachable. Warehouse is also the unit that
matters: stock lives in one, minimums are set per one, and a purchase order is raised for one.
The warehouse column stays in every table, always.

**Aggregation across warehouses differs by report, and the difference is load-bearing:**

- **Stock Valuation aggregates.** The same material across warehouses sums into one figure — that
  is the number being asked for. Per-warehouse subtotals precede the grand total.
- **Low Stock must never aggregate.** The minimum is per (material, warehouse). A material below
  minimum in one warehouse and well stocked in another would, summed, appear above threshold and
  **disappear** — leaving the empty warehouse unflagged, which is the exact failure the report
  exists to prevent. **One row per (material × warehouse), always**, sorted by shortfall.
  **`Recalculate` removed from Stock Valuation.** It posted unposted consumption — a **write action
  on a read-only screen**, reachable by anyone with report-view permission, and its presence
  implied the displayed number needed fixing before it could be trusted. The problem it addressed
  is now solved where it belongs: a physical count settles outstanding consumption before its
  snapshot (D89). Replaced with a plain Refresh, plus a header line when unposted consumption
  exists.

**No shared report component.** CSS classes are shared and must be; components are not. The
family is young and the reports still to come (P&L, food cost) will not all be flat sorted
tables — an abstraction shaped around today's set will be wrong for them and expensive to
unwind (D13).
 
---

### D100 — Sales reports: over time, by hour, by product, by payment method. ✅

Four endpoints under `/api/orders/reports`, the first reports sourced from Orders rather than
Inventory. Permission **`REPORTS_VIEW_SALES`** — seeded in V2 and previously unclaimed by any
Java code. `REPORTS_VIEW_PRODUCTS` remains unclaimed and is a one-line change if a split is ever
wanted.

**Fixed inputs:** `order_date` for the date, `COMPLETE` status only (the enum has exactly two
values, `COMPLETE` and `CANCELLED`; a DB CHECK forbids a COMPLETE order carrying a cancellation
stage, so the status filter is provably sufficient), `Order.paymentMethod` as a single non-null
enum field.

**Grouping is fixed per report and is never a parameter** (D86). Hourly is therefore its own
endpoint, not a granularity switch on the daily one. Filters (branch, cashier, order type)
narrow the scope; they never change the grouping.

**Show components, never one blended number.** Every money row carries `subtotal`, `taxAmount`,
and `totalAmount` separately. **Tax is not revenue** — it is collected for the state, and folding
it in inflates the figure and guarantees the P&L will have to unpick it later.

**Sales by product is pre-tax and cannot be otherwise.** `taxAmount` lives on the order, not the
line, so attributing it across products would require inventing an apportionment rule.
`SUM(lineTotal)` is the honest figure. This is stated in the response documentation because
someone *will* sum this report and compare it to sales-over-time: the difference is exactly the
tax.

**Reports 1 and 3 must reconcile.** They aggregate the same orders over the same filters, one by
date and one by method, so their `totalAmount` sums must be identical. Pinned by four tests
including one that checks agreement **under every filter** — a predicate drifting between two
queries usually shows only when filtered, and each query looks correct in isolation.

**Zero-sales days are omitted, not zero-filled.** An absent day honestly reads as "no completed
orders" rather than "zero recorded", and zero-filling needs a server-generated date series. Note
this differs from loss comparison, where clean rows *are* included — there the material is the
subject and "nothing happened" is the answer; here the day is a bucket and an absent bucket is
not a finding.

**Sales over time is the one report not sorted by magnitude.** It is a time series; the shape
over time is the finding and reordering destroys it.

`cashierUserId` filters on `created_by`, not `shift.cashierUser` — the latter needs a join and is
null for pre-shift-feature orders, which would silently drop history.

**`SalesReportDateRange` deliberately near-duplicates `inventory.reports.ReportDateRange`.** That
one is package-private and throws an inventory error code; making Orders depend on Inventory
internals to validate two dates is worse coupling than six repeated lines (D13).

**`SalesByProductRow` omits `productCode` and `productNameAr`** because the `product` table has
neither column. Returning nullable fields the schema cannot fill is a promise it cannot keep.
See O29.

> **Build note.** V43 `(tenant_id, status, order_date)` — 24.3ms parallel seq scan discarding
> 396k rows → 2.2ms index scan, measured at 400k orders across 12 branches (a single-branch
> seed flatters the old index and would have understated the case). Native SQL throughout,
> matching `aggregateByShift`'s precedent. Reports 1/1b/3 in `OrderRepository`, report 2 in
> `OrderLineRepository` since its grain is the line.


### D101 — Timestamps are tenant-local wall clock; the zone is a property of the tenant. ✅

The system deploys to servers outside Egypt and will onboard Gulf tenants alongside Egyptian ones.
Before this, every timestamp was written with `LocalDateTime.now()` — the **JVM's** zone — so which
wall clock a row recorded depended on where the server happened to sit.

| # | Decision |
|---|---|
| 1 | `Tenant.timezone` — `VARCHAR(64) NOT NULL`, IANA zone id, **never** a numeric offset, **no DB default** |
| 2 | `Branch.timezone` — nullable override, same format |
| 3 | Resolution: `branch.timezone` → `tenant.timezone`. **No third fallback.** Missing zone fails loudly |
| 4 | Storage stays tenant-local wall clock in existing `LocalDateTime` / `TIMESTAMP` columns. **No `Instant`, no `TIMESTAMPTZ`, no column type changes** |
| 5 | Every write site uses `LocalDateTime.now(zone)`, never `LocalDateTime.now()` |
| 6 | Business dates stay `LocalDate`, unconverted. Only their conversion *to* a timestamp takes a zone |
| 7 | Audit timestamps go through `TenantTimestampListener` |

**The audit hook reads the tenant off the row, not from ambient context.** A `TenantContextHolder`
ThreadLocal was designed and rejected: it contradicts the explicit-`tenantId` convention used across
38 controllers, empties silently at every async boundary, and leaks across pooled threads.
`TenantAwareEntity` already carries `tenantId`, so `TenantTimestampListener` takes it from the entity
being saved. The payoff is that the D58 consumption scheduler needed **no changes** for audit
timestamps — each row it writes knows its own tenant. A missing `tenantId` throws; it never falls
back to server time, because a silent fallback produces a plausible-looking wrong row.

`TenantTimeZoneService` reads through `JdbcTemplate` rather than the repositories: its main caller
runs inside `@PrePersist`, part-way through a Hibernate flush, where loading an entity through the
same persistence context risks a re-entrant flush. Zones are cached indefinitely and evicted by the
two services that can change them.

**Scope.** Gulf zones (`Asia/Riyadh`, `Asia/Dubai`) are fixed-offset. Egypt is not — DST was
reinstated in 2023, so `Africa/Cairo` repeats the 23:00–00:00 wall-clock hour once a year on the
last Thursday of October. Storing tenant-local wall-clock makes timestamps inside that hour
ambiguous. This is a deliberately accepted limitation, not an oversight — see **O34** for the
exposure analysis and the remedy if it ever becomes real. A tenant in a zone with a different DST
schedule than Cairo's still requires review before onboarding, since the widened scheduler window
(**O33**) assumes a bounded offset spread.

**Two corrections to the premises this work started from, both verified:**

1. **`atStartOfDay()` was never a timezone defect.** The task was opened on the claim that
   `invoice.getReceiptDate().atStartOfDay()` "resolves against the JVM default". It does not:
   `LocalDate.atStartOfDay()` is `LocalDateTime.of(date, MIDNIGHT)` and reads no zone at all. For a
   `LocalDateTime` column, `atStartOfDay()` and `atStartOfDay(anyZone).toLocalDateTime()` are equal
   for every zone. The explicit form was adopted anyway — it states which day boundary is meant and
   stays correct under a future `Instant` migration — but it fixed no live defect, and the report
   date ranges never returned different rows because of it. `LocalDateTime.now()` was the real bug.
2. **The FIFO `id` tiebreak already existed.** `findByStockBalanceIdAndStatusOrderByMovementDateAscIdAsc`,
   backed by `idx_stock_batch_open_fifo` from V36 and documented in D10. No change was needed.

**Build note.** Flyway max was **V43** (not V37 as this document claimed); V44 adds the columns, V45
drops the `created_at` defaults. Verified post-migration: `tenants.timezone` is `varchar(64) NOT NULL`
with no default, `branches.timezone` nullable, 7 existing tenants backfilled to `Africa/Cairo`, and
0 of 50 `created_at` columns retain a default while all 50 remain `NOT NULL`. Also note this document
was three entries stale (`D98`/`O29`/`O30` were already taken) when the work was specified — the repo
is authoritative.


## OPEN (undecided — do NOT present as decided)

### O1 — Shortfall retroactive COGS correction.

Deferred to the Orders module. Today the shortfall is priced at current average with no back-correction (D11). Whether
Orders will need a retroactive COGS adjustment is **not decided**.

### O2 — Aggregator API / webhook design.

> **V2-scoped.** Not a blocker on any current work — belongs to the deferred online/aggregator
> intake track (D23/D24).

Talabat / Otlob / Noon Food / Fawry ingestion. Manual entry ships first; the automated API/webhook contract (auth,
dedup, mapping to the unified `Order`) is **not decided**.

### O3 — Approval-workflow config surface.

The `ApprovalWorkflow` entity is planned, but *what* is configurable (per document type, per threshold, per role, per
tenant) and the storage/UI shape are **not decided**.

### O4 — Enum-value translation approach.

Enum values need localized labels on the FE, but the mechanism (per-value keys vs a generated map vs backend-supplied
labels) is **not decided**. Partial per-value keys exist today.

### O6 — POS → system order ingestion transport, and whether the POS can echo `IncomingOrderRequest.id` back.

> **V2-scoped.** Not a blocker on any current work — belongs to the deferred online/aggregator
> intake track (D23/D24).

Exact endpoint/payload shape for the POS → system order ingestion call (single API call vs. queue/integration layer) is
**not decided** — the POS itself hasn't been built/designed yet. Whether the POS integration can support echoing back
the internal reference we send it (needed for D24's linking mechanism) is an unverified integration requirement.

### O7 — Aggregator branch-selection mechanism.

> **V2-scoped.** Not a blocker on any current work — belongs to the deferred online/aggregator
> intake track (D23/D24).

Whether/how Talabat, Uber Eats, breadFast, etc. communicate which branch an order is for is **not decided** — depends on
each aggregator's actual API, not yet reviewed.

### O8 — Whether third-party payloads arrive pre-normalized or need per-aggregator adapters.

> **V2-scoped.** Not a blocker on any current work — belongs to the deferred online/aggregator
> intake track (D23/D24).

Whether Talabat/Uber Eats/breadFast send a unified payload shape (via some intermediary) or each requires its own
mapping/adapter is **not decided**.

### O9 — Tenant-created custom roles.

Deferred from V1. If built, `Role` will need a nullable `tenantId` column (NULL = global/ default role, non-null =
tenant-specific custom role) — same nullable-tenant pattern as
`Uom`. Not a current blocker; schema change is additive whenever it's picked up.

### O10 — Fixed Assets: cost-coverage report against net profit.

Deferred until the P&L/accounting module exists (D50). Whether coverage will be computed against manually-entered net
profit or fully system-derived profit (Orders revenue − COGS − payroll − other expenses) is **not decided** — revisit
once the accounting module's design starts.

### O11 — Loyalty: points system (earn/redeem rules, expiry, sync timing).

Deferred out of V1 entirely (D53). Earn rule (percentage of invoice vs. flat per-currency-unit vs. flat per-order),
redemption mechanics (minimum balance, conversion to discount), expiry policy, and whether it's computed synchronously
at order time vs. via a batch job are all **not decided**.

### O12 — Loyalty: offers/promotions design.

Flagged as important and planned for the roadmap (D53), but not designed. Whether it lives inside the Loyalty module or
as a separate module, and whether offers are global-per-tenant or targeted at a customer segment/tier, are **not
decided**.

### O13 — Loyalty: Customer data Change Request / approval workflow.

Principle agreed (staff can request a change to a customer's `name`/`phone`; a user holding a new
`CUSTOMER_DATA_APPROVE`-style permission approves it manually — no automated verification required, e.g. no forced
confirmation call) but not designed. Open: whether the request stores a diff or a full new snapshot, and whether
multiple concurrent pending requests against the same customer are allowed (leaning yes, to keep it simple) or should be
constrained. Not building any schema/endpoint for this until picked up.

### O14 — Loyalty: customer spend/visit reporting and metrics.

The original motivation for the module (total spent, online vs. in-branch split, cash vs. card split, visit frequency
per customer) is explicitly **deferred until after the base Customer↔Order link (D53–D57) ships**. Whether this ends up
as live queries directly against
`Order` (no new tables — consistent with D13) or denormalized fields on `Customer` (mirroring the `lastPurchase*`/
`lastCount*` pattern in Inventory, D5) is **not decided** — note that
`Order.orderSource` and `Order.paymentMethod` already carry the online/offline and payment-method dimensions, so no new
raw data capture is anticipated, only aggregation.

### O15 — Loyalty offline sync queue mechanism.

Only the ordering principle is decided (D57: customer registrations sync before orders in any device's offline queue).
Queue data structure, retry/backoff, and conflict resolution are **not decided** — deferred to when the Orders module's
general offline capability (ROADMAP §1)
is designed.

### O16 — Accounting / P&L module design.

> **Sequenced, not immediate.** High priority overall, but gated behind two prerequisites, in
> order: (1) the Reports module shell (D84) ships first, (2) the `Expense` entity ships second,
> standalone and usable on its own, before (3) the full P&L report is assembled on top of both.
> Do not build P&L schema/endpoints out of order. Open questions below still apply regardless of
> sequencing.

Agreed so far: **no** Journal Entries, no Chart of Accounts, no Balance Sheet, no Equity tracking — explicitly rejected
for now, not deferred-as-a-gap. The only new entity is `Expense`, append-only, carrying `tenantId`, `branchId`, `date`,
`amount`, `category`, `referenceType`,
`referenceId` — no pre-aggregated totals stored anywhere. Fixed Assets are excluded from this module's figures per D50
(cost-coverage/ROI against profit stays blocked on this module — see O10, which this module unblocks once built).

**Not decided:**

- `Expense.category` — fixed backend enum (mirrors Assets' `category`, D47) or free text?
- Whether `Expense` needs an approval/lifecycle step, or any user holding a to-be-named
  `EXPENSES_MANAGE` permission can log one directly with no draft/post stage (unlike Inventory documents, D6/D7/D8).
- **COGS timing in the P&L report**: `OrderConsumptionDoc` reaching `POSTED` is what actually moves the ledger
  (D42/D58). A P&L run while docs sit `PENDING`/`IN_PROGRESS`/`CONFLICT` would show revenue with understated or missing
  COGS. Two candidate resolutions, neither chosen:
  report only over `POSTED` docs and surface a "N docs excluded, pending processing" flag, or block the report entirely
  while any doc for the period is unresolved.
- Decision number reserved as **D-next-free** once this is picked up — do not assume D83/D84/D85 numbering will still be
  adjacent when it lands, other decisions will have been added between now and then.

### O17 — `useDocumentDraftForm` shared hook: whether/when to extract.

> **Deprioritized — not scheduled.** The D13 threshold is met (3 concrete callers: Purchase
> Invoice, Purchase Return, Waste — see D85), so the abstraction is justified whenever it's
> picked up. Not urgent because the duplication is stable, not actively causing bugs.

Scope is Purchase Invoice / Purchase Return / Waste only. Physical Count is explicitly **not**
a fourth caller — it has a different lifecycle (freeze/reconcile) and should not be forced into this hook's shape just
because it also involves a document with lines.

### O18 — `confirmPay()` / `cancelOrder()` not yet routed through the D72 outbox.

These two POS actions still call the backend directly rather than through the sync outbox/idempotency mechanism built
for order creation (D72). This means they remain exposed to exactly the failure mode D72 was built to close for order
creation: a network drop mid-call has no retry, no durability, no idempotent replay. Not a regression (order creation is
what mattered most — payment confirmation and cancellation are lower-frequency, later-in-lifecycle actions) — but a
real, currently-open gap. Natural follow-up once D72's pattern is proven in production; whether these need the *same*
idempotency-key mechanism or a different one (payment confirmation in particular may have different retry-safety
requirements than order creation) is not decided.

### O19 — `device_auth` not wired as real auth source of truth (candidate, unconfirmed).

Surfaced from working notes, not verified against the current file set or code. May overlap with, or may be distinct
from, D33's already-documented "signed device JWT" hardening item — not yet checked which. Do not treat as decided or
even as a confirmed gap until reviewed; listed here only so it isn't silently dropped. Confirm scope (or discard) on a
future pass.

> **Numbering note**: O5 does not exist — number skipped, not lost content. O16/O17/O19/O20 above
> were previously unused or newly reserved numbers, now filled with real content in this pass.

### O20 — Workflows/Approvals: permission-tab redesign first, `DocumentHistory` logging mechanism second.

> **Sequencing locked, design not started.** Explicit two-phase order: (1) redesign the
> Permissions Tab and a new Workflows/Approvals screen in the admin web UI, (2) only after that
> lands, decide and wire how actions get logged to `DocumentHistory`. Do not build the logging
> side first even though it looks like the simpler half — the UI shape decided in phase 1 may
> change what phase 2 needs to capture.

**Settled direction (not yet a decision, still subject to phase-1 redesign):**

- No new gating table — reuses the existing permission model (D36); the gate stays permission-based, nothing new to
  configure per tenant.
- `DocumentHistory` (currently a dormant entity with no repository/service reference — see PROJECT.md) is the intended
  audit-log target once wired: one row per (actor, action, entityType, entityId, timestamp) on Purchase Invoice
  post/unpost, Purchase Return post/unpost, Waste post, Physical Count reconcile, Asset Disposal, Asset Maintenance.
- Assignment is per-user, not per-role and not tenant-toggleable.

**Genuinely open, blocked on the phase-1 redesign:**

- How an existing permission gets flagged as "approval-type" for the new tab's grouping — no such concept exists today.
  Candidates: a naming convention on permission codes (`*_POST`,
  `*_UNPOST`, `*_RECONCILE`, plus the Asset disposal/maintenance permissions) matched at render time (D13-clean, no
  schema change), vs. an actual `isApprovalType` column on `Permission`
  (schema change, more explicit, easier to get wrong at seed time). Not chosen.
- Whether `DocumentHistory.action` is one plain string covering both state-transition actions (post/unpost/reconcile)
  and record-creation actions (Asset Disposal, Asset Maintenance), or needs a `changeType` discriminator. Not chosen.
- **Fate of ROADMAP §3 / O3** (`ApprovalWorkflow` config entity, per-tenant configurable transitions). This plan looks
  like a replacement for that idea, not a deferral of it — but that's not confirmed until phase 1 is designed. O3 stays
  open and un-rejected until then.

### O21 — Reports AI assistant (Phase 2). Not a simple NL→endpoint mapper — needs to handle complex/comparative queries

(e.g. "compare this month vs last," "which branch wastes more"), meaning it must call multiple report endpoints with
different filters and reason across results, not just map one phrase to one call. Likely shape: LLM with tool-calling,
where each report (id, description, filter schema, column schema — i.e. the report registry) is exposed as a callable
tool; the assistant chooses which to call, with what params, possibly several times, then composes the answer. No
backend aggregation/summary field needed — the assistant reasons over raw rows per call. Not designed further now: model
choice, cost/latency of multi-call reasoning, and how much history/context it needs are all open. This sprint's only
obligation to it: keep report ids stable and each report's filters/columns cleanly typed, since that becomes the tool
schema later.

### O22 — Report archetypes B/C/D and tenant-customized reports. Reports collapse into ~4 renderer archetypes, discriminated by ReportConfig.type: flat (homogeneous rows, optional pagination), grouped (rows + group key + per-group subtotals), statement (sections → line items → subtotals → net; P&L is the only known case), comparison (rows × dynamic period columns). Only flat is built — it has 2 real callers (Stock Valuation, Low Stock). The other three are not designed: their exact config/response shapes are deliberately not guessed, and each is to be defined by its first real report (P&L will define statement; a by-supplier/by-reason report will define grouped). The discriminator field ships now so adding a renderer later doesn't require touching the flat path — one loader, N renderers, selected by config.

Tenant customization splits into three cases, none decided: (a) same data, different presentation (hidden columns,
default filters, custom sort) — the ~80% case, would be a report_preset row (tenantId, reportId, visibleColumns,
defaultFilters, sortBy) merged over an existing config at load time, needing zero new queries; the current design
preserves this seam (stable report ids, columns-as-data, declarative filters) but nothing is built. (b) tenant-specific
custom fields — blocked on a custom-fields mechanism on entities, which doesn't exist and is a larger decision than
reporting. (c) genuinely novel queries (new joins/business logic) — not configurable by any honest mechanism; options
are a query builder (rejected direction: large, injection-prone, unbounded performance) vs. sysadmin-authored SQL views
registered against the generic endpoint (real security/ops surface) vs. staying dev work (current de-facto answer, and
likely correct for this market given expected volume). Not decided; do not build any of the three now. Related fork, not
decided: whether the report catalog stays FE-owned (ReportConfig objects in the admin app — current state, correct while
the admin app is the only consumer) or becomes BE-served via a registry endpoint (GET /api/reports/registry, returning
id/type/filters/columns/permission per report). The trigger for switching is a second consumer that cannot read FE
code — the AI assistant (O19) or tenant-defined reports (O21a/c). Note this is a registry endpoint, not an envelope on
each report's data response: report responses stay bare arrays (D86).

### O23 — Post-freeze movement endpoint returns stock-UOM aggregates with no UOM field.

Surfaced during the D87 pass. The endpoint's response carries raw stock-UOM aggregates and
no unit descriptor, while every count-line quantity rendered beside it is display UOM (D87).
Any consumer placing the two side by side displays a wrong comparison, and nothing in the
payload prevents it.

**Not decided:** whether to (a) add an explicit `uom`/`uomCode` field and leave the values in
stock UOM, or (b) convert the aggregates to display UOM server-side so the whole screen speaks
one unit. (b) is more consistent with D87's "same-layer only" rule; (a) is the smaller change
and preserves the canonical figure.

**Blocking status:** must be resolved before any FE consumes this endpoint. Confirm current
FE usage first — if already wired, this is a live display bug, not a latent one.

### O24 — `minimumQuantity` unit. ✅ RESOLVED

**Decided: the threshold lives on `StockBalance`, not on `Material`, and is expressed in the
material's display UOM.**

**Per warehouse, not per material.** A central kitchen and a satellite branch store cannot share
one reorder threshold — the same material warrants a different minimum in each location. The
threshold is a property of stock in a place, not of the material itself.

**Display UOM, always.** A warehouse speaks exactly one unit for a material — its `displayUom`
(D87 layer 2) — and `StockBalance.quantity` is already in it. The threshold uses the same, so
D86's Low Stock condition compares two figures in one layer with no conversion. Had it been stored
in stock UOM, every material whose stock and display units differ would have been reported as low.

**`NOT NULL DEFAULT 0`, and no `COALESCE` anywhere.** Zero means "no threshold configured", which
the `> 0` clause excludes — a material with no real minimum never surfaces as low stock, and
`quantity = 0, minimum = 0` is correctly not a shortage. A `COALESCE` over a `NOT NULL` column
would be dead code that also masks a future nullability regression instead of surfacing it.

**Kept beyond the report, deliberately.** Reaching the threshold should raise a notification
(O28), and further reports are expected to consume it — hence a first-class stored value rather
than a filter parameter.

> **Verified, not assumed.** Two write paths exist (`StockBalanceService` add-material and
> update-stock-settings, both from `WarehouseController`); both store the request value verbatim —
> the only transform is a null-to-zero guard, and neither touches `UomConversionService`. All four
> readers (the Low Stock report, the warehouse stock filter, the `isBelowMinimum` response flag,
> and the shortfall calculation) compare fields of the same `stock_balance` row, so they are the
> same layer by construction. Live proof of that layer: a material with stock GRAM / display
> KILOGRAM shows `stock_balance.quantity = 99.93` against a stock-UOM ledger summing to
> `99,930` — a clean 1000× factor.

> **Follow-up (F10).** A dormant duplicate exists: `Material.minimumStockLevel`, nullable with no
> default, writable through the material create/update API and echoed in responses — and read by
> nothing. It cannot corrupt the low-stock logic today (0 of 29 materials populate it), but a
> client can set it, have it accepted and returned, and see the material never surface as low
> stock: a fully silent failure. It also contradicts this decision in letter. Drop the column.


### O25 — Count list `varianceCount` reads persisted line variance; detail computes it live.

The list endpoint's `varianceCount` (and `largeVarianceValue`) read the persisted
`line.variance`, which is null or stale until reconcile. The detail read computes it live
(D90). An `IN_PROGRESS` count therefore shows one variance count in the list and a different
one when opened.

Lower impact than the detail-screen bug that motivated D90 — a summary figure in a list, not a
number an irreversible action is confirmed against — but a visible contradiction between two
screens showing the same document.

**Not decided:** whether to (a) run the D90 computation for list rows, which means the movement
query per listed count and could be expensive on a long list, (b) drop the figure from the list
for unreconciled counts and show a neutral placeholder, or (c) label it as pre-count. (b) is
cheapest and arguably the most honest — the number is not meaningful until the count is done.

### O26 — Whether order consumption should exit early on repeated technical failures.

The per-material loop continues after a technical failure, and each subsequent material opens its
own `REQUIRES_NEW` transaction. Two failure shapes behave very differently under this:

- **Transient (deadlock, lock timeout):** continuing is correct — the rolled-back material failed,
  later ones may well succeed, and stopping would needlessly withhold consumption that could have
  posted.
- **Persistent (connection loss, pool exhaustion):** every remaining material fails individually,
  each attempting a transaction against an unavailable resource, producing one `errorDetails`
  entry per material for a single underlying cause.

**Not decided:** whether to add an early exit, and if so on what signal — a consecutive-failure
threshold, an exception-type classification, or a connection-health check. Each trades reduced
repeated failures against complete outcome collection and the chance for later materials to
commit.

**Not urgent.** Retry is a full re-run (D31) and the per-material idempotency key makes it safe,
so the cost of continuing is wasted work and a noisy `errorDetails`, not incorrect state. Revisit
if production shows docs failing wholesale on infrastructure faults.

### O27 — Document numbering: a single sustainable scheme across all document types.

The current per-type numbering is ad hoc and its mechanism is not shared. Purchase invoices use
`InvoiceSequenceService` (scoped by tenant/year/document type). Physical counts got their own
`PhysicalCountCodeSequenceService` during this audit, deliberately not reusing the invoice one —
its scope and format are invoice-specific, and its first-row allocation guards concurrency with
the unique constraint alone, with no recovery path (**F7**). Entity codes for master data follow
a third pattern entirely (D75, `{PREFIX}-{NNNN}`).

Three mechanisms, three formats, one of them with a known race.

**Not decided:** the format (does it carry branch? warehouse? year? document type?); the counter
scope; whether one shared allocator serves every document type or each keeps its own; whether
numbers are gap-free (which forces allocation at post, not at create) or may have gaps
(allocation at create, cancelled documents leave holes); and whether tenants can configure a
prefix or format.

**Constraint on whichever design wins:** allocation must be atomic, not check-then-insert. The
`INSERT ... ON CONFLICT DO UPDATE ... RETURNING` pattern used by
`PhysicalCountCodeSequenceService` is the proven shape here and should be the baseline — F7
exists precisely because the older service does not use it.

Existing numbers are never renumbered whenever this is picked up; a new scheme applies to new
documents only, same as D74's stance on `orderNo`.

### O28 — Notification service: scope, mechanism, and delivery surfaces.

Raised while deciding how a `PARTIAL`/`CONFLICT` consumption doc should reach the user (D94).
Deliberately **not** solved as a consumption-specific alert: notifications are a cross-cutting
need — approvals (O20), low stock, shift events, and others not yet enumerated — and a one-off
mechanism built for consumption would either stay a one-off or become the accidental foundation
for all of them.

**Not decided:** whether notifications are persisted entities or derived on read; whether they
carry read/unread state per user; how they are scoped (tenant, branch, role, permission); how
they expire or are cleared; and which surfaces deliver them (in-app, badge, email, push).

**A structural distinction to settle first.** D94's case is not an *event* — it is a *condition*
that persists until resolved and must clear itself when the doc reaches `POSTED`, with no user
action. A read/unread event model would leave it dismissed while still true. Whether one
mechanism serves both event-shaped and condition-shaped notifications, or they are separate
concerns, is the first question, not an implementation detail.

**Interim, agreed:** D94's visibility requirement will be met by a **Dashboard card when the
Dashboard is built** — reusing its existing stat-card pattern, showing the count of unresolved
`PARTIAL`/`CONFLICT` docs and the age of the oldest, and disappearing on its own when none
remain. That is not this open item; it is a concrete placement decision that does not need the
general mechanism.

> Until either lands, a `PARTIAL`/`CONFLICT` doc is visible only on the consumption screen and
> indirectly through the reduced available-quantity figure (D43/D94). A doc in that state means
> revenue was recorded while its cost was not, so profit is overstated until it resolves —
> recorded as a known gap, not an accepted one.

### O29 — Audit user columns: `createdBy`/`updatedBy` are populated by hand and ~40% of services never do it.

Surfaced during the D96 verification pass, when most rows were observed with null `updatedBy`.
The audit that followed found the problem is narrower than it first looked in one direction and
much wider in another.

**Not a defect: the timestamps.** `createdAt` and `updatedAt` are handled centrally by
`@PrePersist`/`@PreUpdate` on `BaseEntity` and work on every write path — verified that nothing
bypasses them (the only `@Modifying` queries are deletes, both native queries are SELECTs, and the
only `JdbcTemplate` writes touch non-audited tables). A null `updatedAt` therefore means what it
should: **the row has never been updated**. Immutable rows like `orders` and `order_line` are 100%
null for exactly that reason.

> An earlier proposal to set `updatedAt = createdAt` on insert is **rejected**. It would discard a
> true distinction — never-modified vs modified — to spare consumers a `COALESCE`.

**The defect: the user columns have no mechanism at all.** Spring Data JPA Auditing is entirely
absent — no `@EnableJpaAuditing`, no `AuditorAware`, none of the annotations. `createdBy` and
`updatedBy` are set by hand, per service, at 75 call sites. The result:

- **26 services** set them somewhere, several only on create and never on update
- **16 services never set either** — Asset, AssetLine, Branch, StockBalance, Uom, MaterialCatalog,
  MaterialCategory, Material, Supplier, Warehouse, Customer, Tenant, TenantUser, User, and others
- Flyway seeds insert with no `createdBy` at all (560 `user_permissions` rows)
- The consumption scheduler passes `null` explicitly, having no security context

Roughly 40% coverage, from code nobody wrote intending to leave it out. That number is the
argument: a per-service fix restores 100% today and depends on every future service remembering,
which is precisely the assumption already disproved.

**Direction agreed — extend the existing `BaseEntity` hooks, not Spring Auditing.**

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) createdAt = LocalDateTime.now();
    if (createdBy == null) createdBy = CurrentUser.idOrSystem();
}
```

The hooks already exist, already fire on every path, and are already where anyone looks for this
behaviour. `@EnableJpaAuditing` + `AuditorAware` would give the same guarantee, but adds a Spring
mechanism whose population step is invisible at the point of use — and the existing hook is the
mechanism this codebase already trusts for the timestamps. The `if (... == null)` guard keeps
explicit service-level attribution working where a service genuinely needs to name a different
actor.

A small helper (`CurrentUser.idOrSystem()`) isolates the `SecurityContextHolder` read so the
entity holds no knowledge of Spring Security. Reading a static context from an entity is not
elegant, but it is what `AuditorAware` does too — here it is explicit rather than hidden.

**Not decided:**

- **The no-actor sentinel.** The consumption scheduler and any future background write have no
  user. `null` is ambiguous — it cannot be distinguished from "a service forgot". A sentinel
  (`0L`, matching the existing `SYSTEM_TENANT_ID = 0L` precedent) states plainly that the system
  did it. A real system user row from `V4__sysadmin_user_seed` is a third option, but conflates a
  human account with an automated actor.
- **The 75 existing manual call sites.** With the hook in place they are redundant, and any that
  writes a *wrong* value now silently overrides a correct one. Remove them in the same pass, or
  leave them and accept two mechanisms?
- **Backfill and `NOT NULL`.** Existing rows carry null `created_by`. Backfilling to the sentinel
  and making `created_by NOT NULL` is what actually prevents recurrence — the constraint, not the
  convention. `updated_by` must stay nullable: null there means "never updated", which is true and
  worth keeping.

> **Blocked on a prerequisite: attribution is currently spoofable.** Most services take `userId`
> from `X-User-Id`, an **optional, client-controlled header that is never validated against the
> JWT**. Any caller can omit it (null attribution) or send another user's id. The authenticated
> id is already in the security context — `JwtAuthenticationFilter` sets a `CurrentUserPrincipal`
> on every bearer-token request, and the HR/jobs/RBAC services already read it via
> `CurrentTenantProvider.getActorUserId()`.
>
> Centralising attribution on top of a spoofable source would produce a complete, uniform, and
> untrustworthy audit trail — worse than a patchy honest one, because it looks reliable. The
> security-layer work of sourcing identity from the token everywhere is **deferred as its own
> effort**; this open item is sequenced behind it. When it lands, `CurrentUser.idOrSystem()` reads
> the principal and `X-User-Id` disappears from the audit path.

O30 — Spring request-binding failures return HTTP 500 with a stack trace, app-wide.

Surfaced while binding required date params on the shrinkage/waste reports. The report feature worked around it with required = false plus explicit validation; the gap itself is untouched and affects every controller in the system.

Observed (probe against the real GlobalExceptionHandler, since removed) — seven binding exceptions all fall through to the catch-all and return an identical, information-free body:

json
{"errorCode":"INTERNAL_ERROR","message":"An unexpected error occurred",
"params":{},"status":500,"path":"...","fieldErrors":null}

MissingServletRequestParameterException, MissingRequestHeaderException, MethodArgumentTypeMismatchException (param and header), HttpMessageNotReadableException (malformed and absent body), MissingServletRequestPartException.

GlobalExceptionHandler handles only AppException, ApiException, MethodArgumentNotValidException, DataIntegrityViolationException, AccessDeniedException, NoResourceFoundException, HttpRequestMethodNotSupportedException, then Exception. It does not extend ResponseEntityExceptionHandler, which is what would normally supply these.

Blast radius — 49 controllers, 228 handler methods:

binding	count
@RequestHeader required	166 (160 of them X-Tenant-Id)
@RequestBody	93
@RequestParam typed (mismatch reachable)	78
@RequestParam required	1
@RequestPart / multipart	0 — none exist

The headline is headers, not params. The missing-param case that surfaced this affects one endpoint app-wide; the missing-X-Tenant-Id case affects essentially every endpoint and is the one most likely to be hit in practice — a client that forgets the header, or a misconfigured gateway.

The log flood is worse than the status code. The catch-all logs log.error with a full stack trace on every malformed request. A crawler or a broken integration hitting endpoints without X-Tenant-Id floods the error log, and a genuine server fault becomes indistinguishable from client noise. Any alerting keyed on ERROR is already unreliable.

The gap is known and has been worked around at least twice. OrderControllerSecurityTest:80 — createRejectsMissingBranchHeaderUsingExistingRequiredHeaderHandling asserts isInternalServerError(). Someone hit this, recognised it, and encoded the 500 as the expected contract rather than fixing it. The test name documents a bug as a feature. It must be updated and renamed in the same commit as any fix — and it breaking is the correct signal.

Recommended fix — a single @ExceptionHandler listing the five exception types explicitly, returning 400. One code path, one shape. Explicit membership beats catching ServletRequestBindingException, matching the reasoning already applied to findBackdatedConsumptionConflicts's explicit type list.

Risk is low and asymmetric: these paths return 500 today, so nothing that currently works changes behaviour. The changed surface is exactly the set that is already broken.

Rejected alternative: extending ResponseEntityExceptionHandler. It would handle all of these for free, but changes the body shape for every exception it owns — including ones currently working. Far larger blast radius for no additional benefit here.

Not decided — the response code:

(a) Reuse VALIDATION_FAILED with each binding failure as a FieldError. Zero new codes, and the frontend provably renders this shape already.
(b) Add CommonErrorCode.REQUEST_BINDING_FAILED(BAD_REQUEST). Semantically cleaner — a missing header is not a field validation failure — but a new code the frontend must learn.

(a) is the recommendation. CommonErrorCode currently has no 400-class code at all.

Blocking check before either is implemented: whether translateApiError (restaurant-saas-web/src/utils/errors.ts) has a graceful fallback for an unknown nested fieldErrors[].errorCode. This was not verifiable from the backend repo and is the deciding factor between (a) and (b). If nested unknown codes render badly, (a)'s reuse advantage disappears.

Deployment order matters. The frontend must handle the new shape before the backend starts emitting it, or the transition window shows a generic error where a useful one is intended — replacing one unhelpful message with another.

Two implementation notes for whoever takes this:

HttpMessageNotReadableException's message can leak Jackson internals (field names, class paths). Follow the catch-all's existing discipline: log the detail, return a sanitised payload.
Drop the log level to warn/debug. Keeping log.error preserves the stack-trace flood, which is half the actual problem.

Client-side retry policies and error boundaries in the web repo were not checked. A 5xx-keyed retry would stop retrying these — the desired outcome, but still a behaviour change worth confirming.


### O30 — `subtotal + taxAmount ≠ totalAmount`: components at scale 6, total rounded to 2.

Surfaced by the sales reports. `OrderService` computes `subtotal = Σ lineTotal` and
`taxAmount = subtotal × 0.14` at scale 6, then stores `totalAmount = round(subtotal + tax, 2)`.

```
subtotal 33.33 → tax 4.6662 → sum 37.9962 → stored total 38.00     gap 0.0038
```

Per order it is a fraction of a piastre. Across a day of report rows the three columns visibly
fail to add up, and an accountant reading the report will call it a bug. Across a tax period the
accumulated difference is real money in a filing.

**Reports are not the fix and were not treated as one** — all four sales reports reconcile on the
stored `total_amount` and none re-derive it, documented on the row DTOs and in the OpenAPI text.
The frontend states in the method line that the total is the stored rounded figure while the
components are unrounded.

**Not decided:** whether to (a) round `subtotal` and `taxAmount` to 2 decimals at write time so
the three always agree, (b) leave it and document, or (c) revisit the money scale for order
totals generally. (a) is the honest fix — the stored components would then match what is
charged — but it is a write-path change touching every order and needs its interaction with line
totals thought through. Tax compliance implications should be checked before choosing.

### O31 — `Product` has no Arabic name and no code.

`product` carries `name` only. `variant_label_ar` and `description_ar` are the only Arabic
columns on the table.

This contradicts a convention applied everywhere else — `Material` and the rest carry bilingual
AR/EN name fields — and its reach is far wider than reports: **product names render in Latin
script throughout an Arabic-first UI**, including the menu, the POS, and printed receipts.

Surfaced by sales-by-product, which correctly omits the fields rather than returning nulls.

**Not decided:** adding `name_ar` (and possibly `code`) to `product` is a menu-module change —
schema, CRUD, DTOs, validation, i18n, and a backfill or nullable period for existing rows. Scope
it as menu work, not reports work. Until then the sales-by-product report shows English names,
with cell-level direction handling so Latin text does not scramble the RTL table.

### O32 — Loss comparison returns one row per material when unfiltered.

By design (clean rows are included), so a large catalog produces a very long unpaginated
payload. The frontend collapses clean rows behind a count, which makes the screen readable but
does not reduce the transfer.

**Not decided:** whether pagination is warranted, and if so how it interacts with client-side
totals (D92 computes totals from rendered rows — paginating breaks that). Making `categoryId`
required was considered and **rejected**: the most valuable use of this report is "show me
everything", and forcing eight passes to get it defeats the purpose. Revisit only if it bites on
a real catalog.


### O33 — Multi-tenant scheduler cutoffs: the supported offset spread is bounded at 2h.

The D58 batching poll compares one cutoff against `created_at` values now stored in several
different wall clocks. A Dubai doc (+04:00) eight real hours old stores as `now-8+4 = now-4`, which
sits **above** a Cairo-computed `now-8` cutoff — so the age trigger never fired for it and the doc
waited roughly twelve hours instead of eight. Not severe (the 50-row count trigger is the practical
one; age is a backstop), but real.

**Resolved for now by widen-and-filter:** the query cutoff is widened by `MAX_OFFSET_SPREAD = 2h`
(Cairo `+02:00` ↔ Dubai `+04:00`) and the precise check is re-applied per tenant in memory, from a
projection that carries `tenantId`, `createdAt` and `lineCount` out of the same query. Over-selecting
discards a row; under-selecting strands a document, so the slack goes toward over-selecting.

**Not decided:** what happens when a tenant outside the Cairo↔Dubai span is onboarded. The constant
is a hard-coded 2h in `OrderConsumptionBatchingScheduler` and nothing enforces that tenants stay
inside it — a tenant in, say, `Asia/Karachi` (+05:00) would silently reintroduce the drift. Options
are deriving the spread from the distinct zones actually in use, or refusing to onboard outside the
supported set. Note the summer complication: with Egypt on DST, Cairo is `+03:00` and the real spread
is 1h, so 2h is a safe upper bound today but is not derived from anything.

### O34 — Egypt observes DST; the repeated hour is accepted.

`Africa/Cairo` repeats 23:00–00:00 on the last Thursday of October. Under D101's tenant-local
wall-clock storage, two rows an hour apart inside that window store identical values.

**Not exposed:**

* FIFO batch ordering — `id` is an explicit tiebreak (D10, `idx_stock_batch_open_fifo`, V36).
* Shift totals and Z-reports — aggregated by `shiftId`, not by a time range.
* Order aggregation — by `orderId` / foreign key, not by timestamp window.

**Exposed:**

* The physical-count netting window (D90/D93), `createdAt > frozenAt AND <= countedAt`. A freeze
  landing inside the repeated hour excludes movements from the second pass, which carry an
  identical stored value. Wrong variance, no error. Narrow — requires a freeze in that specific
  hour on that specific night.
* Any hour-bucketed report spanning the boundary merges two real hours into one bucket.

Accepted given the narrowness. **Remedy if it becomes real:** migrate only the
comparison-participating columns — `physical_count.frozen_at`, `physical_count_line.counted_at`,
`inventory_transaction.created_at` — to `TIMESTAMPTZ`. Display-only timestamps stay as they are.
This is a targeted fix, not a system-wide migration.

> **Two corrections found while verifying the above against the code — resolve before acting on the
> remedy.**
>
> 1. **The netting window's two bounds are on different columns.** The shorthand
>    `createdAt > frozenAt AND <= countedAt` reads as one value bounded twice. The actual clauses
>    are `t.createdAt > :frozenAt` (D93 lower bound) and `t.movementDate <= :maxCutoff` (D90 upper
>    bound, `maxCutoff` derived from each line's `countedAt`) —
>    `InventoryTransactionRepository:207-208`, and the per-line pass at
>    `PhysicalCountService:729` compares `movement.movementDate()` against `line.countedAt`.
> 2. **The remedy column list is therefore incomplete.** It omits
>    `inventory_transaction.movement_date`, which is what the upper bound actually compares.
>    Migrating only the three listed columns would leave a `TIMESTAMPTZ` `counted_at` being compared
>    against a wall-clock `movement_date` — strictly worse than today. Either add
>    `movement_date` to the list or confirm the upper bound is out of scope. Note `movement_date`
>    is midnight for purchases (`receiptDate.atStartOfDay()`) and so cannot fall in the repeated
>    hour, but it is a real clock time for physical counts, waste and consumption, which can.


## Negative Stock Batches (Order-driven Shortfall) — Deferred Feature

Deferred entirely, not a blocker for the Order module. Current assumption for V1: the user enters purchase invoices
regularly enough that open batches cover consumption; on a rare shortfall, the system falls back to the existing default
behavior (D1 — `StockBalance` allowed to go negative, D11 — priced at current average, no retroactive correction). No
negative-batch creation, no per-material shortfall ledger, no settlement mechanism — all deferred. If/when built: a
config flag (tenant/warehouse level) to opt in, negative-balance records scoped at material+warehouse level (not folded
into `StockBatch` itself, to avoid overloading its
"consumed from" responsibility), and settlement against new incoming batches handled as an internal linking/audit table
rather than a second `inventory_transaction` entry (no retroactive backdated ledger rows).

### F9 — Frontend lint: enforcement added, rule scoped, eight real defects fixed. ✅

Surfaced during the D87–D95 audit, when two separate frontend passes could not run targeted
ESLint because the files they needed to edit already failed it. A repo-wide sweep found **91
problems across 72 files**, and — more importantly — that **lint was manual only**: it did not
run in `build`, there was no pre-commit hook, and no CI config existed in the repo. Findings had
accumulated because nothing ever checked. Any file already failing was effectively unlinted, so
new code added to it went unchecked too.

**Enforcement.** `build` now runs `npm run lint` first, and a committed `.githooks/pre-commit`
lints staged `.ts/.tsx`, activated by a `prepare` script (`git config core.hooksPath .githooks`)
that runs on `npm install`. No new dependency — a six-line POSIX hook was chosen over
husky + lint-staged, since the project had neither and the behaviour is expressible directly.
Verified live: a failing staged file blocks the commit. Known trade-off, noted in the hook
itself: partially staged files are linted from the working tree, not the index.

> Existing clones need one `npm install` (or `npm run prepare`) to activate the hook.

**`react-hooks/set-state-in-effect` is disabled repo-wide**, with a dated comment in
`eslint.config.js` recording why. The rule is new in React 19 and this codebase predates it. Of
its 84 findings, ~79 were correct code in a shape the rule dislikes — modal resets
(`setError('')` on `open`), pagination resets (`setPage(0)` on filter change), and deferred
loaders. Rewriting them would be risk without benefit. The rule takes no semantic options, so the
config cannot distinguish those from genuine defects; a per-file override list would have been
manual classification dressed as configuration. **The 8 genuine findings were fixed by hand
instead, and are therefore not covered by any automated check** — that gap is deliberate and
recorded here rather than in a comment nobody will read.

**The 8 defects, with a corrected diagnosis.** The audit's initial assumption — that the four
`*OverviewPanel` files overwrite in-progress edits — was **wrong**: all four were `!editing`
guarded, so a mid-edit refresh already preserved drafts. The real defect was narrower and worse:
a stale draft surviving an **entity identity change** mid-edit, so Save could write one entity's
data onto another entity's id. Fixed by a render-time reset that reseeds while not editing
(unchanged behaviour) and reseeds mid-edit only on identity change. A parent `key` remount was
considered and rejected for these — three of the panels interleave view and edit in one tree with
view-mode fetched state, so remounting would refetch and flash.

`PhysicalCountInProgressView` was the one true clobber case, with no guard at all: the parent
refetches after every partial save, wiping counted quantities and notes typed since. Drafts now
seed once per count identity via `key={count.id}`, and `updateLineDraft` gained a fallback for
lines absent from the seed. **This one sat directly in the count-entry path — a lost counted
quantity is a wrong variance, in the same module this audit was auditing.**

Also fixed: `TenantCodeInput` (genuinely derived — computed during render, the state and effect
deleted), `ManualTransactionModal` (UoM defaulting moved into the material select's `onChange`
and the prefill lookup), and `MaterialCatalogImportModal` (selection pruning moved into
`loadCatalog` where items actually change; deselection stays permanent across filter changes,
since a derived-intersection approach would resurrect pruned selections).

**Two rules were temporarily downgraded to `warn` to unblock enforcement, then restored to
`error` once their six findings were fixed.** `react-refresh/only-export-components` (×5) was
resolved by moving non-component exports into sibling modules —
`FormControls.tsx`'s class-name helpers into `formControlClasses.ts`, and
`MenuCategoriesContext.tsx`'s hook and context object into `useMenuCategories.ts` — with
consumers unaffected via the existing barrel.
`react-hooks/preserve-manual-memoization` (×1, `useInventoryLookups.ts`) was subtler: the callback
closed over an `options` object whose identity changes every render for inline-literal callers, so
the compiler's inferred dependency was coarser than the manual one and it refused to compile.
Fixed by destructuring the two primitives at render scope; dependency values are bit-identical.

Three now-redundant `eslint-disable-next-line` directives in `RecipeVersionFormModal.tsx` and
`MenuProductFormModal.tsx` were removed.

**Remaining:** one warning — `react-hooks/exhaustive-deps` in `TableLayoutPage.tsx:199` (missing
`persistLayout`). Out of scope for that pass.

> **Verified by behaviour, not by lint.** `BranchOverviewPanel` was mounted in a temporary
> Vite harness under StrictMode with a real `LocaleProvider`: a typed draft survived a same-id
> source swap, reseeded on an id change mid-edit, and was discarded on cancel. Harness deleted
> before commit. A rule passing is not evidence a component behaves correctly, and for these
> eight the rule no longer runs at all.