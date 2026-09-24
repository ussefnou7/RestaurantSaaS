# DECISIONS

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155`, POS `03b0e81` on
> 2026-08-30 by Claude Code (doc drift audit — [../claude/DOC_DRIFT_AUDIT.md](../claude/DOC_DRIFT_AUDIT.md)).
> **Coverage was a light contradiction pass, not a full re-verification of every item.** Eleven
> entries were checked; the per-decision ✅/⚠️/❌/🕓 marks below predate this stamp and were not
> individually re-run. Findings from the pass, all still to be actioned except D20:
> D12 (inventory now fully migrated — the "7 services" paragraph is stale), D23 and D24 (intake
> entity/service/endpoints exist, so neither is enum-only, and linking is a server-side PATCH,
> not a POS echo), D57 and D65 (the POS offline outbox is built), O6 and O18 (both settled by
> code). **D20 was found to contradict the code outright and has been moved to OPEN.**
> Do not read an unmarked item as freshly verified.

> **D122 revised 2026-09-20:** force close is decided by the permission, not the place — a
> manager with `SHIFTS_FORCE_CLOSE` closes from the system with no device. Two costs accepted
> and written down there; D123 and D127 carry pointers. `DEVICE_IDENTITY_REQUIRED` moved from
> 401 to 403, which stops admin-web signing the manager out for asking.
>
> **Two fixes landed 2026-09-20**, both backend + POS, both pinned by tests: the shift-detail
> reconstruction that had D123 marked `❌` is closed and D123 is back to `✅`, and competing
> closes are now serialised under a row lock — two simultaneous closes used to both return 200,
> with the second silently replacing the first cashier's counted figure. The POS no longer keeps
> settled tickets past a shift close; an older bill is retrieved by receipt instead.
>
> **Re-verification pass, 2026-09-19** (backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`): **D112-D128 only.** Twelve items marked `🕓` were found built and re-marked
> (D113, D115-D122, D124, D126, D127); D123 was found violated on one path and re-marked `❌`;
> D125 is `⚠️` with one of its three surfaces unbuilt. D114 and D128 remain `🕓` and were
> confirmed unbuilt. Each carries a dated note under its heading. **D1-D111 were not re-run in
> this pass** and still carry the 2026-08-30 caveat above.
>
> **Structural fix, same pass.** D115-D127 and O48-O66 had come to sit under the
> "Negative Stock Batches — Deferred Feature" heading at the end of the file, so thirteen
> ground-truth decisions read as deferred. They are moved into **DECIDED** and **OPEN**
> respectively, and D128 now follows D127 instead of preceding D115. Text and numbering are
> unchanged — nothing was deleted, renumbered or rewritten. **F9 still sits under that heading**
> and is a known leftover.

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

```
> **Narrowed by D105.** The `variantLabel`/`variantLabelAr` columns stay nullable, but a product
> with `parentProductId` set must carry both labels — enforced at the service layer with
> `MenuErrorCode.VARIANT_LABEL_REQUIRED`, not merely in the product form.
> **POS surface specified by D107.** The add-on model here is the data rule; D107 settles where
> the quick-add chips render and how repeat taps behave.
```

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
```
> **Reaffirmed by D103.** Grid visibility is never derived from `MenuCategory`. A proposal to
> hide add-on products by placing them in a dedicated hidden category was rejected there, and a
> category-name-matching form default was built and then removed for the same reason.
```

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

### D20 — MOVED TO OPEN. ⚠️

> **Moved to the OPEN section on 2026-08-30** — see "D20 (moved from DECIDED)" at the end of OPEN.
> The stage-to-consumption mapping it records is **not implemented** in the code
> (`order/core/OrderService.java:120-138`, `:222-241` — no cancelled order produces waste or
> consumption of any kind). It is not ground truth and must not be cited as a hard invariant.
> Do not implement against it until the open question below is settled.

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

> **Superseded in part.** The "all or nothing → CONFLICT" rule no longer holds. A document that
> can post *some* of its materials does so and settles into **`PARTIAL`**, listing exactly which
> materials could not be deducted and why. Losing an entire order's consumption because one
> material is short is worse than posting what can be posted and naming the gap precisely.
>
> `CONFLICT` is now the narrower case: a technical failure, or a document where **nothing** could
> be posted. `PARTIAL` is a missing-stock case.
>
> **They differ in cause but not in consequence** — both hold unposted consumption, both need a
> human to act (enter the missing purchase invoice, then recalculate), and neither resolves on
> its own. Any code deciding whether consumption is outstanding must treat them identically;
> only `PENDING` and `IN_PROGRESS` are transient.

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

> **Revision 2026-09-06 (D127):** cashier login now binds its validated `deviceId` into the
> signed user access token and the device is revalidated on every authenticated request. The
> separate device-login endpoint remains a metadata exchange rather than issuing its own JWT,
> and the order path's plain `X-Branch-Id` remains until the shifts/order rewrite consumes the
> new claim. The original MVP description below is retained as the history of that boundary.

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

### D41 — Order-time warehouse resolution from the authenticated device's branch (revised 2026-09-06).

Device identity, the one-branch binding, and the one-time secret exchange (generation, SHA-256
`secretKeyHash`, deterministic-hash rationale, `POST /api/devices/login`) are defined in **D33**
and are not restated here. D40 governs **who may log in on which device**; this decision governs **which warehouse an
order's consumption is drawn from** once that login has already succeeded.

**Resolution rule.** `warehouseId` is never sent by the client. Every order-creation request (`POST /api/orders`)
resolves the open shift through the signed device identity, takes that device's branch, and
`OrderService.resolveWarehouseForBranch` resolves the warehouse server-side from it at order-creation time. One active
warehouse per branch is assumed (no DB constraint yet — see ROADMAP). Zero or multiple matches fail loudly with
`WAREHOUSE_NOT_FOUND` /
`AMBIGUOUS_WAREHOUSE_FOR_BRANCH` — never silently pick one.

**Independent of the user's own branch.** This resolution reads the authenticated device's branch. It does not consult the
authenticated user's `branchId`, and does not re-run D40's device/user branch-match check — that check already ran once,
at login, and is not repeated per request.

The user supplies the authenticated actor, not the physical branch. Order creation no longer
accepts `X-Branch-Id` or `X-User-Id` as inputs. The existing shift query fetches the device and
branch together; no additional device-validation lookup is introduced. This supersedes the old
cached-header rule, following the user's explicit review decision on 2026-09-06.

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

> ⚠️ This previously pointed at `modules/LOYALTY.md` for full schema/entity/endpoint detail.
> **That file does not exist** (verified 2026-08-30). The module is built — see
> [PROJECT](PROJECT.md) → `loyalty/` — but has no module doc; read the code, or write the doc.

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

**Superseded for the shift rewrite by D119-D126.** The Phase 0 audit found that this model is the
source of the current header-supplied cashier defect and cross-branch shift attachment risk. The
new target scopes the reconciliation key to the authenticated device, keeps cashier attribution
on `openedByUserId` / `closedByUserId`, and adds the blind-count and offline-boundary rules in
D119-D126.

Distinct from any HR scheduling concept — confirmed no HR `Shift` exists, no naming collision. New entity: `Shift(id, tenantId, branchId, cashierUserId, openedAt, closedAt, openingCash,
closingCashCounted, status: OPEN | CLOSED)`. `Order.shiftId` is an explicit FK set at order-creation time (not inferred
from a time window), so reporting stays correct once offline sync (D65) can introduce late-arriving orders later. **X
report**: live, non-destructive aggregation over the current `OPEN` shift's orders. **Z report**: closes the shift,
records
`closingCashCounted` vs. expected, immutable after close. Reuses the existing `SHIFTS_OPEN`
permission (already referenced by D40) rather than adding a new one — confirmed that permission was seeded for exactly
this purpose.

### D65 — Cashier POS: offline order creation is deferred; this implementation pass is online-only.

**Superseded as a statement of current POS behaviour by the Phase 0 audit and D126.** The POS now
does complete and cancel orders offline through a retry queue. D126 is the shift-specific
boundary: opening and closing shifts require connectivity, and closing requires that the queued
orders are flushed first.

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

> **Under question — read before applying this rule.** **O38** asks whether `uomId` alone
> satisfies "an explicit UOM field", now that D111's documented lookup endpoint lets a consumer
> resolve it. Not settled: the admin frontend can resolve an id, but a direct API caller, a future
> integration, and the reports assistant (O21) cannot.
>
> Until it is settled, three responses keep `uomSymbol` as a deliberate carve-out, not an
> oversight — `PhysicalCountLineResponse`, the order-consumption material / error-detail rows, and
> `GET /api/inventory/physical-counts/{id}/post-freeze-movements`. Do not remove them as tidy-up.
> D111 records why they were left alone when the other five responses moved to id-only.

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

> **Amended: the guard blocks on `PARTIAL` as well as `CONFLICT`.**
>
> The original implementation blocked on `CONFLICT` and treated everything else as transient, so
> a `PARTIAL` document produced `FREEZE_CONSUMPTION_NOT_SETTLED` — "still being processed; retry
> the freeze". That is wrong in the way that matters: a `PARTIAL` document **never resolves on
> its own**, so the user retries indefinitely against something that requires them to enter a
> purchase invoice and recalculate. The consumption document's own screen says exactly that; the
> freeze error did not carry it across.
>
> The guard now classifies by **"does this need a human?"** rather than by status name.
> `PARTIAL` and `CONFLICT` block and name the materials holding the document up — normalised
> into one message shape, since `CONFLICT` stores them in `errorDetails` while `PARTIAL` holds
> them as unfulfilled lines. `PENDING` and `IN_PROGRESS` remain retryable. The classification is
> an exhaustive `switch` with no default, so a status added later fails to compile rather than
> silently landing in the transient bucket — which is precisely how this defect arose.
>
> **How it was found, and why no test caught it.** A manual data session: an order consumed Basil
> the warehouse did not have, the document settled `PARTIAL`, and a freeze on that warehouse
> returned the retry message. The freeze guard had full test coverage for `CONFLICT` and
> `PENDING` — but no test for `PARTIAL` at freeze time, because **the documented model said
> `PARTIAL` could not occur**. The tests were written against D30's wording, so they inherited
> its error, and 400 passing tests could not surface it.
>
> Recorded because the lesson generalises: a test suite written from documentation cannot catch
> a documentation error. Only real data through the real UI could, and did.

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

### D102 — `factorToBase` is relative to the root; the UOM tree is flattened on write. ✅

`Uom` is a self-referencing tree — nullable `baseUom` plus `factorToBase` — with no conversion
table, deliberately (D13). `UomConversionService.baseUomId()` reads exactly **one** level:

```java
return u.getBaseUom() == null ? u.getId() : u.getBaseUom().getId();
```

That encodes an unwritten invariant: the tree is exactly two levels deep. V6's seed honours it
(GRAM root, KILOGRAM→GRAM ×1000, TON→GRAM ×1000000). **Nothing on the write path enforced it**,
and the tenant UOM form offered every active unit as a parent. A tenant created a 25 kg sack with
`baseUom = KILOGRAM`, `factorToBase = 25`, which resolved to base KILOGRAM while KILOGRAM resolves
to GRAM — `sameBaseUom` failed and the unit converted to nothing at all, including its own parent.

A second defect sat behind the first: `factorToBase` means "how many of the **root**". The 25 was
typed meaning kilograms and would have been read as 25 grams — a silent 1000× error written to the
ledger, not an exception.

**The fix is to flatten on write, not to make the reader recursive.** `physicalConvert` never
needed the root as a step: it multiplies by the source factor and divides by the target factor, and
neither operand has to be a root (KILOGRAM → TON works today; neither is a root). What the shared
root guarantees is a shared **calibration point** — dividing factor by factor is only meaningful
when both were measured from the same zero. `sameBaseUom` is a comparability guard, not a step in
the arithmetic. A recursive `baseUomId` would still need the factor renormalized, and it invites
cycles through a self-referencing FK with no guard.

**`UomService.buildUom` computes four values and is the only code that writes any of them:**

```
entered_factor         = request.factorToBase        (as typed)
entered_against_uom_id = request.baseUom             (as chosen)
base_uom_id            = the parent's root
factor_to_base         = request.factorToBase × parent.factorToBase
```

The parent is already root-calibrated, so one multiplication is the whole normalization, and
choosing a root as the parent multiplies by 1 — one path, no special case. A tenant UOM may be the
parent: sack → box → kilogram flattens the same way. A divergence between the stored entered pair
and `factorToBase` would be worse than the original bug, because the screen would confidently
display a number the engine does not use.

**The entered pair is display metadata and nothing else.** `entered_factor` (`NOT NULL`) and
`entered_against_uom_id` (nullable — roots were entered against nothing) exist so the form can show
"25 KILOGRAM" rather than "25000 GRAM". **No code may read them for arithmetic.**

**`type` is derived from the parent**, never read from the request: a unit cannot be a different
physical type from the thing it is calibrated against. A disagreeing request value is ignored, not
rejected.

**The requirement is enforced in `createForTenant`, not on the DTO.** `UomRequest` is shared with
`PanelUomController`, and the sysadmin panel is the legitimate path for creating a root —
`baseUom = null`, `factorToBase = 1`. A DTO-level `@NotNull` would forbid the one thing that path
does correctly. The rule is "a *tenant* never creates a root", which is a property of the tenant
creation path, not of the request shape. Enforced there, rejecting with `UOM_BASE_REQUIRED`.

The `base_uom_id` column stays nullable regardless: NULL is the marker for "this is a calibration
root" and pairs with `factorToBase = 1`.

**The parent is resolved tenant-scoped.** A bare `findById` let tenant A reference tenant B's
private UOM — the FK persisted, but `findAvailableForTenant` never returns it, so the unit was
permanently unresolvable for its own owner. `resolveParentUom` rejects with
`UOM_BASE_NOT_AVAILABLE`, mirroring `MaterialService.resolveUom`. It is kept separate from
`loadUom`, which still serves the sysadmin deactivate path with a null `tenantId`.

**Two CHECK constraints hold the shape:**

```sql
ck_uom_root_factor   -- a claimed root cannot carry a factor <> 1
ck_uom_no_self_base  -- a unit cannot be its own base
```

`ck_uom_root_factor` rejects exactly the row shape the sysadmin panel produces (**O37**), which is
the correct loud failure on a sysadmin-only screen. `ck_uom_no_self_base` forecloses
self-reference, which was considered and rejected as an alternative to a nullable `base_uom_id`.

**One compatibility rule: base identity, in all three places** — `UomConversionService.convert`,
`UomService.convertValue` (which now delegates rather than reimplementing), and the frontend
`rootOf` helper. Three rules previously coexisted, which was user-visible: the UI offered a unit,
previewed a converted quantity, and the save then threw. **Type equality is too weak and must not
be substituted** — two roots of the same physical type (a POUND with `factorToBase = 1` beside
GRAM) pass a type check and produce nonsense.

**`factorToBase`, `baseUom` and `type` are immutable once the UOM is in use.** Editing a factor
after transactions exist does not change the ledger, but it silently reinterprets every number
already written into it — D4 from behind. `name`, `nameAr`, `symbol` and `active` carry no
arithmetic and stay editable.

"In use" is any of these — **16 columns across 12 tables, read from `pg_constraint`**:

| Table | Column(s) |
|---|---|
| `material` | `stock_uom_id`, `display_uom_id` |
| `material_catalog` | `default_stock_uom_id`, `default_display_uom_id` |
| `uom` | `base_uom_id` |
| `inventory_transaction` | `entered_uom_id`, `stock_uom_id` |
| `stock_balance` | `uom_id` |
| `physical_count_line` | `uom_id` |
| `inventory_transfer_line` | `uom_id` |
| `waste_line` | `uom_id` |
| `purchase_invoice_line` | `uom_id` |
| `purchase_return_line` | `uom_id` |
| `order_consumption_material` | `required_uom_id`, `entered_uom_id` |
| `recipe_item` | `uom_id` |

`stock_batch` carries **no** UOM column — it inherits from `stock_balance` (D87). Earlier drafts of
this rule named it and omitted eight tables that do hold the FK. `material` and `uom` are the cheap
checks and always precede the rest chronologically, so they short-circuit first — but a correct
guard checks all sixteen columns.

**Decided, not yet enforced — see O35.** No UOM update path exists on either side: there is no
`PUT /uom/{id}` on the tenant path, and `UomService` has no update method. The guard has no call
site to attach to, so it was not built with the rest of this decision.

This is a **blocker, not a queued task**. An update endpoint written without the normalization step
would store `factorToBase` raw — the original bug, arriving through the edit door — and without the
guard it would silently reinterpret every quantity already in the ledger.

> **Build note.** V46 (not V44 — V44/V45 were taken by D101, and `validate-on-migrate: false` means
> a colliding version is skipped silently rather than reported). The migration backfills the entered
> pair *before* flattening, so it captures the pre-flatten values, then flattens in a loop capped at
> 10 iterations that raises rather than hangs if a cycle survived the pre-flight check. Ids are
> stable and no row is inserted or deleted, so all 16 foreign keys stay valid. Pre-flight against the
> dev database found 1 non-flat chain, 0 cycles, 0 cross-tenant parents and 0 roots with a factor ≠ 1
> — the last of which is what allowed `ck_uom_root_factor` to apply. `UomNormalizationIntegrationTest`
> covers 14 cases including the original regression; the suite is 654 tests with 4 pre-existing
> failures unrelated to UOM. `UomConversionService.baseUomId` and `physicalConvert` are unchanged —
> if a future change edits either, it has taken the recursive approach and should stop.

### D103 — Menu-grid visibility is a property of the product (`isMenu`), never of its category. ✅

Reaffirms D17 against a concrete proposal to hide add-ons by putting them in a dedicated
`MenuCategory` ("اضافات") and hiding that category from the grid. **Rejected.** No
`MenuCategory.isHidden` / `isAddOnCategory` column exists.

**Wrong granularity, and it breaks a case D17 explicitly supports.** D17 requires a product to be
able to be grid-visible *and* an add-on simultaneously — the Coke case: `isMenu = true` AND linked
via `ProductAddOn`, the two being independent. A product has exactly one `menuCategoryId`, so a
category-level visibility flag makes that state unrepresentable: Coke would have to live in Drinks
(visible, never suggested as an add-on) or in اضافات (suggested, invisible in the grid). The
per-product flag can express both; the per-category flag cannot express either without giving up
the other.

**Two mechanisms answering one question drift.** "Is this product visible in the grid?" would have
two sources with no defined precedence, and the first disagreement between them is a bug with no
correct resolution.

**Different reasons to change.** A `MenuCategory` is customer-facing menu navigation; `isMenu` is a
merchandising property of one product. Coupling them means a navigation change silently
re-merchandises the grid.

**The category itself is fine, and should exist.** "اضافات" stays a normal `MenuCategory`: an
organizational bucket in the admin product list and a sensible source for the add-on picker. It
simply does not control visibility.

**The live defect this came from was a wrong value, not a missing mechanism.** `اضافة جبن` (product
24) was created with `isMenu = true` and therefore rendered as a grid tile and contributed a
    category filter chip in the cashier. Fixed to `isMenu = false` in `V47`.

**There is no add-on `isMenu` default, and the absence is deliberate.** The original draft of this
decision called for one — the product form defaulting `isMenu` off when the selected category is
the tenant's add-on category — on the reasoning that nobody should have to remember the toggle.
It was built, and then **removed**, because no add-on-category setting or marker exists, so the
only available implementation was matching the category's normalized *name* against conventional
strings (`Add-ons`, `اضافات`).

That is this decision's own rejected idea returning as a soft default, and its failure mode is
precisely the defect above: a tenant names the category `Extras`, or `إضافات` with a hamza, the
match misses, `isMenu` defaults `true`, and the add-on appears in the grid. A default that works
for two spellings and fails silently on the third is worse than no default, because the user
learns to rely on it and stops checking.

`isMenu` keeps its normal default and the user sets it. **A future add-on default needs a real
tenant-level setting or a marker column on `MenuCategory` — a decision, not a string match** — and
even then it stays a form default that no query ever reads.

> **Build note.** `V47` sets `is_menu = false` on product 24. The name-matching default was added
> and then removed from `restaurant-saas-web/src/pages/menu/ProductEditorPage.tsx`; no
> category-name comparison remains anywhere in the product form.
 
---

### D104 — Two product read models: the admin list stays flat; the POS gets a nested `/api/menu`. ✅

The admin product list response is flat and carries add-ons through a separate call. Both are
correct for that endpoint and wrong for the cashier — which is a different consumer, not a
formatting preference.

**The admin product list stays flat.** It is a flat table in the UI, and it is *already* the input
D14's derivation runs on: parenthood is computed from the loaded product list, and the response
carries `parentProductId` plus the derived `isParent`. Grouping is one O(n) pass on the frontend.
Nesting would break paging, sorting, and the row count, and would introduce a "does a child appear
once, twice, or not at all" ambiguity that has no good answer.

**The POS gets a nested read model at a separate endpoint.** It needs grid-visible roots
(`isMenu = true`, `parentProductId IS NULL`) with their variants and add-ons attached. Served flat,
the POS would download the whole catalog to reassemble a tree it could have been handed, then make
one add-on call per product — an N+1 on the hot screen.

```jsonc
GET /api/menu
[
  { "id": 21, "name": "Cheese Pizza", "type": "PARENT",
    "menuCategoryId": 8, "menuCategoryName": "Pizza",
    "minPrice": 70.00, "maxPrice": 140.00,
    "variants": [ { "id": 22, "name": "Cheese Pizza Small", "variantLabel": "Small",
                    "variantLabelAr": "صغير", "sellingPrice": 70.00 } ],
    "addOns":   [ { "id": 24, "name": "اضافة جبن", "sellingPrice": 20.00 } ] },
  { "id": 17, "name": "Chicken Rice", "type": "STANDALONE",
    "menuCategoryId": 6, "menuCategoryName": "Plates",
    "sellingPrice": 85.00, "variants": [], "addOns": [] }
]
```

**Not a D13 violation — this is the second concrete caller.** The POS has a different access
pattern, not a different taste in JSON. It is also a read-only projection, so it cannot drift from
the write path; nothing about the flat endpoint or the entity changed.

**`type` is an explicit discriminator**, not inferred from `variants.length > 0`. Derived either
way, but stated in the contract rather than reconstructed by each consumer.

**Variant children never appear as top-level entries**, only nested under their parent. This is
what lets the POS render the grid straight from the response with no filtering pass.

**The endpoint is pinned at exactly two queries**, by a real-Postgres integration test that also
asserts `addOns` is populated. Both halves of that assertion are load-bearing: a query-count test
alone cannot distinguish an efficient join from a missing one, and would pass happily while every
`addOns` array came back empty.

**A parent's own `sellingPrice` is not a price and is not rendered as one.** `Cheese Pizza` (21)
carries `0.00`. `minPrice`/`maxPrice` are derived from the children and present only on `PARENT`;
the POS tile shows the range (`من 70`), never the stored zero.

**The POS derives its category filter chips from the returned roots**, not from a separate
categories call — so a category with no visible products cannot produce an empty chip.

**Add-ons stay a separate lazy call in the admin product editor.** One product is in scope there
and lazy loading is correct. The N+1 was a POS problem only, and `/api/menu` is where it is solved.

**Boolean JSON naming is normalized to the `is`-prefixed form** — `isActive`, `isMenu`, `isParent`.
The response previously mixed unprefixed `parent`/`active` with prefixed `isMenu` (primitive
`boolean` versus boxed `Boolean` getter naming), so the frontend had to remember which was which.
Pinned with `@JsonProperty` on the menu DTOs. Both consumers were updated in the same pass.
 
---

### D105 — `variantLabel` is required when `parentProductId` is set, enforced in the service. ✅

Narrows D14, which describes `variantLabel` / `variantLabelAr` as nullable bilingual free text.
The **column stays nullable** — standalone and parent products have no label and never will. What
is added is a conditional service-layer requirement: `parentProductId != null` → both labels
required, rejected with `MenuErrorCode.VARIANT_LABEL_REQUIRED`. Same shape as D17's
`VARIANT_CANNOT_BE_MENU_ITEM`: explicit rejection, never silent coercion (D6/D35). No schema
change.

**Form-level enforcement alone would not hold.** Any caller reaching the API directly — a seed
script, the POS, an agent run — creates a labelless child. That was not hypothetical: it was the
state all three existing children were in, and it surfaced in the cashier as variant chips reading
"Cheese Pizza Small / Medium / Large" instead of "صغير / وسط / كبير".

**Selecting a parent is one transition with four effects**, and they ship together:

1. the two label fields appear and become required;
2. `isMenu` is forced `false` and locked (D17);
3. the Add-Ons tab is hidden — add-ons attach to `parentProductId IS NULL` only (D14);
4. the parent dropdown offers **parent-eligible products only** — `parentProductId IS NULL` AND no
   active recipe, served by a `parentEligible` filter on the product list endpoint. Offering a
   product that will be rejected with `PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT` asks the user to
   discover a rule the form already knows.
   **Clearing the parent clears the labels**, in the form and in the service. Otherwise a standalone
   product carries a stale `variantLabel` that nothing renders and nothing cleans up — the standard
   conditional-field drift. A label arriving with no parent is nulled server-side rather than
   rejected, since that is the shape of the legitimate clear-the-parent edit.

**Both languages are required, not one.** The chip is the only surface where a missing label leaves
the POS with nothing at all to draw, and the POS is Arabic-first — requiring English alone would
reproduce exactly the defect this decision exists to close. Regardless, the render fallback is
fixed: missing label → the other language's label → the product `name`. **Never blank.**

**The child keeps its full descriptive `name`.** "Cheese Pizza Large", not "Large". Sales-by-product
reads `name` (O31), and a bare size is not a report row. `name` serves reports and receipts;
`variantLabel` serves the chip. They are not redundant.

**Sibling labels must be unique under one parent** — `MenuErrorCode.DUPLICATE_VARIANT_LABEL`. Two
chips reading "Large" are indistinguishable to the person tapping them. The check is scoped to
siblings only.

> **Build note.** `V47` backfills `variant_label`/`variant_label_ar` on products 22, 16 and 23
> (`Small`/`صغير`, `Medium`/`وسط`, `Large`/`كبير`) — the validation does not reach existing rows,
> and until the backfill the cashier kept rendering names. Admin side lives in
> `restaurant-saas-web/src/pages/menu/ProductEditorPage.tsx`, which also carries the D14 tab
> matrix.
 
---

### D106 — POS variant picker is a centered modal, not a bottom sheet. ✅

**It does not fit the tested floor.** D66 fixes the floor at 1280×800 and the Electron window
enforces it. A bottom-anchored sheet derives its height from the bottom edge: three variants fit,
six or eight do not, and the overflow scrolls the grid behind the overlay rather than the sheet's
own content. Centered, with `max-height` and internal scroll on the chip area, behaves identically
at every variant count — which is the D66 property (one component tree, scaled) rather than a
layout that happens to work at the size it was drawn at.

**Eye path.** The overlay dims the whole screen, so a bottom-anchored panel sends the user to the
bottom of a wide landscape display for the one decision the modal exists to collect. On a POS
screen that cost is paid on every variant sale.

**Spec.** Centered on both axes; `max-width` ~600px; `max-height: 80vh` with scrolling on the chip
area, not the modal. Chips in `repeat(auto-fill, minmax(180px, 1fr))` — one row at three variants,
two rows at six, no breakpoint-specific components (D66). Chip height 56–64px (D67, primary
action). Esc and backdrop dismiss; first chip focused on open, since PC stations are mouse +
keyboard (D68).

**The heading is generic ("اختر الصنف"), not "اختر الحجم".** D14's axis is free text — "ربع / نص",
"كول سلو / بطاطس" — so size wording would be wrong for the first non-size parent anyone creates.

> **Build note.** `VariantPicker.tsx` in the POS repo.
 
---

### D107 — POS add-ons: quick-add chips under the host ticket line; the resulting line is ordinary and unlinked. ✅

Specifies the POS surface for D14's add-on model. D14 settled the data (`ProductAddOn` is
menu-side only; a selected add-on becomes an ordinary `OrderLine` with no host link) but not where
the chips render or how repeat taps behave.

**Chips render under their host line in the current-order panel.** A ticket line whose product
resolves to a root with linked add-ons gets a row of quick-add chips directly beneath it, showing
name and price, **wrapping** rather than scrolling horizontally — the order panel is narrow, and a
chip hidden behind an overflow edge is an add-on that never gets sold. A line whose root has no
linked add-ons renders **no chip row at all**, not an empty container. Minimum touch target 48px
(D67 — secondary action, so the 56–64px primary size does not apply).

**Resolution runs through the root, not the line's own product.** Add-ons attach to
parent-eligible products only (`parentProductId IS NULL`, D14), so a ticket line for
`Cheese Pizza كبير` (product 23, a variant child) surfaces the add-ons linked to its **parent**
(product 21). `/api/menu` gives this for free — `addOns` sits on the root with the variants nested
under it (D104) — so the POS builds a `productId → root` map once at menu load, covering roots and
nested variants, rather than searching the tree per render.

**A tap creates an ordinary, independent `OrderLine`** — its own `productId`, quantity and price,
frozen at completion like any other line (D21). No `parentOrderLineId`, no host link of any kind;
`OrderLine`'s schema is unchanged.

Two consequences were built deliberately rather than worked around:

- **The resulting line renders as a normal top-level line**, not nested or indented under its host,
  and carries no "belongs to" badge. The data does not hold that relationship and the UI must not
  claim it does.
- **Repeat taps merge into one line, including across different hosts.** Tapping اضافة جبن under
  pizza A and again under pizza B yields a single line at quantity 2, exactly as tapping any
  product twice would. With no host link, "extra cheese on A" and "extra cheese on B" are
  indistinguishable in the data. **Rejected: one add-on line per host** — it would fabricate a
  distinction the model cannot store and the receipt cannot show.
  **Removing the host line does not remove the add-on line.** They are independent rows; a cascade
  would be inventing the same missing relationship from the other direction. This is pinned by a
  test specifically so a later pass does not "fix" it into a cascade.

**Add-on products are `isMenu = false`**, so they never appear in the product grid and the chip is
the only route to them. The nested `addOns` projection carries everything the line needs
(`id`, `name`, `sellingPrice`) — no second call per product.

> **Build note.** Root/variant lookup and line creation in `usePos.tsx`; chips in
> `components/NewOrder.tsx`; behavioural coverage in `usePos.orderSubmission.test.ts`. Tenant 7
> already carried the `21 → 24` `ProductAddOn` row; nothing was seeded.
 
---

### D108 — Purchase returns are entered in the original invoice line's UOM. ✅

The first-party purchase-return UI locks both UOM and unit cost to the selected original invoice
line. Selecting that line populates `uomId` and the inherited `unitCost`; clearing it clears both.
Return quantity is entered and validated directly in that original UOM. Compatible alternate UOMs
are deliberately not offered until a concrete user need justifies the extra conversion and
fractional-ledger paths (D13).

This is currently a **UI convention, not a backend invariant**. The add and update DTOs still
require `uomId`, and `PurchaseReturnService` accepts compatible alternate units, converts the
quantity for returnable validation, and derives a converted unit cost. Other clients can therefore
submit a different compatible UOM. On 2026-08-22, live-data inspection found 0 divergent rows out
of 5 `purchase_return_line` rows (`prl.uom_id <> original purchase_invoice_line.uom_id`). Existing
rows must always be rendered from their stored UOM even if a future inspection finds divergence;
locking new input is not a data migration.

### D109 — Layout width caps live on content elements, not page containers. ✅

Page-level width caps were removed from the generic `.page`, `.list-page`, and `.reports-page`
containers. Content that genuinely needs a reading-width constraint owns that constraint itself;
table-heavy document pages may use the available viewport and provide a single horizontal scroll
container with document-specific minimum widths. Fixed action columns use logical inline-end
positioning so the same contract applies in LTR and RTL.

**Verification standard.** A successful build is not interactive layout verification. Layout
changes are checked in a rendered browser at the relevant narrow and wide viewports, in English/LTR
and Arabic/RTL, or are explicitly reported as runtime-unverified.

This decision was originally drafted as D98 in the frontend layout audit. D98 was already assigned
to loss reports; D109 is its stable identifier.

### D110 — Asset acquisition lines are immutable after creation. ✅

An asset line records an actual acquisition. Changing its quantity, unit cost, or purchase date
after creation would rewrite book value without an audit trail, so persisted lines are read-only
and the API intentionally has no update endpoint. Corrections use delete-and-recreate. Deletion
remains prohibited once disposal or maintenance records reference the line
(`LINE_HAS_CHILD_RECORDS`).

### D111 — Small bounded lookups are cached client-side and rows carry the id; large or fast-moving ones are searched server-side. ✅

> **Status: built, all three phases.** UOM is the first and only application; the rule below is
> written to be applied again, but nothing else is in scope yet. Two clauses did **not** survive
> contact with the code — the join drop and the query saving it implied — and the build note at the
> end of this decision records why, along with the three DTOs that cannot be cut because they carry
> no `uomId`. Read it before applying this rule to a second lookup.

Unit-of-measure names (`name`, `nameAr`, `symbol`) are joined and returned on **seven** row-level
render sites — purchase invoice lines, purchase return lines, waste lines, physical count lines,
warehouse stock rows, order consumption rows, and recipe items in the products screen. Each row
carries both languages of a name drawn from a table of a few dozen rows.

**The rule.** A lookup set that is *bounded, small, and slow-changing* is loaded once per session
and cached in the client; its rows travel as `id` alone. A lookup set that is *large or
fast-changing* is not cached at all — its picker is a server-side search endpoint, and only the
ids actually rendered are resolved for display.

| | Cached in full | Server-searched |
|---|---|---|
| Examples | UOM, categories, warehouses, payment methods | Material, supplier, product |
| Picker source | the cache | a search endpoint, always fresh by construction |
| Display source | the cache | a display-resolution cache built on demand |

**Materials are deliberately on the right-hand side, not the left.** A tenant's material catalog
runs to thousands of rows and changes daily; loading it at bootstrap trades a real cost at app
open for a saving that does not exist, and it reintroduces the freshness problem the search
endpoint dissolves outright. This is stated here because "apply the same thing to materials next"
is the obvious and wrong next step.

**Justified now, not premature (D13).** Seven concrete callers exist on day one. That is the same
reading applied in D84 and D86: the threshold is met before the mechanism is written, not
anticipated.

#### The freshness contract — three mechanisms, in order of what they guarantee

The requirement is **not** "the cache matches the server at every instant". It is "no wrong name,
no blank cell, and a newly created unit is selectable". Those are different problems:

1. **Resolve-on-miss** guarantees *display*. An id absent from the cache is fetched individually
   (`GET /api/uom/{id}`) and added. Worst case is one small request; there is no blank-cell case.
   This is the safety net that makes the other two sufficient rather than merely likely.
2. **Revalidate-on-open** guarantees *selection*. Resolve-on-miss cannot help here — a user
   cannot pick a unit that is not in the list. Opening a UOM picker fires a conditional request
   (`If-None-Match`) against the lookup endpoint, rendering from cache immediately and replacing
   it on a `200`. Almost always a `304`, so the cost is a few bytes at the one moment freshness
   matters.
3. **A version header on every response** (`X-Lookups-Version: uom=<version>`) is a latency
   optimization, not a guarantee. Ordinary traffic tells the client its cache is stale, so the
   cache is usually already fresh before a picker opens. The version is cached per tenant in
   memory and evicted by the UOM write paths, mirroring `TenantTimeZoneService` (D101) — **it must
   not cost a query per request**, which would make the whole change a net loss.

**Polling, and per-tenant SSE/WebSocket push, are both rejected.** UOM data changes a handful of
times in a tenant's lifetime. Push would mean a connection layer and, across instances, a
broadcast mechanism — permanent operational cost against a twice-a-year event. The residual gap is
narrow and named: a picker already open when another user creates a unit does not update until it
is reopened.

#### The cache holds inactive UOMs; this is load-bearing

`findAvailableForTenant` filters to `active` and must keep doing so — it feeds the pickers. The
**lookup endpoint must not filter**, because a document from last year referencing a
since-deactivated unit still has to render its name. A cache of active units only produces a blank
cell with no error and no way to notice. Each row carries `active` so the pickers can filter what
the cache cannot.

#### Names resolve live; they are not snapshotted onto document lines

Denormalizing `uom_name_ar` / `uom_name_en` onto each `*_line` row at post time was considered and
**rejected**. A renamed unit is almost always a correction or a translation fix, and the user wants
it to propagate — including to historical documents. Freezing the name would make an old invoice
argue with the unit list forever.

This is cheap to hold today because **no UOM update path exists at all** — no `PUT /api/uom/{id}`,
no update method on `UomService` (O35), so create and deactivate are the only mutations and a name
cannot currently change. If O35b is ever built, this clause is what it must be checked against.

**The real historical-integrity risk was never the name.** It is `factorToBase`, which silently
reinterprets every quantity already written into the ledger — and that is already closed by D102's
immutability rule. Nothing here weakens it.

#### D88 is not amended, and three responses keep `uomSymbol`

D88 requires a quantity sourced from `inventory_transaction` to carry both a converted value and
an explicit UOM field, naming `uomId` + `uomSymbol` as the convention, with an explicit "neither
alone is sufficient" clause. Three responses fall under it: `PhysicalCountLineResponse`, the
order-consumption material/error rows, and `GET /{id}/post-freeze-movements`.

`uomId` alone arguably satisfies D88's intent for a consumer that can resolve ids — the frontend
now can. But the frontend is not the only consumer D88 was written for, and it says so in as many
words. **Those three keep `uomSymbol`.** See **O38**.

#### Export and print return resolved names

A renderer has no client cache, so an export or print endpoint must return full names. **Nothing is
built for this now**: export is client-side CSV and PDF is deferred (D84), so there is no
server-side export endpoint to carve out. The rule is recorded for whoever builds one.

The corresponding client-side rule is load-bearing today: resolution happens **where a row becomes
a view model**, not inside a cell renderer, so the table, the form view and `reportExport.ts` all
consume the same resolved shape. Resolving in JSX leaves the CSV mapping reading the raw row, and
every exported UOM column becomes a column of integers — a silent failure discovered by a client
opening a spreadsheet.

#### The rollout is three phases across two agents, and the order is not negotiable

1. **Backend, additive** — lookup endpoint, single-UOM resolve, version header. Nothing removed.
2. **Frontend** — cache, resolution, revalidate-on-open. The API still sends names; the frontend
   stops reading them.
3. **Backend, subtractive** — the name fields leave the five non-D88 response DTOs and the joins
   are dropped.

Phase 3 before phase 2 blanks the UOM column on seven screens simultaneously, with no error and no
partial failure. The phases exist only to make that impossible; the end state is still a clean cut
with no permanent dual-read.

#### Scope note

`inventory_transfer_line.uom_id` and `material.stock_uom_id` / `display_uom_id` also render UOM
names and were **not** in the original seven. They are not in scope for this pass and are to be
reported, not changed.

#### Build note — all three phases as shipped

Phases 1 and 2 are built on `feat/uom-lookup-backend` and `feat/uom-lookup-frontend`; phase 3 is on
`feat/uom-lookup-cut-phase3` in both repos.

All five were cut, plus a sixth the decision never listed. `uomSymbol` has left
`PurchaseInvoiceLineResponse`, `PurchaseReturnLineResponse`, `WasteLineResponse`,
`StockBalanceResponse` and `ReturnableLineResponse`, and `uomName` has left `RecipeItemResponse`.

`ReturnableLineResponse` was not one of the five and was found only by sweeping the whole DTO
surface: its mapper had already stopped populating the field, so it was serializing as a permanent
`null` while the type still advertised it. Nothing read it — `purchaseReturnLineSchema` resolves
through `matched.uomId` against the cached set.

`UomDisplayFieldCutTest` pins the cut in both directions — the six must not regrow a display field,
and the D88 responses must keep `uomSymbol` so a later tidy-up sweep cannot mistake them for
stragglers.

**The Flutter app is a consumer of two of the five and was knowingly descoped** on 2026-09-01. It
will render bare quantities on its stock and purchase-invoice screens until it gets the phase-2
treatment. This was an explicit decision, not an oversight — see **O42** for what breaks and what
closing it requires.

**The joins are dropped too**, so the cut delivers both halves of what this decision promised.
`LEFT JOIN FETCH sb.uom` is gone from `StockBalanceRepository.findByWarehouse` and `JOIN FETCH r.uom`
from both `RecipeItemRepository` queries. They existed only to serve `uom.getSymbol()`, which no
longer has a caller on these paths.

This was nearly not done, on a plausible and false premise — that `uom.getId()` initializes the lazy
proxy and dropping a join would therefore cost a SELECT per unit. It does not:
`UomIdReadCostIntegrationTest` measures 0 statements for `getId()` against 1 for `getSymbol()`, with
the proxy still uninitialized afterwards. See **O41**, which records the wrong claim as well as the
correction.

**Three DTOs still carry `uomSymbol` and cannot be cut: they have no `uomId`.**
`StockBatchResponse`, `MaterialShortfallResponse`, and the four report rows
(`ShrinkageRow`, `WasteAnalysisRow`, `LossComparisonRow`, `PurchasePriceDriftRow` — the last three
do carry `uomId`, but their renderers read `row.uomSymbol` directly with no cache path, and
`ShrinkageReport`/`WasteAnalysisReport` use it as a CSV export column, which is the export carve-out
above working as intended). These were never among the seven render sites. Report rows are
aggregates, not row-level document lines, and are out of scope for this rule until someone decides
otherwise.

**`symbolAr` is populated for global UOMs only, and nothing else will populate it.** `V49` backfills
six global units (`GRAM`, `MILLILITRE`, `PIECE`, `KILOGRAM`, `TON`, `LITRE`). No UI writes the
field — the tenant UOM form has no input for it — so every tenant-created unit has
`symbol_ar = NULL` permanently and renders through the fallback. That is by design and is safe
because the chain is `symbolAr → symbol → code`, implemented once in `getLocalizedUomSymbol`
(`restaurant-saas-web/src/utils/inventoryUom.ts`) and delegated to everywhere. Anyone adding a
field to the UOM form should start here.

**Single instance is a constraint, not an implementation detail.** The version cache is
process-local. With one instance that is correct. With two, a mutation evicts only the node that
handled it; every other node keeps serving its stale version and `304`s against it indefinitely,
and nothing fails or logs. Adding a second instance requires replacing this cache first.

**`uom.getId()` initializes the lazy proxy.** All five in-scope entities are
`@ManyToOne(fetch = LAZY)` with field access, so Hibernate does not short-circuit the identifier
getter and the query fires even though only the id is read. Phase 3 will therefore save payload
bytes but **not** queries unless the mappers stop touching the association — otherwise it ships the
cost of the change without the benefit.

**`UomController` carries no `@PreAuthorize`.** Security is `anyRequest().authenticated()`, so any
authenticated user can call the lookup. This predates the branch and is accepted deliberately: it is
what lets a menu-only user resolve units in the recipe editor without holding inventory permissions.
Do not add a gate without giving the menu module another route to the lookup.

**Flyway.** `V49` legitimately owns its number. The unmerged menu-category `V48` is now lower than
an applied migration, and `application.yml` sets `validate-on-migrate: false` with `out-of-order`
unset — so if it merges as-is it will be **skipped silently** and its column will simply not exist.
It must move to `V50+` before merging. Not this branch's change; flagged to its owner.

### D112 — Document numbers drop branch/warehouse/tenant-code segments; uniqueness was never carried by the string. ✅

> **Status: built.** All four types allocate through `DocumentSequenceService`; both old generators
> are deleted. The question this decision explicitly left open for the implementation pass — whether
> Waste and Purchase Return had any generated-code mechanism at all — is answered in the build note
> at the end: they did, so all four were format cutovers and nothing needed backfilling. The build
> note also records a table-name trap that outlived the deletion; read it before touching
> `invoice_sequence`.

Resolves **O27**, whose raised text is preserved below under *O27, as it was raised*. Scope is the
four operational document types it named: Purchase Invoice,
Purchase Return, Waste, Physical Count. `D75`'s entity codes (Material, MaterialCategory,
Supplier, Warehouse, Employee, Job) are **not** touched — a master-data row's permanent
identifier is a different problem from a periodic business document's number, and D75 already
shipped. Not reopened here.

**The premise the old formats got wrong.** Every one of these four tables is tenant-owned
(`TenantAwareEntity`, non-null `tenant_id`) and uniqueness is enforced by a per-table constraint
scoped to `(tenant_id, code)`. A code collision across tenants is structurally impossible
regardless of what the string itself contains — the guarantee already lives one layer down, in
the schema. `PC-F7AM-WH-0001-2026-06-20-0001`-style formats were carrying tenant/warehouse/date
segments that added length and reading friction without adding any uniqueness the constraint
didn't already provide.

**Format:** `{TYPE}/{YY}/{NNNNNN}` — fixed 2-letter type code, 2-digit year, 6-digit zero-padded
sequence, unclamped past `999999` (same no-clamp behavior as D75's 4-digit sequences).

| Document type | Code |
|---|---|
| Physical Count | `PC` |
| Waste | `WS` |
| Purchase Invoice | `PI` |
| Purchase Return | `PR` |

Examples: `PC/26/000001`, `WS/26/000012`, `PI/26/000001`, `PR/26/000001`.

**Separator is `/`, deliberately, not `-`.** Considered and rejected switching to `-` for
URL/CSV/filename safety — moot here because these codes are never used as a route/path segment;
every document is addressed by its numeric `id`, and the code is a display/business field only.
Revisit only if a code is ever wired into a URL path segment or an unescaped query filter — not
the case today.

**Counter resets every year, per tenant, per document type.** `(tenant_id, document_type, year)`
is the counter's key; the sequence returns to 1 on the tenant's first document of that type in a
new year. **Year is the tenant's own wall-clock year (D101), not the server's** — resolving the
year from `LocalDate.now()`/server time would repeat D101's original bug for the one field that
is a year number instead of a timestamp.

**Mechanism: one shared allocator, not three.** A single service (e.g. `DocumentSequenceService`)
serves all four types — and any future one — via the proven atomic
`INSERT ... ON CONFLICT (tenant_id, document_type, year) DO UPDATE SET seq = seq + 1 RETURNING seq`
pattern from `PhysicalCountCodeSequenceService` (D91). This is the baseline O27 itself already
named as a constraint, and it is what closes **F7** for good — `InvoiceSequenceService`'s
first-row-race is retired along with the service, not merely avoided going forward.

**Gaps are accepted; allocation happens at create, not at post.** Matches
`PhysicalCountCodeSequenceService`'s existing behavior. A DRAFT document deleted before posting
leaves a hole in the sequence — gap-free numbering was considered and rejected, since it would
force allocation to move to post/complete time, which is a bigger behavior change than this pass
is scoped for and nobody has asked for gap-free numbers.

**No tenant-configurable prefix or format.** O27 left this open; rejected here per D13 — no
tenant has asked for a custom prefix or format, and building the configuration surface ahead of
that need is exactly the premature abstraction D13 exists to block. The type code is fixed
system-wide.

**Existing numbers are never renumbered.** Same stance as D74's `orderNo` and O27's own framing:
the new scheme applies to documents created after it ships. A tenant's existing `PC-<warehouse>-
<date>-0001` rows keep reading exactly as they do today.

**Known trade-off, stated rather than discovered later.** The old Physical Count code let a user
identify the warehouse from the code alone (`PC-<warehouse>-<date>-...`). Under this format that
information is gone from the string — it lives only in the document's own `warehouseId` /
warehouse column, wherever the code is displayed. Accepted: the code's job is to be a short,
unique, human-referenceable label, not a summary of the document's fields — the same reasoning
D74 already applied to `orderNo` staying a plain integer with no device prefix.

**Not decided, left for the implementation pass:** whether Waste and Purchase Return currently
have any generated-code mechanism at all (not confirmed this session — if they're free-text or
absent, the same allocator gives them their first real one, which is a bigger change for those
two than "switch the format" is for Invoice/Physical Count, and should be called out as such when
it's picked up).

#### Build note (D112)

**The open question resolved: all four were format cutovers.** Waste and Purchase Return already
had generated codes — `waste_document.code` and `purchase_return.return_number`, both via
`InvoiceSequenceService`. All live rows were populated (19 waste, 7 purchase return, 33 purchase
invoice, 13 physical count, none null), so no nullable column, no backfill and no `—` rendering
were needed. Nothing was renumbered; the old and new shapes are disjoint, so a counter restarting
at 1 cannot collide with `3WDN-WST-2026-0001`-style history.

**Physical Count had no second generator — it had a second *composer*.** The old code was not
built inside `PhysicalCountCodeSequenceService`; that service returned an `int`, and
`PhysicalCountService.create` assembled `"PC-" + warehouse.getCode() + "-" + scheduledDate + "-" +
%04d` itself. A search for services or generators would not have found it. The lesson for the next
numbering change: **grep for the string assembly, not for the sequence service.** A sweep of every
site assigning a document code or number found exactly four, one per type, all now on the
allocator; `update` on all four preserves the existing number rather than reallocating.

**`DocumentType` is extended, not duplicated,** and its `document_history.document_type` mapping is
`@Enumerated(EnumType.STRING)` over `varchar(50)`, so appending values is safe. `document_history`
held 0 rows at the time of the change, so the D95 `CountLineAction` ordinal hazard did not apply
either way — but the mapping, not the row count, is what makes it safe.

**The two-constructor bean that would not start.** `DocumentSequenceService` exposes a public
`(JdbcTemplate, TenantTimeZoneService)` constructor and a package-private one taking a `Clock` for
deterministic tenant-year tests. Spring only infers a constructor when a bean has exactly one; with
two and neither annotated, it falls back to looking for a no-arg constructor and **the entire
application context fails to start** — not just this bean. The public constructor is therefore
`@Autowired` and must stay so. This shipped broken and was invisible because the test sources did
not compile, so no `@SpringBootTest` had run since the cutover began. A compiling test tree is what
catches this class of defect; a passing unit-test suite is not.

**The table-name trap — read before touching `invoice_sequence`.** `InvoiceSequenceService` is
deleted, but the table called `invoice_sequence` is **still live and must not be dropped**. It is
the mapping target of `TenantSequenceCounter`, which backs D75's entity codes through
`TenantSequenceService`. The table is shared by year bucket: rows at `year = 0` are D75 entity-code
counters (`MAT`, `SUP`, `WH`, `EMP`, `JOB` — 20 counters live), and rows at a real year are the
retired document counters (`PINV`, `PRET`, `WST`) that `document_sequence` replaced. Dropping the
table on the reasoning that its namesake service is gone would silently break every master-data
code while leaving all document-numbering tests green. `MaterialCodeContinuityIntegrationTest`
exists to fail loudly if that happens.

**What V53 did:** created `document_sequence` with the unique key
`(tenant_id, document_type, year)` and dropped `physical_count_code_sequence` (5 counter rows, no
document data, no reader but its own deleted service). It did **not** touch `invoice_sequence`.

**Coverage.** Concurrency is exercised rather than argued: eight threads on one
tenant/type/year release from a latch together and are asserted to receive 1–8 exactly once each.
The tenant wall-clock year is proved by pinning one instant, `2026-12-31T23:00Z`, and reading it
through two tenant zones — Kiritimati (UTC+14) allocates `PI/27/000001` while Midway (UTC−11)
allocates `PI/26/000001`, from the same server clock. Each of the four services asserts **its own**
`DocumentType` via a captured argument, with the stub matching any type, so Waste silently drawing
from the `PURCHASE_INVOICE` counter fails on the captured value rather than passing a format check.

**No `TO` or `TI` values** were added; D114 introduces them when it has a consumer.

#### O27, as it was raised

> Preserved verbatim from the observation log, which now carries a pointer here. D112 above is the
> decision that answers it.

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

### D113 — Expiry and age: two tracks, one `daysRemaining` column; age is measured per warehouse and never stored. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built — PART B shipped.** The admin-web read surface landed in `fa426b7`: the batch list
> renders `daysRemaining` sorted and coloured with `—` for null
> (`WarehouseStockBatchSubRow.tsx:121-158`), `maxAgeDays` is editable on the warehouse stock row,
> and `expiryTracked` is on the material form. The report and alert surface stay deferred as
> scoped below — that deferral is unchanged, not reopened.

> **Status: backend built; frontend pending.** The five backend slices are recorded in the build
> note below; PART B remains unbuilt.
> *(superseded 2026-09-19: PART B shipped in admin-web `fa426b7` — see the verification note
> under the heading. Preserved because the no-delete rule protects the reasoning, and the
> 2026-09-03 correction beneath it is still the record of the backend slice.)*
> *(corrected 2026-09-03: backend shipped — this line previously read "decided, not built" and
> named five files as a follow-up prompt, all of which had already been changed.)*

Adds shelf-life awareness to the batch layer. **The entire module is read-side except one
guard** (§8) — it does not touch FIFO ordering, the ledger, or any existing write path.

#### 1. Two tracks, discriminated by a flag on the material

| Track | Condition | What governs |
|---|---|---|
| **Dated** | `material.expiryTracked = true` | the printed `expiryDate` on the batch |
| **Fresh** | `material.expiryTracked = false` | age in the current warehouse vs. that warehouse's limit |

Canned goods, dairy, frozen and sauces carry a real printed date and belong to the first track.
Vegetables, poultry, meat and fish carry no reliable date and belong to the second.

**Why the second track exists at all.** For fresh goods the system has no way to know the
starting point — poultry delivered today may have been slaughtered today or three days ago — and
the dominant variable (the cold chain in transit) leaves no trace anywhere in the data. A derived
expiry date for these items would be a **prediction the system cannot make**, presented with the
same authority as a printed date. The first time it is visibly wrong, users stop trusting the
whole column, including the dated half that was correct.

So for fresh goods the system stops predicting spoilage and reports **age**, which is a fact it
owns: receipt dates are recorded, transfer dates are recorded, nothing is estimated. The user
supplies the policy limit; the system asserts the breach with certainty.

> **Rejected: deriving expiry for fresh goods from a per-warehouse shelf life, recalculated
> proportionally on transfer** (`remaining = (1 − daysAtSource / lifeAtSource) × lifeAtDestination`).
> The formula is internally sound and reduces correctly to "carry the date" when both warehouses
> are equal — but every input for a fresh item is an estimate, and each transfer multiplies one
> estimate by another. Its error is largest exactly where the stakes are highest (poultry). A
> confident-looking wrong number is worse than an honest coarse one.

#### 2. Fields

**`material`**

| Field | Type | Meaning |
|---|---|---|
| `expiryTracked` | `boolean NOT NULL DEFAULT false` | selects the track; makes `expiryDate` mandatory to post a receiving document (§7) |

**`stock_balance`** — same row `minimumQuantity` lives on, per `(material, warehouse)`

| Field | Type | Meaning |
|---|---|---|
| `maxAgeDays` | `integer NOT NULL DEFAULT 0` | maximum days this material may sit **in this warehouse**. `0` = not configured |

`0`-means-unconfigured mirrors `minimumQuantity` exactly (D86) rather than introducing a nullable
sibling beside it. A material with `maxAgeDays = 0` never produces a `daysRemaining` and never
raises an alert — see §6.

> **The limit belongs per `(material, warehouse)`, not on the material.** Tomatoes keep for a
> month in the central warehouse's cold room and must not sit two days at a branch. This is not
> an occasional exception to a product-level number — for chilled goods it is the normal case, so
> the warehouse-scoped row is the primary location, not an override. A material-level default is
> a data-entry convenience that can be added later if filling the rows becomes tedious; nothing
> in this design depends on its absence.

> **Known limitation, inherited from `minimumQuantity`'s placement.** `stock_balance` rows are
> created when stock first reaches a warehouse, so a limit cannot be configured for a
> `(material, warehouse)` pair that has never held stock. Accepted for consistency with the
> shipped precedent. The alternative — a separate `material_warehouse_settings` table — was
> considered and rejected as a new table for one column; revisit only if a second
> per-pair configuration field appears.

**`stock_batch`**

| Field | Type | Meaning |
|---|---|---|
| `warehouseEntryDate` | `date NOT NULL` | when this batch entered **this** warehouse |
| `expiryDate` | `date NULL` | as printed on the goods |

**No `originReceiptDate` field is added.** `movementDate` already carries the original supplier
receipt date and travels with the batch through a transfer, so total age is available without a
third date column. See §4.

**No `productionDate` field is added.** It was considered for recall traceability and dropped —
nothing in this design computes with it, and the expiry date answers the operational question.

#### 3. `warehouseEntryDate` — every populating path, by name

`NOT NULL` deliberately, so a path that forgets to populate it fails loudly. This mirrors D10's
prerequisite note about `movementDate`, where a null would have silently sorted a batch last in
FIFO forever with no error raised.

| Path | Value |
|---|---|
| Purchase invoice post | `receiptDate` (identical to `movementDate`) |
| Physical count surplus | `countedAt` |
| Opening balance | ledger record date |
| Purchase return unpost (batch restore) | the original batch's value, preserved |
| **Transfer receipt post** | the receipt document's `actualArrivalDate` — **the only path where this diverges from `movementDate`** |

**Migration:** add the column, then `UPDATE stock_batch SET warehouse_entry_date = movement_date`
before applying `NOT NULL`. For existing rows this is not an approximation — no transfer path
exists yet, so every current batch entered its warehouse on its movement date and the two values
are genuinely equal.

> **Expect a reviewer to ask why two columns hold identical values.** Until the transfer module
> ships they will be identical in every row, because the transfer receipt is the only path that
> separates them. The field is deliberately added ahead of the thing that makes it differ, so the
> expiry module can ship and be tested standalone. **Do not collapse them.**

#### 4. Two ages, one of them free

```text
total age             = today − movementDate          (from first supplier receipt; travels)
age in this warehouse = today − warehouseEntryDate    (resets at every receipt)
```

`daysRemaining` and every alert are computed from the **second**. The first is displayed in the
batch detail and in the report, because it is the only number that catches goods which spent 25
days in the central warehouse and arrive at a branch on a clean counter.

> **Load-bearing dependency on the transfer costing decision.** Total age works only because a
> transfer carries the source batch's original `movementDate` to the destination. If a later
> change re-stamps `movementDate` at the transfer date — or blends several source batches into
> one destination batch with a single synthesized date — total age is lost **silently**, with no
> error and no failing test. The two decisions are coupled; amend them together.

**`today` is the tenant's own wall-clock date (D101), never the server's.** Both figures are
whole days from `date` columns; no fractional days, no timestamps.

#### 5. `daysRemaining` — one column, both tracks

The user's question is never "how long has this been here" but **"how many days do I have left"**,
and both tracks can answer it:

```text
expiryTracked = true   →  daysRemaining = expiryDate − today
expiryTracked = false  →  daysRemaining = maxAgeDays − (today − warehouseEntryDate)
```

Same unit (days), same direction (**lower is more urgent**, negative is overdue), one sort, one
colour rule, one alert threshold. A single screen mixes canned and fresh without ambiguity.

> **Why not a raw "age" column for both.** A batch showing `40` would mean "40 days old, use it"
> for poultry and "received 40 days ago, expires in 2027" for canned tuna — **the same number
> pointing in opposite directions**, which is worse than no column at all. Age answers a question
> that is only meaningful for one of the two tracks; `daysRemaining` answers a question that is
> meaningful for both.

**Age is still exposed**, as a secondary factual column (`today − warehouseEntryDate`). It does
not drive sorting, colouring or alerts.

**Both values are computed in the DTO on every read and stored nowhere.** The general rule they
follow: *persist a number when it is the record of a decision someone acted on (D90's frozen
expected quantity); compute it when it describes the present moment (D2's average cost).* Age is
always "now". Storing it would require a daily job over every batch and would produce a value
that is stale the instant it is written.

#### 6. Silent opt-out is intentional

| State | `daysRemaining` |
|---|---|
| `expiryTracked` and a date is present | from the date. **`maxAgeDays` is ignored entirely** |
| not tracked, `maxAgeDays > 0` | from age in warehouse |
| not tracked, `maxAgeDays = 0` | `null` → renders `—`, raises no alert |
| `expiryTracked` but `expiryDate` is null | `null` → renders `—`, raises no alert |

**`maxAgeDays` never applies to a dated material.** A tin of tuna does not expire in two days
because it is sitting at a branch; the printed date wins outright. No `min()`, no interaction
between the two mechanisms.

The last row is reachable only for batches created **before** `expiryTracked` was switched on for
that material. It must render and sort without error rather than throw. Those batches are listed
in the report so the user can decide whether to enter the dates or let them age out.

> **Built-state correction.** Tracked batches with a null expiry are not only migration history:
> physical-count surplus and opening balance permanently open batches without an expiry date. The
> only production producer of a non-null `LedgerCommand.expiryDate` is
> `PurchaseInvoiceService`. The future missing-dates report must therefore surface current batches
> from those paths as well as batches that predate an `expiryTracked` switch.

A tenant who has configured nothing sees the columns empty and receives no alerts. The module is
adopted material by material, not switched on wholesale.

#### 7. `expiryTracked` blocks the post, not the save

A receiving document for a material with `expiryTracked = true` cannot reach `POSTED` while any
of its lines is missing `expiryDate`. It can be saved, edited and left in `DRAFT` freely.

Same reasoning as D85: constraints belong on the transition, not on the draft. A storekeeper part
way through a delivery, still looking for the date printed on the carton, must be able to save
what he has. **Block the save and he types any date at all to get past it** — which produces
confident wrong data instead of an obvious gap.

The date is captured on the invoice **line** and copied to the batch the line opens.

#### 8. The one write-side guard: transfer dispatch

The single place expiry has teeth rather than merely reporting.

```text
dated:  expectedArrivalDate ≥ expiryDate                              → block
fresh:  (expectedArrivalDate − warehouseEntryDate) ≥ maxAgeDays(destination)  → block
```

This closes a real behaviour in multi-branch operations: the central warehouse clearing
near-dead stock onto branches, which receive it with no indication of its state. Both sides of
the comparison are recorded dates — nothing in the guard is estimated.

> **Deferred to the transfer decision, not implementable in this pass.** The transfer module does
> not exist yet. This section states the rule so that the transfer decision inherits it rather
> than re-deriving it; it is built there, with the warning band (§10) settled at that point.

#### 9. What this module explicitly does not do

- **It does not change FIFO consumption order.** D10 stands untouched — no `ORDER BY` change, no
  index change. Moving to FEFO would change which *cost* is released to COGS, which is a far
  larger decision than adding a field, and would mutate figures in closed periods.
- **It does not block consumption of expired stock — it flags it.** Blocking stops the movement
  from being *recorded*; it does not stop the food reaching the customer, since the cook is
  already holding it. The result is the same meal served and no ledger row, which is strictly
  worse than an alert.
- **It does not write off expired stock automatically.** Expiry is not disposal. Disposal is a
  Waste document (D7) with a person deciding, and that is where the cost should land.
- **It does not model storage zones.** Shelf life is really a property of storage conditions —
  a cold room and a dry shelf inside one warehouse differ. `(material, warehouse)` is a
  deliberate approximation, recorded here so it is not later mistaken for an oversight.

#### 10. Deferred, with the reason

- **"Use first" priority flag and the receipt condition grade** (`good / acceptable / use first`).
  The flag's value was that it lifted a batch to the head of the consumption queue — which is
  precisely the FIFO-order change §9 rejects, and it would additionally hand a user a lever over
  which cost hits COGS. Without that effect the condition grade is a field nobody fills. Both are
  parked together. If revived: restrict to the fresh track, log user/time/reason, make it
  irreversible after any consumption, and treat the `ORDER BY` change as an amendment to D10.
- **The warning band before the §8 dispatch block** — percentage of life used, or absolute days
  remaining. Percentage behaves identically across both tracks and suits the unified column;
  absolute days read more plainly to a user. Settle it with the transfer decision.

#### 11. Definition of done

1. A purchase invoice for an `expiryTracked` material **cannot post** without a line expiry date,
   and **can be saved** in `DRAFT` without one. Both asserted.
2. `warehouseEntryDate` is non-null on a batch opened by each of the four currently-reachable
   paths in §3, verified against live rows — not assumed from reading the code.
3. Migration backfills existing rows and the `NOT NULL` constraint applies cleanly; a live count
   of nulls after migration is zero.
4. A fresh material with `maxAgeDays = 3` received 5 days ago reports `daysRemaining = −2` and
   appears in the alert.
5. A dated material 60 days old with an expiry two years out reports a large positive
   `daysRemaining` and **does not** appear in the alert — its `maxAgeDays`, if set, has no effect.
6. `maxAgeDays = 0` and a null expiry both render `—` and raise nothing.
7. An `expiryTracked` material with a pre-existing null-expiry batch renders `—` without throwing,
   and appears in the report's missing-dates list.
8. `daysRemaining` is absent from the database schema — asserted by a test that fails if a column
   by that name (or `age_days`) is ever added.
9. `today` resolves from the tenant's wall-clock date: a tenant at UTC+3 near local midnight
   computes the same figure the user would (D101).
10. Both locales: the columns are labelled and the negative/overdue state is legible in Arabic
    with Arabic-Indic digits (D12-adjacent screen conventions).

#### 12. Follow-on

The transfer decision consumes this one: `warehouseEntryDate` from the receipt document's
`actualArrivalDate` (§3), the dispatch guard (§8), the warning band (§10), and the
`movementDate`-travels dependency (§4). Do not implement the transfer module without reading §4.

> **Scope correction — the report and the alert surface are deferred to a follow-up pass.**
> Not a reversal: every rule in this decision stands unchanged. What is corrected is that three
> places assumed a surface this pass does not build.
>
> **Superseded references.** §6's closing sentence ("Those batches are listed in the report…"),
> and definition-of-done items 4, 5 and 7 where they require a batch to "appear in the alert" or
> in "the report's missing-dates list". Read those three items as assertions about
> `daysRemaining` alone.
>
> **Why deferred rather than built.** A report has its own shape under D86 — one hand-written
> query, one service in `inventory/reports/`, gated by `INVENTORY_REPORTS_VIEW` — and a dedicated
> alert surface is a screen, not a column. Folding either into this pass would make the diff
> unreviewable and mix a read-model change with a new permission-gated feature.
>
> **What this pass actually surfaces.** The `daysRemaining` and age columns on the existing batch
> list, sorted and coloured, with `null` rendering as `—`. That delivers the operational value
> without a new screen. A batch whose material is `expiryTracked` but whose `expiryDate` is null
> renders `—` and is otherwise invisible until the report exists — accepted for this pass, and
> the first thing the report is built to show.
>
> **Caught by the implementing agent before any code was written**, on reading the decision
> against its implementation prompt. Recorded here rather than fixed silently, so the deferral is
> a stated scope boundary and not an omission someone later reads as a missing feature.

> **Built (backend only), 2026-09-01 — slices: schema/model, batch-date propagation, computed
> batch read, purchase-post guard, and backend tests.** Production opens a new batch at exactly
> one point: `StockBatchService.createBatchFromInbound`, called only by `InventoryLedgerService`.
> Its reachable producers are purchase invoice, physical-count surplus, and opening balance;
> purchase-return unpost restores the existing row instead of constructing another one. Keeping
> the assignment at this single point, together with the database `NOT NULL`, means a future
> batch-opening producer — transfer receipt first — cannot pass a null `warehouseEntryDate`
> through silently: it must attach at and update this construction point. `TRANSFER_IN` remains
> dormant with no producer, consistent with D10's build note; it is the hook for that module.
>
> V52 added `material.expiry_tracked`, `stock_balance.max_age_days`, `stock_batch.expiry_date`,
> `stock_batch.warehouse_entry_date`, and `purchase_invoice_line.expiry_date`.
> `warehouse_entry_date` was added nullable, backfilled, then made `NOT NULL` in three separate
> statements. On the dev database, Flyway records V52 at 2026-09-01 23:27:41; all 61 current
> batches predate that installation, so the backfill covered 61 rows. The live post-migration
> counts are 0 null `warehouse_entry_date` values and 0 mismatches against
> `movement_date::date`.
>
> **Definition-of-done evidence correction.** Item 2 is met by test, not by live-row
> verification. Zero batches had been created after V52 installed when the review measured the
> database, so the 0-null and 0-mismatch figures above prove the migration backfill, not the new
> write path. `StockBatchServiceTest` covers the three batch-opening producers plus restore, and
> `ExpiryAndAgeIntegrationTest` covers the persisted purchase-invoice path.
>
> The backend invariants are pinned by
> `ExpiryAndAgeIntegrationTest.datedExpiredBatchReturnsNegativeDaysRemainingAndIgnoresMaxAge`,
> `ExpiryAndAgeIntegrationTest.zeroBoundaryReturnsZeroForDatedAndFreshTracks`,
> `PhysicalCountReconcileIntegrationTest.trackedMaterialCountSurplusHasNullExpiryAndCountedWarehouseEntryDate`,
> the purchase-line-to-batch trace in
> `ExpiryAndAgeIntegrationTest.purchaseInvoicePostCopiesLineExpiryAndReceiptDateToLiveBatch`, and
> the no-derived-columns schema guard in
> `ExpiryAndAgeIntegrationTest.schemaStoresOnlySourceDatesAndKeepsBothBatchDatesSeparate`.
>
> One full run omitted `ExpiryAndAgeIntegrationTest` entirely: its Surefire report remained the
> stale 23:53 report from the focused run while the full run began at 23:54, producing a total of
> 736. A repeat full run and a clean full run both collected all seven methods and completed 743
> tests with 0 failures, 0 errors, and 0 skipped. The cause was not established. The leading but
> unconfirmed explanation is that the full run began while the focused run's target state was
> still settling, a condition a clean CI run would not have.
>
> **Test-count correction.** The 743 figure above is stale and came from an uncommitted
> intermediate tree. At `c9a7716`, the committed D113 code exists but the test tree does not
> compile because tests still reference the deleted sequence services. The only reproducible
> clean baseline from the completed D112/D113 tree is 752 tests.
>
> **Surefire report hazard:** a stale XML report is indistinguishable from a passing one to
> anything that sums `target/surefire-reports/*.xml` without checking timestamps — a class that
> stopped running reads as green. The mitigation is for CI to run `./mvnw clean test`, not an
> incremental `test`; this repository currently has no checked-in CI definition, so neither is
> enforced. PART B is not built; D113 remains `🕓` until the frontend read surface ships.
>
> *(superseded 2026-09-19: PART B shipped; D113 is `✅`. The Surefire hazard and the
> missing CI definition above are unaffected and still stand.)*

### D114 — Warehouse transfer: two linked documents, blind receipt, batch-snapshot costing. 🕓

> **Status: decided, not built.** D112 is built; numbering needs only the two transfer enum values
> with prefixes `TO` / `TI` — see §14.
> *(corrected 2026-09-03: D112 shipped — this line previously read "Blocked on D112".)*

Transfers move stock between warehouses under a control designed to make loss visible and
attributable rather than to make it impossible. The controls are: stock leaves the source before
it arrives at the destination, the receiver reports quantities without seeing what was sent, and
nothing can make a transfer disappear.

---

#### 0. What already exists, and why it is replaced rather than adapted

A discovery pass inventoried the repository before any code was written. Findings:

**A dormant transfer schema exists** — `InventoryTransfer`, `InventoryTransferLine`,
`TransferStatus (DRAFT, IN_TRANSIT, COMPLETED, CANCELLED)`, created by
`V9__operations_physical_count_transfer.sql` with foreign keys in `V10`. `TRANSFER_IN` and
`TRANSFER_OUT` already exist in `InventoryTransactionType` and in the ledger's check constraint.

**It is entirely unreachable**: no controller, no service, no repository, no DTO, no test, no
production reference. **Both tables hold zero rows.**

**It is the design this decision rejects.** One combined document with dispatch and receipt
timestamps on the same row; one combined line carrying requested, dispatched *and* received
quantities, both transaction ids, and **a single blended `unitCostSnapshot`**. That is §1's
single-document model and §7's blended costing — the two shapes rejected here after they were
considered on their merits.

**Therefore: dropped and recreated, not migrated.** Zero rows and no producer make replacement
free of data conversion, and adapting D114 onto a schema built for the rejected model would drag
the rejected model's columns into the new design. The migration drops
`inventory_transfer` and `inventory_transfer_line` explicitly. `TRANSFER_IN` / `TRANSFER_OUT` and
the tenant/warehouse/material/UOM foreign-key infrastructure are reused as-is.

> **The existing frontend transfer pages are a liability, not a starting point.** Routed screens,
> types and an API service exist for the single-document lifecycle, calling
> `/api/inventory/transfers` routes that do not exist — a non-functional shell that encodes the
> rejected design in its types. The frontend pass **replaces** them; it does not adapt them.

---

#### 1. Two documents, not one

| Document | Code (D112) | Created by | Effect of `POSTED` |
|---|---|---|---|
| **Transfer Out** (dispatch) | `TO/YY/NNNNNN` | a user, at the source | stock leaves the source |
| **Transfer In** (receipt) | `TI/YY/NNNNNN` | **the system**, when the dispatch posts | stock lands, variance recorded |

Both carry a shared `transferRef` so the pair reads as one movement in the UI and in reports,
while each keeps its own number, its own lifecycle and its own audit trail.

**Both use the standard `DRAFT → COMPLETE → POSTED` vocabulary (D97) with no new words.** A single
document would have needed two complete/post cycles and a bespoke status machine; two documents
model what actually happens — two events, two places, two responsible people, two dates.

**In transit** is not a status. It is the state where the dispatch is `POSTED` and its receipt is
not — derivable from the pair, requiring no new enum value and no phantom warehouse.

---

#### 2. Five rules that make the receipt document a control rather than a hole

1. **It has no creation endpoint.** No "new receipt" button, no `POST`. It is created server-side
   only, inside the transaction that posts the dispatch. A receipt a user can create from nothing
   is a path to creating stock from nothing.
2. **Its lines are fixed.** The receiver enters a quantity against each dispatched line and can do
   nothing else — no adding a material, no deleting a row. Nothing received is a line with zero,
   never a deleted line.
3. **It cannot be deleted or cancelled.** The only way to resolve it is to post it — including
   posting a receipt of zero, which records a total loss and demands an explanation. **There is no
   way to make a transfer disappear**, which is what closes "it was dispatched, it was never
   received, blame the road".
4. **The dispatch has no unpost.** Not conditional, not while the receipt is untouched. Same
   reasoning as D7: a dispatch records that goods physically left. A mistake is corrected by a
   reverse transfer carrying its own documents, never by erasing. This closes the classic
   manipulation — dispatch 10, receiver reports 8, source edits the dispatch down to 8, variance
   gone.
5. **The dispatched quantity is not serialized onto the receipt while it is open.** See §3.

`Cancel` is available on the **dispatch only, from `DRAFT`**. Delete follows D6's dual check —
`DRAFT` **and** no ledger rows.

---

#### 3. Blind receipt: the hiding is in the API, not the UI

While a receipt document is `DRAFT` or `COMPLETE`, its response DTO **must not carry the
dispatched quantity**. After it is `POSTED`, the DTO carries dispatched, received and variance
together.

The receipt line holds a foreign key to the dispatch line, so a careless serializer that expands
the association leaks the number the whole mechanism exists to withhold. **A frontend that hides a
field the endpoint returns is not blind receiving** — anyone can read it from the network tab.
This is a DTO-shape invariant, asserted by test, not a UI convention.

The two-document shape already helps: the dispatched quantity lives on a different document
belonging to a different branch, so ordinary tenant/branch scoping hides it without a special
rule. The invariant above exists because the FK makes that accidental rather than guaranteed.

> **The paper leak is real and is not solved here.** If the delivery note the driver carries shows
> quantities, blind receiving ended before it began. When printing is built, the receiving copy
> prints materials without quantities. Recorded so it is designed rather than discovered.

---

#### 4. No partial receipt

A receipt is posted exactly once, for the whole document.

> **"Received 6 of 10" is not a partial receipt — it is a variance of 4.** Those four units are a
> loss from that moment, not stock still in transit. They cannot arrive tomorrow, because they
> already left the source and no document is open to land them in.

**Why partial receipt is rejected rather than deferred.** Goods split across two vehicles on two
days have two custodians, and a partial receipt makes the shortfall unattributable to either —
which defeats the only purpose the receipt has. A user who genuinely needs to send goods in two
trips creates two transfers.

**The operational rule the whole design rests on: one dispatch = one vehicle = one custody
handoff.** Nothing in the code enforces it; a storekeeper can raise one dispatch and load it onto
two trucks. §5 is what makes that visible.

---

#### 5. Custody: the carrier field

The dispatch carries a `carrier` field (free text — a driver's name is enough; a lookup is not
justified until someone asks).

Without it the variance report can group by warehouse, by route and by user, but **never by the
person who actually held the goods** — which is the dimension §4's reasoning depends on. The
question the report exists to answer is *"which carrier recurs on every shortage?"*, and a
one-off shortage is noise while a pattern against one name is not.

---

#### 6. Dates, and the two different alerts they produce

- `expectedArrivalDate` — entered on the dispatch.
- `actualArrivalDate` — entered **by the receiver, by hand**, on the receipt. **Never stamped from
  the posting instant**, because a receipt is routinely posted hours after the vehicle arrived.

These are two distinct signals, and collapsing them into one loses the more serious:

| Condition | Meaning |
|---|---|
| `today > expectedArrivalDate` and the receipt is not `POSTED` | **The transfer is late.** |
| `postedAt − actualArrivalDate` is large | **Goods sat at the destination unrecorded.** |

The second is the dangerous one — it is the window in which stock is present, unbooked, and
unattributable.

---

#### 7. Costing: the batch snapshot

**Dispatch** consumes FIFO from the source exactly as any other outbound movement does (D10
ordering unchanged), and records what it consumed as child rows of the transfer line:

`transfer_line_batch` — per source batch: `quantity`, `unitCost`, `movementDate`, `expiryDate`.

**Receipt** allocates any shortage **pro-rata across those rows**, then opens **one destination
batch per surviving row**, preserving that row's `unitCost`, `movementDate` and `expiryDate`.
`warehouseEntryDate` on every new batch is the receipt's `actualArrivalDate` (D113 §3).

```
dispatched 10 kg  =  2 @ 10  +  8 @ 12          → 116 total
received    9 kg  →  shortage 1, allocated 0.2 / 0.8
shortage value    =  0.2×10 + 0.8×12  =  11.6
destination gets  =  1.8 @ 10 (its date)  +  7.2 @ 12 (its date)
```

> **Rejected: collapsing the shipment into one blended-cost batch** (`116 / 10 = 11.6`, one
> destination batch of 10 at 11.6, shortage priced at 11.6) — which is also the shape the dormant
> `unitCostSnapshot` column in §0 was built for.
>
> The argument for it is correct as far as it goes, and is recorded because it is right: the
> missing kilogram has no identity, any allocation of it is a convention rather than a fact, and
> **the blended unit cost is arithmetically identical to the pro-rata allocation** — 11.6 either
> way. No precision is lost in pricing the shortage.
>
> It does not follow that the surviving units lose their identity too. Those are two different
> questions: what the *missing* units were is unknowable; what the *surviving* units cost and how
> old they are is fully known at dispatch. Blending discards the second to settle the first.
>
> What blending actually costs — none of it about price:
> - **Batch age.** One blended batch carries one `movementDate`. Goods received in January and in
>   August cannot share one honest date, and stamping the transfer date makes a transfer an
>   **age-reset**, contradicting D10's premise that FIFO models physical rotation.
> - **Traceability.** "Which branches received the delivery from supplier X on the 12th?" is a
>   live question in a food business — spoiled goods, a supplier complaint — and blending erases
>   the answer.
> - **It is a one-way door.** Blended history cannot be un-blended (cf. D89's historical-data
>   note). Carrying the snapshot and later ignoring it is free.
> - **Rounding.** `116/10` divides cleanly; `100/3` does not, and `33.333333 × 3` no longer equals
>   the value consumed. The snapshot never divides, so *value of destination batches = value
>   consumed at source* holds exactly.
>
> The engineering cost of the snapshot is one child table and a loop instead of a single insert.

---

#### 8. Variance

**Shortage (received < dispatched).** Allowed, never silent. Posting requires a reason on the
document. The value — computed per §7 — is written as a ledger row with
`referenceType = TRANSFER_VARIANCE` and **no stock-balance mutation**: the goods are in no
warehouse to deduct from. Per D98 the loss reports filter on `reference_type`, so this slots in
without touching transaction-type filters.

**The loss is attributed to the source**, with the carrier recorded. The destination reported it;
charging the destination is the fastest way to stop people reporting.

**Surplus (received > dispatched) is not receivable.** The quantity is capped at what was
dispatched. There is no batch behind the excess and no cost for it, and **creating value from
nothing is worse than the discrepancy it would paper over**. It is flagged; the correction is a
physical count at the source, per D89's "an error is corrected by counting again, never by
erasing".

---

#### 9. In-transit is derived, not stored

The quantity and value in transit are computed from `transfer_line_batch` rows belonging to
transfers whose dispatch is `POSTED` and whose receipt is not. No stock-balance rows, no
in-transit warehouse.

> **Rejected: a system-owned "In Transit" warehouse.** It would reuse the batch machinery for
> free, but it mixes batches from unrelated transfers into one FIFO pool — so receiving transfer A
> could consume transfer B's batches — and it needs excluding from every picker and from Low Stock
> while being included in Stock Valuation. The snapshot answers the same question with none of it.

In-transit value belongs in Stock Valuation: it is stock the tenant owns.

---

#### 10. The dispatch guard — corrects D113 §8's field

A dispatch is blocked when the goods would arrive already dead:

```
dated (expiryTracked):  expectedArrivalDate ≥ expiryDate                          → block
fresh:                  (expectedArrivalDate − movementDate) ≥ maxAgeDays(destination) → block
```

> **D113 §8 wrote the fresh case against `warehouseEntryDate`. That is wrong and is corrected
> here** — same shape of defect as D93's correction to D90's window bound: the rule was right, the
> field was not. `warehouseEntryDate` **resets at every receipt**, so on a second hop
> (central → branch A → branch B) the goods present as newly arrived and the guard passes goods it
> exists to stop. `movementDate` is the immutable origin date and travels, so the guard composes
> across any number of hops. The two fields are equal on a first hop, which is why the error is
> invisible until the transfer module has been in use for a while — and why a one-hop test cannot
> distinguish the correct implementation from the wrong one.

Both operands are recorded dates; nothing in this guard is estimated. It closes the behaviour of a
central warehouse clearing near-expiry stock onto branches that receive it with no indication of
its state — and it is the only place D113's expiry data has teeth rather than merely reporting.

A **warning band** below the block threshold is deliberately **not specified**. See §13.

---

#### 11. Permissions

Two, deliberately separate: **dispatch** and **receive**.

Segregation of duty is not implemented as a code rule — the owner controls access and decides who
holds what, and the default posture is that a storekeeper does both sides of their own warehouse.
Two permissions are what makes the separation *available* to an owner who wants it; one permission
would make it impossible regardless of intent.

In practice the two sides fall to different people anyway, because users are branch-bound and the
two warehouses are in different branches.

---

#### 12. What this decision does not do

- **It does not change FIFO consumption order.** The dispatch is an ordinary outbound consumer.
- **It does not add an in-transit status, an in-transit warehouse, or an in-transit balance row.**
- **It does not implement approval workflow.** O20's approvals are unbuilt; the word "approve" is
  not used here (D97).
- **It does not print.** The receiving-copy rule in §3 is recorded for whoever builds printing.

---

#### 13. Not decided

- **The warning band before the §10 block** — percentage of life used, or absolute days remaining.
  Percentage behaves identically across both expiry tracks and suits D113's unified column;
  absolute days read more plainly. Left open in D113 §10 and still open.
- **Who is bound to a branch-less warehouse.** §11 leans on users being branch-bound, but D86
  records that a central warehouse belongs to no branch and is typically the tenant's largest
  stock value. Whoever is scoped to it is undefined, which means the natural segregation §11
  assumes may not hold for exactly the transfers that matter most. Resolve before relying on
  branch binding as a control.
- **Whether a reverse transfer needs its own document type** or is an ordinary transfer in the
  opposite direction with a reference to the original. §2's rule 4 makes it the only correction
  path, so it will be used; nothing yet says it must be distinguishable in reports.

---

#### 14. Dependencies

- **D112 — built prerequisite.** `DocumentSequenceService` now serves all operational document
  numbering, and both former document generators are deleted. D114 needs only two transfer values
  added to `DocumentType`, with `TO` / `TI` prefixes, when their consumers are built; it must use
  the shared allocator rather than add another generator.
  *(corrected 2026-09-03: D112 shipped — this bullet previously read "hard blocker, confirmed by
  discovery … there is no `DocumentSequenceService`", which was true when written and became false
  when D112 landed. Corrected in place rather than preserved, because the no-delete rule protects
  reasoning, not stale facts: leaving it would have misled the next reader of D114 exactly as the
  review's F3 described.)*
- **D113** (`warehouseEntryDate` populated from `actualArrivalDate`; the §10 guard; the expiry and
  age fields the guard reads).
- **D113 §4's coupling, in the other direction.** D113's total-age figure works only because a
  transfer carries the source batch's original `movementDate` to the destination — which §7 does.
  Re-stamping it at the transfer date, or blending several source batches under one synthesized
  date, silently destroys total age with no error and no failing test. §7 and D113 §4 are amended
  together or not at all.

### D115 — Expenses: a flat record of money that left with no stock behind it. No lines, no lifecycle, no document code. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built (backend + admin web).** `V54__expenses.sql`, `ExpenseController.java:41-104`,
> `restaurant-saas-web/src/pages/expenses/`. Flat row, no lifecycle, no document code, nullable
> branch, and the four permissions exactly as tabled. Separation of duties remains stated and
> unenforced, as written.

The module answers one question: **where did the money go**. It is not an accounting module.
O16's rejections stand unchanged — no journal entries, no chart of accounts, no balance sheet, no
equity.

**The boundary, which is the load-bearing part of this decision.** A purchase invoice is **not**
an expense. Material purchases enter stock and become cost when they are consumed and sold
(COGS, via `OrderConsumptionDoc` / the ledger). An expense recorded for the same purchase would
be counted a second time, and the eventual P&L would overstate cost by exactly the food bill —
the largest line in a restaurant. The rule, which must appear in the `D`-entry, the module doc,
and the create form's own helper text:

> **Anything that enters a warehouse has a purchase document, not an expense.**
> An expense is money that left with no stock behind it.

In scope: rent, electricity, water, gas, repairs, maintenance, salaries, marketing, cleaning
consumables, licences, transport, phone/internet.

**Shape: one flat row.** One expense = one amount, one category, one date. No header/line split —
an electricity bill has nothing to put in lines, and a user buying three things from one shop can
enter one row or three at their discretion. No `LineSchema`, no `useDocumentLines`, no line table
(D13; and the same reasoning that kept stock balances and physical counts out of that hook).

**No lifecycle.** Unlike inventory documents (D6/D7/D8) there is no DRAFT → COMPLETE → POSTED.
A user with the permission writes the row and it is immediately real. This resolves O16's second
open question. Approval, if it is ever wanted, is the `ApprovalWorkflow` track (O3/O20) applied
from outside — not a status column added here pre-emptively.

**No document code.** D112's `{TYPE}/{YY}/{NNNNNN}` allocator covers documents with a lifecycle
and a business identity; an expense row has neither and is addressed by its numeric `id` like any
other record. Adding an `EX` type now would produce a sequence with no reader. If a printed
reference is ever needed, the allocator already takes the type as a parameter and the addition is
two lines (D112).

**Branch is nullable and means what it says.** `branchId IS NULL` = a company-level expense (head
office, owner's vehicle, group marketing). This is real and must be permitted. It carries one
reporting rule: **a branch-scoped report never allocates unbranched expenses onto branches.** Any
apportionment — by floor area, by revenue share, by headcount — is cost accounting, which O16
rejects. Unbranched rows are shown on their own line or excluded, never spread.

**Four permissions**, following the read/write split already used elsewhere (D52):

| Permission | Gates |
|---|---|
| `EXPENSES_VIEW` | all reads, expenses and categories |
| `EXPENSES_CREATE` | creating an expense |
| `EXPENSES_VOID` | voiding an expense (D117) |
| `EXPENSES_CATEGORY_MANAGE` | creating/editing/deactivating a tenant category (D116) |

`EXPENSES_VOID` is separate from `EXPENSES_CREATE` deliberately: once expenses can explain a cash
shortfall, the person who writes an explanation should not also be the person who can erase one.

**Separation of duties is stated, not enforced.** The intended policy is that no user holds both
`EXPENSES_CREATE` and a POS/shift-closing permission — otherwise a cashier can explain away his
own drawer variance. **No code enforces this**, and none is added here: permission-combination
rules have no home in the current RBAC model (D36) and inventing one for a single case would be
premature. It is a configuration responsibility, and belongs in the permissions-screen redesign
(O20) if it is ever to be surfaced.

**Not modelled, deliberately, none of them tracked as gaps:** VAT/input tax on an expense (there
is no tax module to feed); recurring/scheduled expenses (D13 — nothing has needed one; the user
enters twelve rows a year); a `Supplier` FK — the payee is free text, because `Supplier` is an
inventory entity bound to purchase invoices and widening it to cover the plumber and the
electricity company changes what it means for the module that owns it.

### D116 — `ExpenseCategory` is a table with global seeded defaults, not a backend enum. This diverges from D47 on purpose. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `expense_category` with nullable `tenantId` and the thirteen global rows seeded in
> `V54__expenses.sql:71-83`; deactivate-only, no delete. `system_key` is still absent, as scoped —
> O48/O50 own it.

Resolves O16's first open question.

**Why not the enum.** D47 made asset category a fixed enum and was right to: there, category is a
secondary attribute over five broad buckets that genuinely cover the domain. Here the category
**is the product**. The whole question the module exists to answer — *where did the money go* — is
answered by the category and nothing else. A fixed enum guarantees an `OTHER` bucket, and `OTHER`
grows until it holds the largest share of spend, at which point the module reports nothing. That
is the difference, and it must be written down: this is the same reasoning as D47 applied to a
case where the answer comes out the other way, not an inconsistency with it.

**Shape mirrors `MaterialCategory` exactly** — `common/BaseEntity` with its own **nullable**
`tenantId` (see CONVENTIONS, "Rows that can be global"):

- `tenantId IS NULL` → a global seeded default, visible to every tenant, **read-only to tenants**
  (no rename, no deactivate).
- `tenantId` set → tenant-created, fully editable and deactivatable by that tenant.

Resolution is the same predicate `Uom` and `MaterialCategory` already use: global rows plus the
caller's own.

**Accepted trade-off:** a tenant cannot rename or hide a global default they dislike. This is
accepted for now because they can always add their own alongside, and because per-tenant seeding
would require a tenant-provisioning hook that is not confirmed to exist. Revisit only if a real
tenant asks — do not build a per-tenant override table pre-emptively.

**No `systemKey` column in this pass.** It was designed — a stable key so that future
auto-posting code (asset maintenance, payroll) can resolve "the maintenance category" without
matching on a display name. It is **not built**, because nothing writes system-sourced expenses
yet and a column no code reads is the same dormant schema D114's discovery pass had to untangle.
Whichever pass first posts an expense from another module adds the column and the constraint;
that is named in O48 and O50 as part of their scope.

**Seeded global defaults** (`name` / `nameAr`), all ordinary categories with no special status —
including maintenance and salaries, which are entered by hand until O48/O50 land:

Rent/إيجار · Electricity/كهرباء · Water/مياه · Gas/غاز · Salaries & wages/مرتبات وأجور ·
Maintenance & repairs/صيانة وإصلاحات · Cleaning & consumables/نظافة ومستهلكات ·
Marketing & advertising/تسويق ودعاية · Licences & government fees/رخص ورسوم حكومية ·
Internet & phone/إنترنت وتليفون · Transport & delivery/مواصلات وتوصيل · Bank & payment fees/رسوم
بنكية ومدفوعات · Other/متنوع

**No delete on categories, only deactivate** — consistent with the soft-deactivate convention.
A deactivated category stays readable so historical rows still render their name, and is excluded
from the create form's picker. This is the same reason D111's UOM lookup must return inactive
rows: a row referenced by history has to keep rendering after it is retired.

### D117 — An expense is append-only. Correction is a void with a reason, never an edit and never a delete. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** No `PUT` and no `DELETE` on `/api/expenses`; correction is `POST /{id}/void` behind
> `EXPENSES_VOID` (`ExpenseController.java:103-104`).

There is no `PUT /api/expenses/{id}` and no `DELETE`. Correction is
`POST /api/expenses/{id}/void`, which sets `status = VOIDED` and stamps `voidedBy`, `voidedAt`
and a **required** `voidReason`. Reports and totals sum `ACTIVE` only. A voided row is never
hidden from the list — it renders struck through with its reason visible.

**The reason is loss prevention, not tidiness.** Once an expense can explain a cash shortfall,
a mutable expense is a way to erase one: record something, watch the variance land on zero, then
edit the amount afterwards. A void leaves the opposite trace — *somebody wrote an explanation and
then removed it* — which is itself a finding worth surfacing.

Consistent with the module's other write rules: the inventory ledger is never mutated (D1/D3) and
asset acquisition lines are immutable after creation (D110). Expense diverges from D110's
delete-and-recreate only because a deleted expense leaves no trace, and here the trace is the
point.

**Amount is strictly positive.** No negative expenses, no credit rows. A refund from a supplier
is not modelled at all in this pass; if one occurs the original is voided and, if partial, a new
expense is entered for the net. Stated so nobody adds a sign convention later.

### D118 — Two timestamps, both first-class. `paymentSource` ships now; the shift link does not. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built, and the deferral below has since been closed.** `expenseDate`, `createdAt` and
> `paymentSource` shipped in `V54`. **`paidFromShiftId` is no longer deferred** — it landed in
> `V57__expense_paid_from_shift.sql` under D124/O51, with the manager-selected shift and the
> frozen `Shift.expensesAtClose`. Read the "deliberately not in this pass" paragraph as history.

**`expenseDate` (`DATE`) is when the money left. `createdAt` (`TIMESTAMP`) is when it was written
down.** Both are stored, both are exposed, and **the gap between them is a signal, not metadata.**
An expense entered twenty minutes later is routine. One entered three days later, for exactly the
amount a shift closed short, is the thing the shift module will be built to catch. `expenseDate`
is user-supplied; `createdAt` is stamped by `TenantTimestampListener` and is never set by hand
(CONVENTIONS).

**`paymentSource: CASH_DRAWER | CASH_ON_HAND | BANK`**, non-null. `CASH_DRAWER` means the money
came out of a cashier's till — the flag the expenses screen needs so a drawer payout is
distinguishable from a bank transfer.

**`paidFromShiftId` is deliberately not in this pass.** The shift module's own decisions are still
being drafted (opening float carry-over, handover variance, the freeze-at-close rule, the
treatment of an expense recorded after close), and an FK whose semantics are unsettled is worse
than a missing one. Today `CASH_DRAWER` is a flag with no link. The column, the resolution of the
branch's open shift at create time, and the freeze/late-arrival rules all land together in the
shift pass as one additive migration — tracked as **O51**.

**One rule from the shift design is fixed now, because it constrains that migration**: a shift's
closing figures are frozen at close and are **never recomputed**. An expense recorded against an
already-closed shift is stored and linked, but does not alter the stored variance; it surfaces in
a separate adjusted column alongside it. The frozen number is the only witness to what was in the
drawer at the moment it was counted, and overwriting it destroys the evidence the module exists to
produce.

### Shift implementation audit.

The Phase 0 audit of the existing shift module found that it contradicts D119-D125 on every
material axis, so the implementation is now a rewrite rather than an extension.

| Fact | Evidence |
|---|---|
| Shift is owned by a **cashier**, not a drawer; no drawer/station/terminal entity exists anywhere | `Shift.java:36`, `V22__shift.sql:11` |
| Open-shift uniqueness is **application-enforced by design** -- the migration says so and creates no index | `V22__shift.sql:2-4` |
| An existing open shift raises `SHIFT_ALREADY_OPEN`; the service never resumes | `ShiftService.java:44-56` |
| Cashier identity comes from **`X-User-Id`**, independent of the JWT; also whitelisted in CORS | `ShiftController.java:32-58`, `CorsConfig.java:32-33` |
| Close does **not** verify ownership | `ShiftService.java:86-104` |
| **All three shift endpoints require `SHIFTS_OPEN`**, and the seeded `CASHIER` role holds it | `ShiftController.java:29-58`, `V3__role_permission_seed.sql:14-18` |
| `expectedCash` and `cashVariance` are computed and **returned to the caller**; `GET /current` returns the expected figure **before counting** | `ShiftService.java:75-82`, `133-149` |
| The POS **renders** the expected figure before the count and the variance after close | `restaurant-pos/src/pos/components/ShiftClose.tsx:47-63` |
| A sign-out button exists; it drops the local shift without closing the server shift | `Shell.tsx:27-33`, `usePos.tsx:713-730` |
| **The POS completes and cancels orders offline**; shift open/close are not outboxed | `usePos.tsx:1135-1162`, `1008-1029` |
| Order creation selects an open shift by **header-supplied cashier id**, and does not check the shift's branch matches the order's | `OrderService.java:168-172` |

Two of these are live defects independent of this design: any `CASHIER` can close any shift in
the tenant, and an order can be attached to a shift in a different branch.

### D119 — The device is the drawer. No drawer entity. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `V56__shift_device_rewrite.sql` — `shift.device_id NOT NULL`, no drawer entity, no
> stored balance, counts on the shift, and `uk_shift_open_per_device`.

*Revision 2026-09-05: the original text specified a `CashDrawer` entity referenced by the shift.
Superseded -- the drawer is 1:1 with the cashier device.*

A drawer sits under one machine and does not move. Modelling it as a separate entity in a 1:1
relationship with `Device`, carrying no fields of its own, is an abstraction with one caller --
D13. **The shift references `deviceId`.**

This is not only simpler; it removes work and closes a hole:

- No new entity, no table, no tenant setup step, and **no drawer-discovery contract**.
- The POS sends **no drawer identifier**. The device is known from device authentication, so the
  drawer cannot be misreported.
- **The branch is derived from the same device as the shift.** Selecting a shift by device
  alone does not prevent a mismatch if the order still trusts a separate branch header.
  Order creation must use the fetched device's branch for both the order and warehouse (D41).
- Uniqueness becomes `(deviceId) WHERE status = 'OPEN'`.

**Accepted limit:** two devices sharing one physical drawer, or a drawer moving between devices,
cannot be expressed. Neither is a current reality. If one becomes real the split is a migration,
not a reason to build the entity now for a case nobody has.

**No stored balance.** The device drawer's balance is **derived on read**, never persisted:

```
balance = last count
        + cash orders since that count
        - cash refunds
        - expenses recorded against it
```

A stored balance column would be a second copy of a truth that already exists, and two copies
drift. That is not hypothetical: **O27** is exactly this failure -- `subtotal + taxAmount` no
longer agrees with `totalAmount` by fractions. Here the drifting number would be money people are
held accountable for.

**Cash sales are never written into a drawer ledger.** They live on orders, which already carry
`shiftId`. Writing them a second time would create the same two-copies problem inside a single
feature.

**Nothing writes to the drawer during a shift.** Between the opening and closing counts the system
records no drawer movement at all. There is no drawer transaction type invented for this module,
and specifically **no float top-up or safe-drop type**: those were designed and then removed,
because neither happens in practice. Adding types with no producer is the dormant-schema problem
D114 had to untangle -- D13.

**Counts live on the shift. No separate count table.** Counting happens at exactly two moments,
each of which already has a row: `openingCount` and `closingCount` on `Shift`. A separate table
would be one-to-one with the shift and never queried without it.

This holds only while those are the only two counting moments. A spot count (O56) is a count
belonging to neither, and it is the change that would justify extracting the table -- noted there
so it reads as an extraction rather than a redesign.

**Surplus and shortfall are one column, and surplus is not the lesser finding.** A drawer that
persistently runs over is at least as strong a signal as one that runs short: it means money is
being taken in that the system was not told about. An implementer who treats positive variance as
benign has removed half the detection.

**Where a variance shows up.** Nowhere in any balance, because no balance is stored. The next
shift starts from the counted figure, so a shortfall drops out of the arithmetic automatically.
This is why no adjustment movement is needed: the count *is* the reconciliation.

Variances surface only in reporting (D125), and **the cumulative figure is what catches theft, not
the single shift**. Honest error scatters around zero; theft accumulates in one direction.

### D120 — Shift lifecycle: open and closed. A mandatory blind count at each end. No sign-out. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** Open/closed only, `business_date` a real column fixed at open,
> `opened_by_user_id`/`closed_by_user_id` from the JWT principal, and one open shift per device
> enforced by a partial unique index rather than a service check. The `businessDate` inherit
> branch is deliberately not coded — see the implementation note below, and D120's deferred
> continuity-across-close rule.

```
OPEN -> (count + close) -> CLOSED
```

There is no approval step and no pending state. **A design requiring a manager to approve every
close was considered and rejected on operational grounds**: a daily approval a manager has no
time to perform becomes a button pressed without looking, which is worse than no approval at all
because it manufactures the appearance of oversight. The manager's attention belongs on the
exceptional case, not the routine one.

**Counting is mandatory at both ends and is blind (D123).** A single count serves two purposes: it
closes the account of the period before it and opens the next. This is what makes a variance
attributable to a bounded period rather than to a vague stretch of time.

**There is no sign-out button.** Closing the shift is the only way to leave. This removes the one
path by which a cashier could end a session without counting -- and without it, consecutive
shifts' variances merge into a single figure that cannot be separated or attributed to either
person.

**Ordering at login.** The client asks whether an open shift exists **before** rendering the cash
keypad. The existing flow asks for the count first, then discovers the open shift server-side and
resumes onto it -- silently discarding the number the cashier just entered. A user entering a
figure the system throws away is never acceptable, regardless of consequence.

**Same cashier returning to their own open shift resumes it.** No count, no close, no event.

**One `OPEN` shift per device, enforced by a database constraint, not a service check.** The
device represents one physical drawer; two open shifts against it would be two accounts of the
same money and no variance could be attributed to either.

**Identity comes from the JWT principal.** `openedByUserId` and `closedByUserId` are separate
fields, both taken from the token, never from a request header.

**`businessDate` is a property of the shift, fixed when it opens**, and is a real column -- not
derived from `openedAt` at read time:

```
open shift exists on this device  -> inherit its businessDate
otherwise                          -> LocalDate.now(branch zone)     [D101]
```

A shift opening at 22:00 and closing at 03:00 belongs entirely to the earlier day. **A clock-based
day boundary was considered and rejected**: any cut-over time splits overnight shifts across two
days and mis-assigns a shift that opens fifteen minutes before it. Orders and expenses take the
`businessDate` of *their shift*, never the date of their own timestamp.

**There is no end-of-day event and none is needed.** The day boundary is inferred at open, from
the date comparison above, with no scheduled job and no "daily close" button.

*Implementation note, 2026-09-06: the inherit branch above is **not implemented, by decision**.
It is unreachable as written -- a shift is only created once no open shift exists on the device,
which `uk_shift_open_per_device` also enforces -- so the rule reduces to
`LocalDate.now(branch zone)` and that is what `ShiftService.resolveBusinessDate` does. The dead
branch was deliberately not coded, because dead code that reads as a live rule is worse than its
absence.*

*The overnight case this decision cares about is carried by the date being **fixed at open** and
never re-derived, which is implemented and tested. What is **not** carried is continuity across a
close: a cashier closing at 02:00 and the next opening at 02:05 start different business dates.
Whether that second shift should inherit the previous night's date is a **different rule** from
the one written above -- it would key on the last **closed** shift, not an open one -- and it is
**deferred, not overlooked**.*

### D121 — The variance is only valid because close waits for the sync queue. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `handover_variance` (null on a device's first shift, never zero), `variance` and
> `expected_cash` stored at close. **Its precondition is client-enforced only** (D126), and
> competing-close serialisation is still an open review finding —
> [SHIFT_REVIEW_FOLLOWUP.md](SHIFT_REVIEW_FOLLOWUP.md) finding 4.

*Revision 2026-09-05: the original assumed orders are complete at close. The audit established
the POS transacts offline, which was not known when the decision was written.*

```
handoverVariance = openingCount - previous shift's closingCount     (drawer sat closed)
variance         = closingCount - (openingCount
                                   + cash orders COMPLETE
                                   - cash refunds
                                   - expenses on this shift)
```

**These figures are meaningful only because D126 forbids closing while the sync queue holds
orders.** Without that precondition the server sums the orders it has received, orders still in
flight are missing, and the difference is reported as a shortfall that is really latency.

If anyone later relaxes D126, every variance in the system silently becomes noise -- and the two
changes are far enough apart that nobody would connect them. That is why the dependency is written
into this decision and not only into D126.

**The two are different findings and merging them destroys the stronger one.**

`variance` covers the cashier's own shift, where a genuine mistake in change is an ordinary
explanation.

`handoverVariance` covers a window in which **the drawer sat closed** -- no sales, no expenses,
nobody on shift. **A discrepancy there has no innocent explanation**, and it is the single
strongest signal the module produces. Stored in its own column and surfaced on its own, never
folded into the shift's variance.

**A device's first ever shift has no `handoverVariance`** -- there is no prior count. The opening
count establishes the baseline. This case must be handled explicitly rather than defaulted, or it
becomes a null read as a zero.

**Rounding follows the existing rule**: at line level, with header figures as sums of rounded
lines, never independently rounded.

### D122 — Force close: whoever holds the permission closes it — the cashier at the drawer, or a manager from the system. ✅

> **Revised 2026-09-20. The title's "no manager" no longer holds; everything below it does.**
> The original ruled out a manager closing at all. What it actually ruled out was *waiting* for
> one, and the case that forced the revision is the opposite: a cashier standing at the drawer who
> **cannot** close, because they do not hold `SHIFTS_FORCE_CLOSE`. Before this the drawer stayed
> open until somebody who did walked over — the delay the decision exists to prevent.
>
> **The rule is now the permission, not the place.** `SHIFTS_FORCE_CLOSE` decides; the device
> decides only *which* shift when there is one:
>
> | Who | From | Needs |
> |---|---|---|
> | the cashier who opened it | POS | `SHIFTS_CLOSE` |
> | another cashier | POS, **on that same drawer** | `SHIFTS_FORCE_CLOSE` |
> | a manager | the system, **no drawer** | `SHIFTS_FORCE_CLOSE` |
>
> A token carrying a device must still match the shift's device, so nothing about the POS path
> changed. A token without one is scoped by the permission instead — including when it is the
> caller's own shift, since with no device there is nothing else holding them to one till.
> **Open and `current` still require a device:** there is no opening a drawer you are not at.
>
> **Two costs, both accepted deliberately.**
>
> 1. **The record says who *wrote* the count, not who took it.** A manager closing remotely types
>    a figure the cashier read out to them. The original's "the record states who counted it" is
>    weaker here, and no code can close that gap.
> 2. **The blind count is weaker on this path.** A manager typically holds
>    `SHIFTS_VIEW_VARIANCE`, so they can read the expected figure on the shift detail and then
>    enter it, landing the variance on zero. That defeats the third of the original's three
>    guards.
>
> **Why (2) is accepted rather than mitigated:** the manager is expected to see these figures in
> every other context — the shifts list, the detail, the reports are all built for them (D125).
> Withholding the number only at the moment they close would protect nothing they could not read
> a tab away. The blind count was always aimed at the person being measured, and that is the
> cashier, whose path is unchanged.
>
> Remote closes stay identifiable: they are `forcedClose = true` with a `closedByUserId` that
> holds no device, so a report can treat them as their own category.
>
> Pinned by `ShiftServiceTest.closeShift_fromTheSystem*` — allowed with the permission, rejected
> without it, and still rejected on the caller's own shift when there is no drawer.
>
> **Also fixed here:** `DEVICE_IDENTITY_REQUIRED` answered **401**, so admin-web's interceptor
> signed the manager out for pressing the button. The session was never invalid — a browser having
> no device is normal — so it answers **403** now. The POS keys its terminal-session check on the
> error code rather than the status, so a POS session that loses its device binding still ends.

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `forced_close` is derived and pinned by `chk_shift_forced_close` in the database;
> `SHIFTS_FORCE_CLOSE` is seeded in `V58` and held by neither `CASHIER` nor `BRANCH_MANAGER`;
> `ShiftController.java:126-140` gates on either close permission and
> `ShiftService.requireClosePermission` enforces the exact one. The two force-close **ratios** of
> §3 are not built — they belong to D125's missing cashier-performance surface.

A cashier leaves without closing. The next one signs in and finds an open shift.

**The next cashier counts and closes the abandoned shift.** They do not wait for a manager.

**A manager-closes-it design was considered and rejected on accuracy grounds**, not convenience:
by the time a manager arrives the next cashier has been selling, and the drawer holds two people's
money mixed together with no way to separate them. **A late count is not a count.** The person
standing at the drawer is the only one who can count it in the one moment it still contains only
the previous shift's cash. Timeliness outranks the identity of the counter, because a delayed
figure is not evidence of anything.

Recorded as:

- `closedByUserId != openedByUserId` -> **`forcedClose = true`**, permanently on the shift
- The variance is recorded against the **abandoned shift**, and the record states who counted it

**The system does not adjudicate.** A variance from a forced close has two possible causes that
cannot be distinguished from the data: the absent cashier took money, or the present one counted
short and pushed a shortfall onto a colleague. The module's job is to record the figure, the
shift, the counter and the flag -- and let a person decide. Consistent with the standing principle
that the system makes theft visible rather than preventing it.

**Three things make the second cause harder**, and all three are required:

1. **`SHIFTS_FORCE_CLOSE` is a permission distinct from ordinary closing.** Not every cashier
   holds it.
2. **The count is blind here too.** The closer cannot see the expected figure for a colleague's
   shift, so cannot aim at a specific shortfall.
3. **Both patterns are measured**, not just the obvious one: how often a cashier's shifts are
   force-closed by others, **and how often a cashier force-closes other people's shifts**. The
   second column is what catches this specific abuse, and it is the one an implementer is likely
   to omit.

### D123 — Expected figures and variances are never shown to the cashier. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Partly built, and currently violated on one path.** Held: `ShiftResponse` omits the five
> figures by construction (`ShiftResponse.java:12-23`), `V56`'s `chk_shift_close_fields` keeps an
> OPEN row free of close figures, and `ShiftQueryService` nulls variance columns without
> `SHIFTS_VIEW_VARIANCE`. **Violated:** `CASHIER` holds `SHIFTS_VIEW`
> (`V3__role_permission_seed.sql:16-17`), and `GET /api/shifts/{id}` returns
> `salesByPaymentMethod`, `cashSales`, the order list and the expense list to any `SHIFTS_VIEW`
> holder (`ShiftDetailResponse.java:31-46`) — enough to reconstruct the expected figure the count
> is supposed to be blind to. Tracked as finding 1 in
> [SHIFT_REVIEW_FOLLOWUP.md](SHIFT_REVIEW_FOLLOWUP.md); omitting the named variance fields is not
> the same as withholding the figure.
>
> **Fixed 2026-09-20, and the reconstruction is closed.** `ShiftQueryService.findById` no longer
> queries the order or expense rows at all without `SHIFTS_VIEW_VARIANCE` — they are not fetched
> rather than fetched and dropped — and `openingCount`/`closingCount` moved behind the same gate,
> since the opening float is the largest single term of `expectedCash`. Re-run against the same
> data that proved the leak: a caller holding only `SHIFTS_VIEW` now receives 0 order rows and no
> counts, against a stored `expected_cash` of 590.20. The manager path is unchanged.
>
> **The POS keeps no order history to substitute for it.** Settled tickets are deleted from the
> device at shift close (`purgeSettledTickets`), so the local screen can no longer be summed
> either; an older bill is retrieved from the server through the receipt lookup below. Unpaid
> tickets, the per-device order counter and device registration are explicitly excluded from that
> delete.
>
> **The replacement read path is `POST /api/orders/lookup-receipt`**, which needs the order number
> *and* the printed total. The number alone is an enumerable per-device counter; requiring the
> total means a caller has to already hold the receipt, so a match discloses nothing new. A wrong
> total is answered identically to an order that never existed, repeated failures are throttled,
> and the search is scoped to the branch of the caller's signed device.
>
> Pinned by `ShiftQueryServiceTest.findById_withoutVariancePermission_*` (including one asserting
> the projection getters are never called, so no populated field is left for a later edit to
> forget to clear), `OrderServiceTest.lookupByReceipt*`, and `ticketRepo.test.ts`'s purge suite —
> which includes the case that matters most: unsettled work is never deleted.

The cashier sees a keypad. Not the expected amount before counting, and **not the variance after
closing**.

Showing the expected figure turns a count into data entry -- the cashier reads the number and
types it back, and the count stops being evidence of anything.

Variance is visible only under a permission (`SHIFTS_VIEW_VARIANCE` or equivalent), separate from
operating a shift.

**One entry, no edit, no general re-count.** A recount is a new, manager-authorised event; the
original figure survives it. Consistent with D117 and with the ledger's append-only rule (D1/D3):
the number recorded at the moment the money was counted is the only witness to that moment, and
overwriting it destroys the evidence.

**Second acknowledged limit, added 2026-09-20.** A manager closing a shift from the system
(D122 revision) holds `SHIFTS_VIEW_VARIANCE` and can read the expected figure before entering the
count. The blind count therefore does not hold on that path. Accepted because the manager sees
these figures everywhere else by design (D125), and the person the count measures — the cashier —
still counts blind.

**Acknowledged limit, and it must be written down rather than discovered later.** A cashier who
takes 50 and declares 50 short of the expected figure produces a variance of zero. **No system can
detect this from the count alone.** What stands against it is not an approval step but:

- **the blind count** -- with no expected figure, there is nothing to aim at
- **accumulation over time** -- errors made honestly scatter around zero; theft accumulates in one
  direction. A cashier whose shifts land *too* precisely, while colleagues scatter by +/-20, is
  itself the signal
- a **spot count** (O56), currently deferred

Nobody should read these figures as independently verified. They are the cashier's own account,
made under conditions that make a convenient answer hard to construct.

**Audit additions within D120/D122/D123.** `SHIFTS_CLOSE` exists as a seeded permission but no
endpoint enforces it today; all three existing shift endpoints require `SHIFTS_OPEN`, which the
`CASHIER` role holds. Any cashier can currently close any shift in the tenant. The rewrite fixes
that as part of D120's close permission and D122's force-close split, not as a separate feature.
`SHIFTS_FORCE_CLOSE` and `SHIFTS_VIEW_VARIANCE` do not exist and must be seeded. `X-User-Id` is
also whitelisted in `CorsConfig.java:32-33`; removing the header from these paths without
removing it from CORS leaves the door visible. Other controllers still use it, so the CORS
cleanup belongs with the wider migration and its survival here is deliberate.

### D124 — Drawer expenses are recorded by a manager, from the expenses screen, and freeze at close. ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `V57` column, `GET /api/expenses/selectable-shifts` behind `EXPENSES_CREATE`, the
> picker in both admin-web creation surfaces, frozen `expensesAtClose`, and
> `lateExpenses`/`explainedVariance` rendered beside the stored variance rather than folded into
> it. The expense/close ordering race (finding 6) is open.

*Revision 2026-09-05: the original resolved "the currently open shift" server-side. Superseded.*

Money leaving the drawer for a real cost -- a delivery tip, ice, a plumber -- is recorded as an
**expense** (D115-D118), never as a POS action. The cashier is not the person spending it, and
recording it at the till would put the explanation in the hands of the person the variance is
measured against.

Attribution by timestamp was considered and rejected: `expenseDate` is a **`DATE`** with no time
(D118, shipped in `V54`), so on a day with three shifts it cannot identify one -- and if the date
did drive attribution, a manager could erase any shortfall by dating an expense into the shift
that has it. **That would turn the expenses screen into an eraser for variances**, the single most
exploitable path in the design.

**The manager selects the shift.** The list shows, per entry: **cashier name, business date,
open/close times, device, and status**. Filtered to the expense's branch and a recent window (7
days by default, extendable) -- an unbounded list becomes unreadable within months, and an
explicit choice nobody can read is not an explicit choice.

- **Cashier name is read from `openedByUserId`, never stored on the shift.** A denormalised name
  is a second copy that goes stale when a user is renamed -- same reasoning as the balance in
  D119.
- Where the branch has one device and one shift covering the date, it is preselected. The manager
  can still change it.
- **Closed shifts appear in the list and are selectable**, labelled with the consequence, not just
  the state: "Closed -- this will be linked, but its recorded variance will not change." Without
  that, a manager records expense after expense believing they are correcting the figures.

**The freeze rule is unchanged and is separate from attribution:**

| | |
|---|---|
| **Which shift** | the manager's explicit choice |
| **Whether stored figures move** | `createdAt` vs `closedAt` -- recorded before close, it enters `expectedCash`; after close, it does not |

**Late expenses.** A manager records at the end of the day, or the next one. An expense recorded
against an already-closed shift **is stored and linked, and does not change the stored variance**.
It appears in a **separate column** beside it:

```
Variance at close      -300
Late expenses           300   recorded after close
Explained variance        0
```

**The two figures are never merged into one.** Collapsing them lets any shortfall be erased after
the fact by recording an expense for the matching amount -- the easiest exploit available in the
whole system, and it would turn the expenses screen into an eraser for variances. Showing both
keeps the original evidence and makes the explanation itself visible and reviewable.

**The gap between `expenseDate` and `createdAt` is the signal** (D118). Twenty minutes is routine.
Three days, for precisely the amount a shift closed short, is the finding.

**This scopes O51**, which deferred `paidFromShiftId` out of the Expenses pass. The column,
manager-selected shift and frozen snapshot are implemented; the expense/close ordering race
remains an open review finding. Do not treat a frozen field alone as proof of that guarantee.

Shift times and expense classification on shift detail use the branch timezone (2026-09-06
review decision). Existing expense `createdAt` audit values remain tenant-local in storage under
D101. Convert those values to the branch timezone before comparison with `closedAt` and display;
do not change the meaning of new audit rows while leaving historical rows in the old zone.
This fixes timezone mismatch, not transaction ordering. The existing wall-clock DST limitation
(O34) and mutable timezone configuration are not resolved by conversion.

Because the manager now chooses which shift absorbs an expense, `paidFromShiftId` is a sensitive
field. The shift detail screen must list every expense with **who recorded it and when**, not just
a total -- the question in any investigation is "who attached this amount to this shift, and
when".

### D125 — Three surfaces, and every cashier metric is a ratio. ⚠️

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Two of the three surfaces are built.** Shifts list (`GET /api/shifts`, variance-sorted,
> variance columns gated) and shift detail (`GET /api/shifts/{id}`) exist backend and web.
> **Cashier performance does not exist** — no endpoint, no service, no page; the ratios,
> cumulative variance and both force-close columns are unbuilt, and O54 says the cancellation
> ratio would be structurally incomplete anyway. `Branch.varianceTolerance` (O57) is also absent.

**Shifts list** -- branch and date filters. Cashier, device, open/close times, duration, sales by
payment method, opening/expected/counted, both variances, `forcedClose`. **Sorted by variance by
default, not by date** -- the screen exists to bring the anomalous to the top.

**Shift detail (Z)** -- full breakdown, orders, drawer expenses, late expenses, events.

**Cashier performance** -- per user over a period: order count, sales value, **cancellation ratio
by count and by value**, discount ratio, mean variance, cumulative variance, refunds, shifts
force-closed by others, **shifts they force-closed for others**.

**Ratios, not counts, and value-weighted as well as count-weighted.** Ten cancellations out of 500
orders is not eight out of 50. And organised theft appears as a **pattern** -- a cashier 30 short
in 80% of their shifts -- not as a single large incident. **Cumulative variance over 30 days is
the figure that catches it; a single shift's variance rarely is.**

**`Branch.varianceTolerance`** (O57) exists so that small honest differences do not flag. It
suppresses the flag, never the record: the figure is always stored, and the accumulation above is
computed over all of it, tolerated or not.

Built on the reports shell (D84/D86) as read-only queries. **The shifts list is an operational
list, not a report** (D83) and is a different artifact from the two report screens.

### D126 — The offline boundary ✅

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built in the POS.** Close blocks on a non-empty queue in both states, shows the pending count,
> requires connectivity, is the only session exit, and revokes the refresh token with durable
> retry. The server-verifiability limit below stands unchanged and is not a gap to be closed.

The POS completes and cancels orders offline and retries them from a local queue; shift open and
close are not queued. **Selling is fully local** -- the token is needed only when the queue syncs,
which needs the network anyway, so an expired token at sync time is renewed through the refresh
flow (D127) rather than blocking the sale that already happened.

**Close requires an empty sync queue.** Orders still in flight are money already in the drawer
that the server has not seen. Closing without them makes the server sum only what it has received
and report the shortfall as a variance -- which is latency, not loss.

**Both queue states block, not just one.** `PENDING_SYNC` *and* `SYNC_ERROR`. An order that was
paid and then failed to upload has cash in the drawer exactly as one still retrying does; treating
`SYNC_ERROR` as settled would let the largest and most suspicious category through. Note that
`getPendingSyncOrders()` currently returns only `PENDING_SYNC` while the UI counts both -- the
blocking check and the number shown to the cashier must be **the same set**.

The refusal shows **the count of pending orders**, not a generic retry message: a number tells the
cashier whether to wait ten seconds or fetch someone. A **different** message when the failure is
connectivity rather than queue depth.

#### This precondition is enforced by the client, not verified by the server

**Stated plainly because it constrains how far D121's figures can be trusted.**

The queue lives in the device's local SQLite/OPFS. It exposes no watermark, sequence number or
flush acknowledgement to the backend, so **the server cannot distinguish an empty queue from
orders a device has not yet sent.** A `pendingCount` in the close request would only be the caller
asserting its own compliance.

The official POS enforces the rule. **A modified client could close a shift with orders
outstanding, and the resulting variance would be wrong.** In practice that guards against the
realistic threat -- a cashier working around the POS -- and not against a fabricated client, which
is an acceptable trade today.

Making this server-verifiable requires a synchronisation barrier protocol that does not exist and
has not been designed. Recorded as a limit, not a gap to be quietly closed later: **anyone reading
D121 must not assume the order set is server-guaranteed complete at close.**

#### Connectivity

**Close requires connectivity. Open requires connectivity.** Offline close is pointless -- the
next cashier could not open a shift anyway -- so **the shift continues under the same cashier until
the network returns.** Selling continues offline throughout; only the shift boundary is online.

Consequence, stated so it is not mistaken for an oversight: **a branch starting the day with no
connectivity cannot open a shift, and so cannot trade.** Existing behaviour, not a new restriction
(O65).

#### In-progress tickets are not money

An unpaid open ticket has taken no cash. It does not block closing and **carries over to the next
shift** -- the shift is decided **at payment**, consistent with D93 (`COMPLETE` orders only).

**A cashier may therefore take payment on a ticket a colleague opened, and it is attributed to
whoever took it.** Correct -- the customer is at the table and whoever is standing there collects
-- and written down so it is not later read as data leaking between users.

#### Closing signs out, on the server

The sign-out button is removed (D120); closing the shift is the only exit.

**Closing calls the server logout endpoint and revokes the refresh token (D127).** A local token
wipe alone would leave a valid refresh token on the server for its full lifetime, so the shift
would end while the credential did not. That revocation is why the refresh lifetime's configured
maximum is reached only when a shift is never closed -- which is why it is short (7 days).

Queue credentials cannot be discarded while orders are pending. Because close already requires an
empty queue, that state is unreachable -- **but the code must forbid it explicitly rather than
relying on two unrelated rules happening to compose.**

**Recovery clarification, approved 2026-09-06.** Closing ends the visible cashier session
immediately and retains unpaid tickets for the next session. If server logout cannot be reached,
retain the revocation credential in durable logout-pending storage and retry; losing that
credential is not successful revocation. Token expiry triggers shared refresh/retry on foreground
and background requests. Unreachable refresh preserves the session and queued orders; rejected
authentication ends the visible session and stops authenticated uploads.

**Explicit reset exception, approved 2026-09-06.** Device reset checks pending paid orders and
asks the operator to cancel or force reset, showing the count and permanent deletion consequence.
Only that explicit force-reset confirmation authorizes deleting unsent orders. Normal close,
authentication loss, and ordinary reset must not silently discard them.

### D127 — Cashier access tokens carry optional, live-validated device identity; refresh tokens are rotating and revocable. ✅

> **Narrowed 2026-09-20 by D122's revision.** "Device-bound operations reject its absence" still
> holds for opening a shift and for `current`. **Closing no longer does:** a manager with
> `SHIFTS_FORCE_CLOSE` closes from the system with no device at all. Absence of the claim is a web
> session, not a fault — which is also why it answers 403 rather than 401.
>
> Unchanged: a claim that is present must be valid, live and this tenant's. A *bad* device still
> invalidates the session; a *missing* one no longer does on every path.

> **Status verified 2026-09-19** against backend `85d9b7a` + working tree, admin-web `fa426b7`,
> POS `99c6463`.
>
> **Built.** `V55__refresh_tokens.sql`, `auth/refresh/`, and the per-request user/role/device
> revalidation in `JwtAuthenticationFilter`.

`deviceId` is optional in the access-token format because admin-web sessions do not belong to a
physical cashier station. It is not optional for device-bound operations: those operations reject
its absence, and no header, request-body field or branch inference may fill it in. Cashier login
accepts the existing `deviceId` input only after verifying that the stored device belongs to the
user's tenant and is active, then carries that validated identifier in the signed token.

The authentication filter rechecks the user, role and claimed device in one database lookup on
every request. The token establishes identity; revocable state remains live. A missing, inactive
or cross-tenant claimed device invalidates the session. A web manager therefore cannot operate a
physical drawer merely by holding a shifts permission.

Login also issues an opaque, 256-bit refresh token. Only its SHA-256 hash is stored. Refresh
tokens expire after 7 days, rotate under a row lock on every successful use, are revoked on logout,
and are revoked when a user is deactivated or deleted. Refresh re-reads the user's status, role
status and current role code, plus the device state when present; it never copies revocable claims
from an old access token. The access-token lifetime remains 24 hours. Closing a shift signs the
cashier out and revokes the refresh token, so the effective lifetime is the length of the shift;
the configured maximum applies when a shift is never closed, which is the case where a long window
is a liability rather than a convenience.

### D128 — Media attachments: one generic `media_link`, cardinality enforced per purpose, deletion is permanent. ✅

> **Status: decided and built 2026-09-21**, against backend working tree on `85d9b7a` and
> admin-web `fa426b7` + working tree. Migration `V63__media_attachments.sql`; module
> `media/`; resolvers in `menu/product/` and `hr/service/`. Shipped with **two** purposes —
> `PRODUCT_IMAGE` and `EMPLOYEE_PHOTO`. `PURCHASE_INVOICE_ATTACHMENT` is not built; it joins
> `EXPENSE_RECEIPT` in §9 as a cheap addition, which means §6's add-only guard has a code path
> (`MediaOwnerResolver.isMutable`, checked by `MediaService.delete`) but no owner that ever
> returns `false`.
>
> **Five amendments the implementation pass forced. Each refines or reverses a sentence below,
> and the sentence below is left standing so the delta stays visible.**
>
> 1. **A purpose carries two permissions, not one.** §2's table names one. Reads and writes need
>    different ones: a cashier holds `PRODUCTS_VIEW` and must see every product image on the POS
>    grid, so gating `GET /api/media/{id}/{variant}` on `PRODUCTS_UPDATE` blanks the menu for
>    everyone who cannot edit it. `MediaPurpose` therefore carries `viewPermission` and
>    `managePermission`. The confirmed pairs are `PRODUCTS_VIEW`/`PRODUCTS_UPDATE` and
>    `HR_EMPLOYEES_VIEW`/`HR_EMPLOYEES_UPDATE` — the HR family is the `HR_EMPLOYEES_*` one
>    `EmployeeController` actually uses, not the `EMPLOYEES_*` codes V2 also seeds.
> 2. **"No migration" in §4 is wrong.** §1 requires CHECK constraints mirroring the enums, and so
>    does CONVENTIONS. A new owner type or purpose therefore costs a one-line `ALTER` widening
>    `chk_media_link_owner_type` / `chk_media_link_purpose`, plus the §3 index predicate if it is
>    single-valued. Adding an attachable entity is *one enum value, one purpose, one resolver
>    bean, and one trivial migration.* Everything else in §4 stands.
> 3. **WebP needed a dependency.** §2 allows `image/webp` and Java 21's `ImageIO` ships no WebP
>    codec at all — `ImageIO.read` answers `null`, which would have surfaced as
>    `MEDIA_IMAGE_UNREADABLE` on a perfectly valid file. Resolved with
>    `com.twelvemonkeys.imageio:imageio-webp` (pure Java, ServiceLoader-registered, no native
>    library). It is a **reader**: derivatives are written as JPEG, or PNG when the source carries
>    alpha, while `ORIGINAL` keeps its own bytes and content type.
> 4. **The ETag is the checksum *plus the variant*.** §8 names `checksum_sha256` alone. One
>    checksum covers every rendition of a file, so a bare checksum is correct only while
>    conditional requests stay keyed to a URL. Appending the variant costs five characters and
>    removes the reliance.
> 5. **The content type is sniffed, not believed.** The multipart `Content-Type` is a client
>    assertion, and the stored value is echoed back on every read. `ContentTypeSniffer` settles
>    the type from magic bytes, the sniffed value is what gets stored, and reads carry
>    `X-Content-Type-Options: nosniff`. HEIC is recognised deliberately so the refusal can name
>    the format — §10's first open item is answered as **documented refusal**, with strings in
>    both languages telling an iPhone user to switch Camera → Formats to "Most Compatible".
>
> **Two consequences of §8 that only appear once a browser is involved.** Media reads are
> permission-gated, so the request carries an `Authorization` header — and `<img src>` cannot send
> one. Every image in the admin web app is fetched as a blob and rendered from an object URL
> (`MediaImage`, `mediaService.fetchMediaObjectUrl`). This costs nothing in network terms, because
> `immutable` plus a year's `max-age` is honoured by the browser HTTP cache for `fetch` exactly as
> for `<img>`; what it costs is that a raw `<img src={variant.url}>` anywhere in the app is a 401
> rendering as a broken image. Object URLs pin their blobs in memory, so the cache is capped at
> 150 entries and evicts by revoking.
>
> **Still open after this pass.**
> - **EXIF orientation is not applied.** A phone photo whose orientation tag says "rotate 90°" is
>   stored and displayed unrotated, because `ImageIO` does not honour the tag and nothing here
>   reads it. Most visible on portrait employee photos. The fix is to parse the APP1/EXIF
>   orientation and transform before scaling; it was not built because §10's refusal path was the
>   larger risk and this one is cosmetic and reversible.
> - **§5's link→owner reconciliation**, but the discovery it was waiting on now has an answer:
>   **products do have a hard-delete path** (`ProductController.delete` →
>   `ProductService.deleteProduct`), so a deleted product leaves an orphaned `media_link` row and
>   unreclaimed bytes. The sweep is worth writing. Employees deactivate rather than delete.
> - **The orphan sweep itself** (§5, storage listing vs `media_variant.storage_key`) is not
>   built. `StorageService.list` exists for it and has no other caller.

Resolves **O49**. O49 asked for a receipt image on an expense and deliberately refused to build
one, on the grounds that *"the intent is to design document attachment as a general capability
(any document carries an image) rather than bolting a single-purpose upload onto expenses."*
This is that general capability. It ships with three purposes and **not** the expense one — see
§9 — because the Expenses module (D115–D118) is itself `🕓` and an owner that does not exist
cannot be resolved.

There is no file-storage layer in the system today. This decision creates one.

---

#### 1. Three tables: the file, its derivatives, and the link

```
media_file          the bytes' identity. Knows nothing about who owns it.
  id, tenant_id                       TenantAwareEntity
  original_filename                   as uploaded, for display only — never used in a key
  content_type                        image/jpeg | image/png | image/webp | application/pdf
  size_bytes, width, height           width/height NULL for application/pdf
  checksum_sha256                     of the original bytes
  + audit

media_variant       one row per stored rendition, including the original
  id, media_file_id FK
  variant                             ORIGINAL | LARGE | MEDIUM | THUMB
  storage_key       UNIQUE
  content_type, width, height, size_bytes
  UNIQUE (media_file_id, variant)

media_link          the generic ownership edge
  id, tenant_id, media_file_id FK
  owner_type                          enum + CHECK constraint mirroring it
  owner_id          BIGINT
  purpose                             enum + CHECK constraint mirroring it
  sort_order        INT NOT NULL DEFAULT 0
  INDEX (owner_type, owner_id)
```

**`media_link` is polymorphic, and that is a deliberate departure from CONVENTIONS' "prefer a
real child table" instinct.** The alternative considered was one link table per owner
(`product_image`, `purchase_invoice_attachment`, …), each with two real foreign keys. It was
rejected on breadth, not on principle: the requirement is that *any* record becomes attachable
without a schema change, and the per-owner shape charges a migration, an entity, a repository and
a mapper for every new one. At two owners the per-owner tables win. At the open-ended set this
capability is being built for, they do not.

**What the departure costs, stated rather than discovered later:** the database cannot enforce
that `owner_id` names a live row. §3 buys most of that back; the rest is §4's job.

**`media_variant` is a table rather than four nullable columns on `media_file`** because a future
size is a certainty, not a possibility — the online ordering surface will want a rendition the
POS grid does not. When one is added, the backfill needs to know which files already have it.
Nullable columns answer that only by accident; a row does it by construction.

---

#### 2. `MediaPurpose` is the single source of truth for every per-purpose rule

One enum carries, per purpose: the owner type, the required permission, the cardinality, the
allowed content types, the size ceiling, and the derivative set. There is no second place where
any of these live, and no `if (purpose == ...)` outside the enum.

| Purpose | Owner | Permission **(confirm)** | Cardinality | Types | Max | Derivatives |
|---|---|---|---|---|---|---|
| `PRODUCT_IMAGE` | `PRODUCT` | menu-manage | **single** | image | 10 MB | `FULL` |
| `EMPLOYEE_PHOTO` | `EMPLOYEE` | hr-manage | **single** | image | 5 MB | `AVATAR` |
| `PURCHASE_INVOICE_ATTACHMENT` | `PURCHASE_INVOICE` | `INVENTORY_PURCHASE_MANAGE` | **multi** | image + pdf | 15 MB | `DOCUMENT` |

Derivative sets, longest edge, aspect ratio preserved, **never upscaled**:

| Set | Renditions |
|---|---|
| `FULL` | `ORIGINAL`, `LARGE` 1600, `MEDIUM` 800, `THUMB` 200 |
| `AVATAR` | `ORIGINAL`, `MEDIUM` 400, `THUMB` 96 |
| `DOCUMENT` | `ORIGINAL`, `THUMB` 200 — images only; a PDF stores `ORIGINAL` alone |

**Authorization is the owner's, never the media module's.** There is no `MEDIA_UPLOAD`
permission and none is to be added. Attaching to a purchase invoice requires the purchase
invoice's own permission. A generic upload permission would let anyone holding it attach a file
to any record in the system, which is the whole access-control model of the application defeated
by one convenience.

**No server-side cropping.** `AVATAR` fits, it does not crop to square. The UI squares the frame
with `object-fit`. Cropping is destructive and the crop the server guesses is wrong on exactly
the faces it matters for.

---

#### 3. Cardinality is a database constraint, not a service check

```sql
CREATE UNIQUE INDEX uk_media_link_single ON media_link (owner_type, owner_id, purpose)
WHERE purpose IN ('PRODUCT_IMAGE', 'EMPLOYEE_PHOTO');
```

A partial unique index gives single-valued and multi-valued purposes different guarantees in one
table. This is the part of the per-owner-table model that survives the departure in §1, and it is
enforced one layer below anything a service can forget.

Uploading a second file to a single-valued purpose **replaces** — it does not error. The
replacement and the old file's removal (§5) happen in one transaction.

---

#### 4. `MediaOwnerResolver` — the media module knows no owner types

The media module has no compile-time dependency on Menu, HR or Inventory, and no switch on
`owner_type`. Each owning module contributes a bean:

```java
public interface MediaOwnerResolver {
    MediaOwnerType ownerType();
    boolean exists(Long tenantId, Long ownerId);
    boolean isMutable(Long tenantId, Long ownerId);   // §6
}
```

Registered into a map keyed by `ownerType()`. A purpose whose resolver is missing at startup
**fails fast at startup**, not on the first upload — a missing resolver is a wiring defect, and
discovering it when a user uploads a receipt is discovering it in the worst place.

Three things fall out of one interface:

- **`exists` is the tenant check.** It is called with the *request's* tenant, so an `ownerId`
  belonging to another tenant fails as not-found. The same check re-run on the linked
  `media_file`'s `tenant_id` closes the other direction: a client supplying a foreign
  `mediaFileId` cannot attach it to a row it does own.
- **`exists` is also the reconciliation job's predicate** (§5), so the sweep needs no new code
  per owner type.
- **`isMutable` is the deletion guard** (§6), for the same reason.

Adding a new attachable entity is: one `MediaOwnerType` value, one `MediaPurpose` value, one
resolver bean. No migration, no table, no change to the media module.

---

#### 5. Deletion is permanent, and the storage write is ordered to fail safely

**Deleting a file deletes the bytes. There is no recovery, no detached state, no version
history.** Replacing a product image destroys the previous one. This was considered against
keeping superseded files as history and rejected: an unreachable file that no screen lists and no
endpoint returns is not history, it is storage nobody is accounting for, and personal data (an
employee photo, an identity document) retained with no purpose and no path to it is a liability
rather than a feature.

**Two orderings carry the whole correctness story, and they are opposites:**

- **Upload: bytes first, commit second.** The worst outcome is a stored object with no row —
  invisible, harmless, and swept by §5's job. The reverse ordering produces a row pointing at
  nothing, which renders as a broken image in the UI and cannot be swept, because from the
  database's side it looks correct.
- **Delete: commit first, bytes second.** The storage keys are written to
  `media_deletion_queue` in the same transaction as the row deletion, and a job in `job/` removes
  the objects afterwards. Deleting bytes inside the transaction means a rollback destroys a file
  whose row came back.

**`media_deletion_queue(id, tenant_id, storage_key, enqueued_at)`.** It is transactional by being
an ordinary table; that is the entire mechanism, and it needs no more.

**The orphan sweep** compares the storage listing against `media_variant.storage_key` and deletes
what has no row. Objects with no row are unreachable by construction — no user, no query and no
endpoint can name one, or say what it was for. Weekly is often enough; this is the rollback
residue of §5's first ordering, not a routine occurrence.

**Orphaned `media_link` rows** — a link whose owner was hard-deleted — are the reconciliation job's
target, using §4's `exists`. Whether any owner in scope has a hard-delete path at all is a
discovery item for the implementation pass, not an assumption: products and employees are expected
to deactivate rather than delete, and purchase invoices cancel.

---

#### 6. An attachment on an immutable record is add-only

Deletion of a link is permitted only while `isMutable(tenantId, ownerId)` returns true. For a
purchase invoice that is `status != POSTED`; unposting restores the ability to delete, which is
consistent with what unposting means everywhere else.

Adding is always permitted, in every state. The correction path for a wrong attachment on a
posted document is to add the right one beside it, not to erase the wrong one. An attachment is
evidence for a financial record, and the same reasoning that makes `inventory_transaction`
append-only — corrected by a reversing row, never by an edit — makes its evidence non-erasable
once the record it justifies is final.

**Accepted trade-off:** a file attached to the wrong invoice and posted stays there permanently.
It can be superseded, never removed. This is the intended behaviour, not a gap.

---

#### 7. Storage: local disk now, S3-compatible later, and the two rules that make that cheap

Everything goes behind `StorageService` (`put`, `get`, `delete`, `exists`, `list`). V1 implements
it over a local filesystem root supplied by a configuration property. Cloudflare R2 was chosen as
the intended destination — S3-compatible, so the same SDK and the same key layout, and with no
egress charge, which is the cost that dominates when a menu image is read thousands of times and
written once — but it is not built now.

**The provider is cheap to change only because of two rules that are not negotiable from day one:**

1. **No URL is ever stored.** `storage_key` only; every URL is built at read time. A stored
   absolute URL turns a provider change into a data migration across every row.
2. **The key layout is fixed now:** `t{tenantId}/{ownerType}/{uuid}/{variant}.{ext}`. Moving to
   an object store later is then a tree copy. The `uuid` is generated; **the uploaded filename
   never appears in a key.**

**What local disk costs, recorded so it is not discovered in production:**

- The root must live outside the deployed war and survive redeploy. A path inside the servlet
  container is erased by the next deployment.
- **Media is no longer in the database backup.** `pg_dump` stops being a complete backup of the
  system the moment this ships. The filesystem root needs its own backup, and whoever operates
  the server has to be told.
- No presigned URLs are possible, so every read is served by the application (§8). This is
  acceptable while everything is permission-gated and becomes the reason to move once product
  images need to reach a public menu.

---

#### 8. Reads are served by the application, permission-checked, and cacheable

`GET /api/media/{id}/{variant}` resolves the link, resolves its purpose's permission, checks it,
and streams. `ETag` is the `checksum_sha256`; `Cache-Control: private, max-age=31536000,
immutable`; conditional requests answer `304`.

`immutable` is safe because content is never rewritten under an existing key — a replacement is a
new `uuid` and therefore a new key. Cache invalidation is not needed and must not be implemented;
that property is what a CDN will later depend on.

`private` is correct today because every response is permission-gated. When product images move
to a public bucket behind a CDN, that purpose's responses flip to `public` — a per-purpose
property, not a global one. Employee photos and financial attachments never become public.

---

#### 9. What is not built, and why each one is cheap to add

- **`EXPENSE_RECEIPT`.** Expenses (D115–D118) are `🕓`. A purpose whose owner does not exist is
  dormant schema with no producer — the exact shape D114's discovery had to untangle. The
  expenses pass adds one enum value and one resolver. **O49 is resolved by the capability
  existing, not by the purpose shipping**, and the expenses pass owns the purpose.
- **Purchase return, waste, physical count, asset, maintenance attachments.** Same two lines
  each. Added when someone asks.
- **Multiple images per product.** Single, per §2. When the online ordering surface needs a
  gallery, `PRODUCT_IMAGE` becomes multi-valued by removing it from §3's partial index — plus
  whatever ordering and primary-image semantics that surface actually turns out to need, which is
  not knowable now (D13). This is deliberately *not* pre-built.
- **Presigned direct-to-bucket upload.** Every byte passes through the application in V1.
  Correct at this volume, and required anyway while the application is what validates and
  transcodes. Revisit when upload volume, not aesthetics, makes it a problem.
- **Deduplication by checksum.** The column is stored and nothing reads it for dedup. Recorded
  so its presence is not mistaken for an implemented feature.
- **HEIC.** Rejected with a translated error. iPhone uploads are a real and expected source, and
  Java's `ImageIO` cannot decode HEIC without a native plugin — see O-next below.

---

#### 10. Open, recorded rather than guessed

- **O-next-free — HEIC uploads.** Safari usually transcodes to JPEG on a file-input upload, but
  not on every path, and a user who reaches a rejection has no way to comply from a phone. The
  candidates are a server-side decoder (a native dependency) or a documented refusal. Decide
  before a tenant meets it, not after.
- **O-next-free — link→owner reconciliation.** Depends on whether any in-scope owner has a hard
  delete path (§5). If none does, the job is not yet worth writing and this stays open.
- **The multipart ceiling is two limits, not one.** Spring Boot's
  `spring.servlet.multipart.max-file-size` defaults to 1 MB, and any reverse proxy in front of the
  war has its own body limit. A 10 MB purpose ceiling with either default left in place fails at
  1 MB, from a layer that produces no `errorCode` for `translateApiError` to render. Both are
  configuration, not code, and both are the implementation pass's problem to confirm.

### D129 — The POS is the authority on order money. The server records what was charged and derives nothing. ✅

> **Status: decided and built 2026-09-19** against backend working tree on `85d9b7a`,
> POS `99c6463` + working tree.

Resolves **O30**. Reverses the prior invariant that the server computes order money.

#### The defect

`money()` in the POS rounded to the whole currency unit **at print time only**
(`Math.round(n)` in `pos/i18n.ts`). Nothing else rounded. So the payable amount was never a
value anywhere in the system — it was a string, produced independently at each place a figure
appeared, over an unrounded float that stayed unrounded everywhere else.

An order of 110.00 in goods:

```
POS internal float         125.40
printed, and collected     EGP 125          ← the customer pays this
sent to the backend        lines only, no money at all
stored as total_amount     125.40           ← server applied its own 0.14
counted by expectedCash    125.40
```

**0.40 short, on one sale.** With a 14% rate almost no basket lands on a whole unit, so this
was every cash order, not an edge case. D124 derives the drawer balance rather than storing it,
so there is nothing in the arithmetic to absorb the gap: it arrives in full at close, as a
variance in the cashier's name, for money nobody took. The change due was computed off the
unrounded figure and displayed off a separately-rounded one, so it drifted too.

#### The decision

**The POS sends `subtotal`, `taxAmount`, `totalAmount` and each `lineTotal`. The server stores
them verbatim.** `VAT_RATE` is gone from `OrderService`; the backend no longer knows the tax
rate and cannot re-derive any of it.

**Why the client and not the server.** A sale reaches `POST /api/orders` *after* it is over.
The food is gone, the cash is in the drawer, and under D126 the device may have been offline for
hours — the order can arrive long after the customer left. At that point the receipt is the only
record of what changed hands. A server figure that disagrees with it is not a correction; it is
a second, wrong number that the drawer will be measured against.

**Nothing is rejected on money grounds.** This follows from the same fact. A 4xx on a paid sale
strands it in the device's outbox (`syncScheduler` treats a rejection as terminal and stops
retrying), so the cash sits in the drawer with no order behind it and the shift cannot close on
an empty queue (D121) — strictly worse than recording a figure that is a few piastres off. The
server reconciles and **logs**: `Σ lineTotal` against the header subtotal, each line against
`quantity × unitPrice`, and `subtotal + taxAmount = totalAmount` exactly.

**The reconciliation checks are rate-free on purpose**, so they survive a rate change and any
future POS rounding rule. The cost is that a client under-reporting *tax* specifically is not
detectable here — it lowers the drawer expectation along with it. Catching that is cumulative
variance reporting's job (D125), not the write path's. **This is the residual exposure of the
decision and is accepted, not overlooked.**

#### The rounding rule, and where it lives

One function — `orderTotals()` in `pos/money.ts` — feeds the cart, the pay screen, the receipt,
the board cards and the payload. There is no second path to the number.

```
lineTotal = round(unitPrice × quantity)      to MONEY_UNIT
subtotal  = Σ lineTotal                      D121's rule: header is the sum of rounded lines
total     = round(subtotal × (1 + VAT_RATE)) to MONEY_UNIT
tax       = total − subtotal                 the plug, so the three always agree exactly
```

**`MONEY_UNIT = 1`, the whole pound.** Sub-pound coins are effectively out of circulation, so
125.40 is not a collectable bill — a system that demands it has already lost the 0.40 and only
gets to choose who is blamed for it. Charging 125 and recording 125 is the whole fix.

**Tax is the plug, not a computation.** Rounding the total is what makes it collectable;
deriving tax as the remainder is the only way the three figures still reconcile afterwards. The
consequence is that `taxAmount` is not exactly 14% of `subtotal` — it is the VAT on the amount
actually charged, off by under a unit. **`MONEY_UNIT = 0.01` makes it exact again and requires
no other change**, which is the switch to reach for if ETA e-invoicing ever needs 2-decimal
tax lines. That is the only known reason to revisit this.

#### What this costs

Money now arrives from a client. The mitigations are that the device is authenticated (D127),
the figures are reconciled and logged, and D125's cumulative variance is unaffected for
everything except the tax term. Weighed against a guaranteed, universal, silent drawer
shortfall on every cash sale, it is the better trade — but it is a trade, and an implementer
must not "restore" server-side calculation without reading this entry.

### D130 — The POS keeps no order history past a shift close. An older bill is fetched by its receipt. ✅

> **Built 2026-09-20**, backend + POS, pinned by `ticketRepo.test.ts`'s purge suite,
> `OrderServiceTest.lookupByReceipt*` and `apiClient.test.ts`'s `lookupReceipt` cases.

**Reverses the retention rule.** `ITicketStore` previously said settled tickets were "written,
never deleted" and the device kept every sale it had ever taken. It now deletes `PAID` and
`CANCELLED` rows at shift close, and a cashier who needs an older bill asks the server for that
one order.

**Why, and it is not storage.** A device holding its own sales is a device that can be read to
reconstruct a drawer. D123 withholds the expected figure from the cashier, but the local history
screen listed every order of the shift with its total — so the count the cashier was about to
make blind could be computed by scrolling. Server-side gating cannot reach that, and no backend
change ever will: the device took the sales, so it has them. **The only way the blind count is
real is if the device does not keep them.**

**What is deleted, and what must never be.**

| | |
|---|---|
| Deleted at close | `ticket` rows with `local_status` in (`PAID`, `CANCELLED`) |
| **Never deleted** | unpaid work — `HELD`, `SENT_TO_KITCHEN`, `IN_PROGRESS`, `READY` — which carries over to the next shift (D126) |
| **Never reset** | `order_counter`; receipts are looked up by number **and** total, so a restarted counter puts two different sales behind one key |
| **Never cleared** | `device_auth`; the device stays registered across closes |

The purge matches an explicit allow-list of what may go rather than excluding what may not, so a
status added later is **kept** by default. That direction is deliberate: the failure of keeping a
row too long is a tidiness problem, and the failure of deleting one too early is a table's order
disappearing mid-service.

**Ordering.** The delete runs only after the server has accepted the close. D126 already requires
an empty sync queue to close, so no unsent sale can be destroyed — but the ordering is stated and
coded separately rather than resting on two unrelated rules happening to compose, because what is
being deleted is the record of money that changed hands. A purge that fails after a successful
close is logged and does not report the close as failed: the shift *is* closed on the server.

**The replacement read path: `POST /api/orders/lookup-receipt`, keyed on order number *and*
printed total.** Both appear on the receipt. The number alone is a per-device counter — 1, 2,
3 — so a lookup keyed on it would hand back the per-order amounts that D123 exists to withhold,
just through a different door. Requiring the total inverts it: the caller asserts what they are
already holding, so a match discloses nothing new, and a caller without the receipt has to guess
the one number being protected.

- **The response is the full order**, lines and prices included, and that is consistent rather
  than a leak — redacting a receipt from the person holding it protects nothing. The match is the
  gate, not the payload. It also makes a reprint possible without any local data.
- **A wrong total fails exactly like an order that never existed** (`404 ORDER_NOT_FOUND`), so the
  endpoint is not an oracle for which order numbers are real.
- **Failures are throttled per device** — 5 per minute. The proof-of-possession argument only
  holds while amounts cannot be swept in bulk, so the cap is part of the control, not a nicety.
  In memory, therefore per instance: several nodes multiply the budget, which is acceptable
  against a cashier at a till and is recorded rather than left to be discovered.
- **Scoped to the branch of the caller's signed device**, not the device. The customer returns to
  the restaurant they bought from, not the till they paid at; a two-machine branch would
  otherwise turn them away at the wrong counter. The branch is read from the token, never from
  the request, so the client cannot widen it.
- **Gated on `ORDERS_CREATE`, not `ORDERS_VIEW`.** The latter carries the filterable order list,
  which is the browsing this replaces — and which the cashier role has never held, so the POS's
  old "past orders" tab answered `403` for every cashier who pressed it.

**Kept as its own route rather than a filter on the order list**, for the same reason the list is
not used: a filter can be relaxed one parameter at a time until it is a list again, while a route
taking exactly two values and returning exactly one order cannot drift into one.

**Accepted cost, stated so it is not discovered at a counter:** a receipt whose sale was taken on
a device in another branch, or before a device was reset, is not reachable by the cashier. That
goes to a manager through the admin system. The alternative — widening the scope to the tenant —
buys a rare case at the cost of letting any cashier probe any branch's sales.

### D131 — A relaunch resumes the session from disk. The server is asked afterwards, never before. ✅

> **Built 2026-09-20**, backend + POS. Backend pinned by the login-replaces-session behaviour
> below; the POS restore is a state-initialiser change with no new store to test.

**The bug this closes.** Refreshing the tab logged the cashier out. It was never a logout — the
access token, the refresh token and the device registration all survive in `localStorage`. What
did not survive was React state, which starts at `loggedIn: false`, so the app rendered a login
screen while holding valid credentials for an open shift.

**Why a browser annoyance is a till outage.** In a packaged desktop or tablet build the same code
path runs every time the OS evicts a backgrounded app, an update restarts it, or the power cuts —
daily events on a tablet, not accidents. Three consequences, in increasing order of severity:

1. A password prompt at the counter during service. The practical workaround a branch adopts is a
   short shared password, which is how D127 stops meaning anything.
2. **A relaunch during an outage stopped the branch trading.** Selling is fully local (D126) — the
   menu is cached, orders go to the outbox — but *login* needs the network. So with the shift open
   on the server, the device registered, and the menu on disk, the cashier sat at a login screen
   they could not pass. The offline design failed at the only moment it existed for.
3. **Every relaunch minted another 7-day credential and left the last one live.** Measured on real
   data before the fix: one user with **17 active refresh tokens**, another with 9 on one device.
   Closing a shift revokes *one* token, so D127's "the effective lifetime is the length of the
   shift" was quietly untrue after a device had been restarted a few times.

**The restore runs in the state initialiser, not an effect.** `localStorage` is synchronous, so
reading it during the first render avoids painting a login screen and replacing it a frame later.

**The server is still authoritative, and is asked immediately afterwards** — `GET
/api/shifts/current` reconciles: no shift returns the cashier to the count, a colleague's shift
routes to force-close, their own fills in `currentShiftId`. A failure is ignored on purpose; the
session stands and the answer arrives on a later pass.

**`currentShiftId` stays null in the meantime, and nothing about selling needs it.** The ticket
row and the outbox row both take a nullable shift id and the sync scheduler ignores the column.
The one operation that needs it is closing, which requires connectivity anyway (D126) — so it
cannot be reached while the id is unknown. That is what lets the restore skip the network without
skipping the server.

**No local copy of the shift is kept, and that is deliberate.** POS migration 17 deleted the old
`shift` table precisely because it was written on every open and never read back, and a cache
nobody reads cannot be trusted by whoever finally does. Resurrecting it was the first approach
here and was abandoned on reading that reasoning: the restore needed the *session*, not the
shift, and the shift comes from the server as it already did.

**Login now replaces the session on that station** (`revokeExistingFor`, called before the new
token is issued). Scoped to user + device, null-for-web included, so signing in at a drawer does
not end the same person's admin-web session, and two drawers can hold a session each. Verified:
four consecutive logins on one device leave exactly one live token, the replaced token is
rejected on refresh, and an unrelated web session is untouched.

### D132 — Kitchen time rides on the paid order, as a duration the POS sums itself. ✅

> **Revised 2026-09-20, one day old (`V60`).** The original stored two instants,
> `sentToKitchenAt` and `readyAt`, on the assumption that one order is one firing cycle. That
> holds for takeaway and **breaks for a dine-in table, which pays once for several tickets**:
> there is no single send/ready pair, so two columns could not represent the thing being measured.
> Replaced by `kitchenTimeSeconds` — totalled on the device — plus `orderStartedAt`.
>
> **This retires the original's own argument against a stored duration.** That argument was that a
> duration duplicates what the instants already carry; with N tickets behind one order there are
> no instants for it to duplicate, and the POS is the only place holding each ticket's kitchen in
> and out. Same reasoning as D129: the POS is the authority on what it observed, and the server
> records it verbatim.
>
> **Two things improved by accident.** The duration is a difference of epoch milliseconds taken on
> one device, so a wrong clock and a DST repeat both cancel out — the original had to inherit
> O34's DST limitation and this does not. And `orderStartedAt` with `orderDate` gives **table
> occupancy**, which is a genuinely different number: two tickets cooking at once are counted
> twice by the sum and once by the span. Verified live — a table that sat 90 minutes while the
> kitchen worked 25.
>
> **`V60` is a new file, not an edit to `V59`.** `V59` was already applied, so editing it would
> change a recorded checksum and — with `validate-on-migrate: false` — be silently skipped on
> every database that had run it. That is O66's trap exactly.
>
> The paragraphs below describe the superseded two-instant shape. Read them for the reasoning
> that still holds — null is never zero, nothing is validated, written before its reader — and not
> for the column names.

> **Built 2026-09-20** (`V59`), backend + POS, pinned by `OrderServiceTest.createCompletedOrder*KitchenTimings*`
> and `buildOrderRequest.test.ts`'s timing suite.

**The data existed and was being thrown away.** The POS records four instants per ticket —
created, sent to kitchen, ready, paid — and none of them ever left the device. D130 made that
sharply worse rather than better: settled tickets are now deleted at shift close, so every close
destroyed a day of kitchen timing that cannot be reconstructed.

**Two columns on `orders`, not three.** `sentToKitchenAt` and `readyAt`. The completion instant is
already `orderDate`, which the POS generates at payment, so:

```
cook time  = readyAt   - sentToKitchenAt
total time = orderDate - sentToKitchenAt
```

A third column, or a stored `cookTimeMinutes`, would be a second copy of a figure these already
carry — the drift D119 refuses for the drawer balance, with O27 as the live example of what it
costs.

**Total time is measured from the kitchen send, not from when the ticket opened.** A dine-in
ticket sits open while a table makes up its mind; counting from creation would measure the
customer's deliberation and report it as the restaurant's speed.

**Sent on the order that already exists, not through an event trail.** O54's `PosShiftEvent` is
designed for a different problem — the invisible actions *before* payment — and building it to
carry two timestamps that the paid order can hold itself would be the heavier answer to the
lighter question. The POS posts once, at payment, and already holds both values at that moment.

**Wall clock, like every other timestamp here (D101), and durations are what may be trusted.**
Both instants come off the same device clock, so their difference is right even when that clock
is wrong: a skewed device misreports *when* something happened, never *how long* it took. The one
exception is a DST repeat, where an hour occurs twice and a duration spanning it lands an hour
out — the existing limitation in O34, inherited here rather than newly introduced.

**Null is a real answer and must never be read as zero.** A takeaway paid without reaching the
kitchen has neither value; a ticket cancelled before it was marked ready has only the first. A
report has to exclude those rows, because a zero would render as an instant kitchen and quietly
improve the average. Both the POS payload and the column are nullable, and nothing substitutes
`now` for a missing measurement.

**Nothing is validated and nothing is rejected.** Ready is not checked to follow send, nor send to
precede payment. The sale is already paid by the time it arrives (D129), so refusing it over a
timing oddity would lose money that changed hands in order to protect a statistic. A device whose
clock moved mid-ticket produces a nonsensical pair; that is a row for a report to drop, not a
reason to lose the order.

**Written before its reader exists, deliberately.** That is normally the dormant-schema mistake
D13 warns about and D114's discovery pass had to untangle. The distinction: dormant schema is a
*guess at a future shape*, while this shape is already known and already measured, and the cost of
waiting is not a later migration but data that no migration can recover. The report itself is a
follow-up.

**Follows the state merge.** `SENT_TO_KITCHEN` and `IN_PROGRESS` map to the same backend
cancellation stage and are both pressed by the same cashier, so merging them costs nothing — and
notably costs nothing *here*, because these two instants live in their own columns and survive it.

### D133 — A dine-in table orders in rounds and pays once. The rounds are tickets; the bill is the table's. ✅

> **Built 2026-09-20** (POS only — no backend change), pinned by `tableManagement.test.ts`'s
> rounds suite and `buildOrderRequest.test.ts`'s `kitchenTimingFor` cases.

**The bug this closes, which was not a reporting problem.** A ticket cannot be edited once it is
`READY` (`editOrder` returns early), and an `OCCUPIED` table offered no "start order" action. So
the moment the food came out, **the table could not order anything else until it paid** — no
dessert, no second round of drinks. The only escape was to close the bill early and open a new
one, which split a single table across several sales and corrupted every per-order figure that
followed.

**A table may now hold any number of open tickets, and settles them as one sale.**

| | |
|---|---|
| **Ticket** | one firing cycle — sent to the kitchen once, marked ready once |
| **Table** | the bill: every open ticket on it, paid together |

**The ticket list is derived, never stored.** Each ticket already carries its `tableId`, so
`ticketsOnTable` filters the orders rather than keeping a list on the occupancy row — the second
copy D119 refuses for the drawer balance, and it means rounds need no schema change at all.
`activeTicketId` stays on the row and still means exactly one ticket: it is what move and merge
act on, and those operate on a single round.

**Lines are concatenated, not merged by product.** Two rounds of the same dish are two things
that happened at two times; folding them into one line of quantity two would erase that the
second was ordered later — and with it the reason the kitchen was asked twice.

**One bill, because the backend cannot express anything else.** `Order.paymentMethod` is a single
enum (D25) and split payment is the open question in O63, so a table paying in parts has nowhere
to go. That constraint and the operational answer happen to agree here, which is worth stating:
if O63 is ever built, this decision is where a split would land, and the rounds are already the
natural unit to split along.

**Payment settles every round at once.** The sale that posts contains all their lines, so leaving
the others open would show work on the board that has already been paid for. The occupancy row is
folded over every settled id, which also releases a merged secondary table.

**This is what made D132's duration necessary.** With several tickets behind one order there is no
single send/ready pair to store, so kitchen time has to be summed on the device — the two
decisions were built together and neither stands alone.

### D134 — A dish cooked and then taken off the order is waste. Same consumption machine, different ledger movement. ✅

> **Built 2026-09-20** (`V61`, `V62`), backend + POS. Implements D20, which had been decided and
> never carried out — PROJECT.md records that the code performed no waste mapping for any
> cancellation.

**What was broken.** Order status is final-only, consumption runs on `COMPLETE`, and a `CANCELLED`
order consumes nothing. So a grilled chicken the customer sent back was never deducted: the
balance still counted it, and the loss appeared in no report. There was also no way to *express*
it — editing a ticket is refused once it is `READY`, so the POS could not take a cooked dish off
an order at all.

**The wasted dish stays on the order, priced, and out of the total.**

| | |
|---|---|
| On the line | `line_type = WASTE`, `waste_stage` (D20's two cooked stages only) |
| Money | its real menu price, frozen at the time |
| Totals | excluded — the customer did not pay for it |

**Why it keeps its price.** Two different figures are wanted from waste and only one already
existed: what it **cost** is the materials, which the ledger values at FIFO/average (D1, D11); what
it was **worth** is the revenue that never arrived, and nothing else records that. Menu prices
move, so it has to be frozen on the line rather than looked up later. Zeroing the line was
considered — it would have kept D129's reconciliation a plain sum of every line — and rejected,
because it destroys the figure the feature exists to produce.

**Exactly two readers had to change, and that is the whole blast radius:** D129's reconciliation,
which now sums `SALE` lines only, and the sales-by-product report, the one query that reads
`order_line` directly. Everything else reaches lines through the order, where showing a binned
dish is the point.

**Not a waste document — a consumption document with a type.** `WasteService` runs
DRAFT → COMPLETE → POSTED and needs a human at each step; POS-originated waste has nobody behind
it, so the document would sit in DRAFT forever, and auto-completing it would defeat the review the
lifecycle exists for. `OrderConsumption` is already automatic, batched, order-linked, and handles
shortfalls as states rather than failures (D94). So waste rides it:

```
sale lines  → ORDINARY doc → CONSUMPTION_SUMMARY
waste lines → WASTE doc    → WASTE
              same lifecycle · same scheduler · same PARTIAL/CONFLICT
```

`findOrCreatePendingDoc` gained the type, and the pending-per-warehouse unique index became
`(tenant, warehouse, type)` so both can accumulate side by side. **The docs are resolved lazily**,
so an order with no waste never opens an empty waste doc for the poll to keep picking up.

**The doc is not bound to a shift, deliberately.** It stays keyed by warehouse and closes on the
batching thresholds. Those are a ledger-efficiency concern, not a reporting one — the rows carry
their order and their timestamps, so a report groups by shift from the data. Bending the batching
to suit one report's shape would have to be bent again for the next.

**Only cooked stages are waste, enforced in the service and in the database.** Anything cancelled
before the kitchen started consumed nothing, so a line for it would deduct stock that was never
used.

**Nothing is validated against the money and nothing is rejected.** The sale is already paid when
it arrives (D129), so a timing or pricing oddity is a row for a report to drop, never a reason to
lose the order.

#### Also fixed here: consumption docs could be abandoned forever

`claimDoc` commits `IN_PROGRESS` before processing, and the poll only ever selects `PENDING`. An
instance dying between the two left the doc unreachable — its stock never left the ledger, silently
and permanently. The poll now returns docs stuck in `IN_PROGRESS` past the lock window to
`PENDING`.

Its cutoff subtracts the supported offset spread instead of adding it, the opposite slack to the
age arm: `updatedAt` is tenant-local (D101), so a tenant running ahead would otherwise look stuck
early, and **reclaiming a doc that is genuinely mid-process is the one outcome worth being late to
avoid.** The release re-checks the cutoff under the row lock, so a doc that resumed meanwhile is
left alone.

Worth stating plainly: ShedLock is not what makes any of this correct. The claim is a locked read
plus a committed status flip, so a second instance finds the doc no longer `PENDING` and stops.
ShedLock only saves the wasted race.

### D135 — A user sees one branch or every branch. The server decides which, and it never asks the client. ✅

> **Decided 2026-09-21.** Resolves the core of O62. Zero migration: the two columns this rests on
> already exist and are already validated on write.

> **Correction, same day.** This entry first said "nothing reads them". That is **wrong**, and the
> correction is the useful part: the mechanism exists *and is in production use* —
> `CurrentUserScopeProvider` (`auth/service/`) already implements this decision almost exactly,
> gating on `Role.branchScoped`, returning an empty `Optional` for an unscoped caller, and
> throwing 403 from `ensureCanAccessBranch`. **It is wired into HR and nowhere else** — Employee,
> LeaveRequest, LeaveBalance and SalaryAdjustment filter by branch today; the other thirteen
> branch-owning entities do not. So this is not a new mechanism to design. It is **one module's
> proven pattern generalised to the platform**, which is a smaller and much safer job than the
> original framing implied.

**What is already true, and is the reason this is small.** `User.branch_id` is a nullable column
([`user/entity/User.java:59`](../src/main/java/com/smart/restaurant_saas/user/entity/User.java)),
`Role.is_branch_scoped` is a non-null boolean from `V14__rbac_role_scoping.sql`
([`rbac/entity/Role.java:54`](../src/main/java/com/smart/restaurant_saas/rbac/entity/Role.java)),
and `validateRoleBranch` already enforces the invariant in both directions: a branch-scoped role
**must** carry a branch, and a non-scoped role **must not**
([`user/service/TenantUserService.java:216`](../src/main/java/com/smart/restaurant_saas/user/service/TenantUserService.java)).
So the data is correct today. It is written, validated, echoed in `UserResponse` — and read by
zero query paths.

**The rule.**

| Role | `branch_id` | Sees |
|---|---|---|
| branch-scoped | non-null, enforced | that branch only |
| not branch-scoped (OWNER, and any other role marked so) | null, enforced | every branch in the tenant |

**The gate is `Role.branchScoped`, not `isOwner()`.** The owner sees everything because the owner
role is not branch-scoped, not because of a name check. This is the existing database invariant
rather than a second, narrower one laid on top of it, and it means a tenant can mark an accountant
or an area manager as unscoped without a code change. `isOwner()` stays what it is — a role gate
for owner-only *actions* (D36's model), never a data-visibility gate.

**One branch or all — no subsets.** A `user_branch` junction table was considered and rejected for
now: no confirmed need, and O62's "partial access" was a possibility, not a requirement. The
upgrade is purely additive whenever it becomes real — a junction table with `User.branch_id`
demoted to a default, and the filter's `=` becoming `IN`. Deciding it now would be guessing at a
shape nothing calls for.

**Scope is the branch-owning entities only.** Fourteen entities carry `branch_id`; everything else
is tenant-wide and stays that way. Materials, UOMs, the material catalog, suppliers, the menu and
customers are **not** branch data, and filtering them would be inventing a boundary the business
does not have.

Two joins rather than a column, and both are deliberate:

- **Inventory** reaches branch through `Warehouse.branch_id`. There is no branch on a stock
  balance or a document, and there should not be.
- **Shifts** reach branch through the device. `Shift` carries no branch column on purpose — the
  comment at [`pos/shift/Shift.java:27`](../src/main/java/com/smart/restaurant_saas/pos/shift/Shift.java)
  records why: a second copy goes stale, and it is exactly what let an order attach to a shift in
  another branch before the rewrite.

**The dangerous half is the default, not the filter.** Fourteen controllers take `branchId` as an
optional client-supplied `@RequestParam` today, and an omitted parameter currently means
**all branches**. Under this decision an omitted parameter means **the caller's branch** for a
scoped user, and only keeps meaning "all" for an unscoped one. Every one of those endpoints
changes behaviour for scoped users — that is the point, and it is why this cannot be rolled out
endpoint-by-endpoint without a list.

**An explicit foreign branch id is a 403, not an empty list.** Silently returning nothing teaches
a caller that the branch is empty; refusing teaches it that the branch is not theirs. It also
makes the guard visible in tests, which an empty result does not.

**The scope is resolved server-side from the authenticated principal, never from a header or a
parameter.** `CurrentUserPrincipal` does not carry `branchId` today and must, so a service can
reach it without a per-request user lookup. Whether it rides the JWT as a claim or is read live
follows D36's existing reasoning about permissions — a branch reassignment must not stay effective
until the token expires, so live wins unless measurement says otherwise.

> **Same root cause as O29, and they should be read together.** Both are the system trusting the
> client for identity it already holds: O29 takes the actor from `X-User-Id`, this takes the branch
> from a query parameter. A fix to either that does not source from the principal is not a fix.
> Shift-review finding 12 (filter options requiring unrelated grants) is a symptom of this same
> gap and is expected to fall out of it.

**Not covered here:** which branch a *write* lands on. This decision is about reads. Order
creation already derives branch from the signed device's open shift (D122) and must not start
reading `User.branch_id` instead.

#### Build note — 2026-09-21, backend, no migration

Fifteen list endpoints across thirteen modules now resolve their branch through
`CurrentUserScopeProvider`, joining the four HR paths that already did. Suite: **906 green**, up
from 892.

**The branch costs no extra query.** `findAccountForAuthentication` already joined `Role` for the
live role check, so `r.branchScoped` and `u.branchId` were columns on rows it was fetching anyway.
They ride `AuthenticatedAccount` onto the principal in `JwtAuthenticationFilter.authenticate`,
beside the live role code and for the same reason. `CurrentUserScopeProvider` consequently lost
its `UserRepository` and `RoleRepository`: it used to spend two to four reads per call resolving
what the filter had already fetched.

**A null branch on a scoped role denies.** Reading it as "unrestricted" would turn a broken row
into full visibility, so `requireOwnBranch` throws. The same instinct drove the fail-closed
defaults on the two short `CurrentUserPrincipal` constructors: a principal built without branch
information is *scoped with no branch*, which denies everything, rather than unscoped, which
would grant everything.

**Three entities allow a null branch** — `Expense`, `Warehouse`, `IncomingOrderRequest` — and such
a row is tenant-level data. A scoped caller's list excludes it for free, since `branch_id = :own`
cannot match a null; `ensureCanAccessUnbranched` covers the case where they ask for it by name,
which would otherwise return an empty list and imply the tenant has none.

**One defect found by the suite and worth recording.** The expense guard was first placed in
`loadProjection`, the shared single-row loader. Create and void reload through it to build their
response, so a scoped caller creating an expense got a committed write and a 403 body. The guard
belongs on the read entry point, not on a loader the write path shares — a shape worth checking
wherever this pattern is applied next.

> **Verified by reversion, not only by green.** Every pre-existing test runs as an unscoped
> caller, so 892 passing proved nothing was broken and nothing about the filter working.
> `CurrentUserScopeProviderTest` plus two `TableServiceTest` cases cover the scoped paths, and
> neutering `resolveBranchFilter` to return its argument makes 5 of them fail while the unscoped
> and sys-admin cases correctly keep passing. The guard is load-bearing.

#### Build note — writes, same day

Reads alone were the worse state, not a safe half: a scoped user could create an expense against
another branch and then not see it, so the two halves disagreed about the same record. The rule
below extends this decision rather than opening a new one.

**A write may only land on a branch the caller can read.** Guarded on create for expense, table,
table section, warehouse, device, asset and order intake; on void for expense; on deactivate for
device; and on the single-record loaders for warehouse and managed users.

**Order creation is deliberately untouched**, per this decision's own exclusion: its branch comes
from the signed device's open shift (D122), not from the caller, and pointing it at
`User.branch_id` would replace a stronger source with a weaker one.

**User management is the write that matters most.** Without it a branch manager mints a user with
no branch, logs in as them, and sees everything — the scope mechanism defeated through the screen
that defines it. Creating or moving a user is now guarded on both sides, and because an unscoped
user carries a null branch, a scoped caller cannot create one at all.

**A move has two branches, and checking one is not checking the move.** `ensureCanMoveBetweenBranches`
exists to name that: the update is addressed by id, so guarding only the target lets a scoped
caller pull a record they cannot see *into* their own branch, and guarding only the source lets
them push one out. Reverting it to check the target alone fails exactly the one test written for
it, which is the evidence the helper is not ceremony.

#### Build note — switched on, `V65`

`BRANCH_MANAGER` and `CASHIER` are now `is_branch_scoped = true`. The migration is separate from
the code on purpose: the mechanism is reviewable without changing anyone's visibility, and
visibility changes without touching the mechanism.

**It repairs a contradiction rather than creating one.** Both roles already had users carrying a
`branch_id`, which `validateRoleBranch` forbids for a role it believes is unscoped. The flag was
wrong, not the data — and checked before flipping: across the dev and QA databases, 2
branch managers and 6 cashiers, none with a null branch.

**The migration refuses rather than locking anyone out.** A scoped role with no branch denies every
check, so flipping the flag on a user with a null `branch_id` is a silent, total lockout on their
next request. `V65` raises instead, naming the count — a failed deploy is visible, a cashier locked
out at opening time is not. Same guard shape `V14` used before enforcing `users.role_id NOT NULL`.

**`OWNER` and `SYS_ADMIN` stay unscoped** — the owner sees every branch precisely by not being
confined to one. `ACCOUNTANT`, `HR_MANAGER` and `INVENTORY_MANAGER` are left alone because whether
they are per-branch or tenant-wide is a per-tenant judgement nobody has made; the same statement
flips them whenever it is.

> **Proven over the real filter, not a hand-installed principal.** The unit tests show the provider
> decides correctly *when told* the caller is scoped; they cannot show it is told, which runs
> through the role flag, the authentication query's projection and the filter.
> `BranchScopeIntegrationTest` seeds a real cashier, mints a real token, and asserts the narrowing,
> the 403 on another branch, the 403 on unbranched rows, an unscoped control, and that moving the
> user takes effect on the same token. Its last case flips the role back to unscoped inside the
> test transaction and watches the narrowing disappear — so the suite fails if `V65` is reverted.
> Suite: **916**.

> **Still not enforced by the database.** `validateRoleBranch` holds the "scoped role ⇒ has a
> branch" rule on the API path only; a direct SQL insert, a seed or a future migration can still
> create a scoped user with no branch, who is then locked out with no error explaining why. A
> trigger is the only way to close that, and was not built here.

> **Suite: 910.** Two intermittent failures were observed across the pass — one
> `LiveAccountStateIntegrationTest` 403 and one run with nine errors — neither reproducible in six
> consecutive clean runs afterwards, and neither traced to this change. They match O46's recorded
> hazard exactly: hardcoded ids in a shared database with `ON CONFLICT (id) DO NOTHING` seeds.
> Recorded rather than dismissed, because a suite that is green six times out of eight is not a
> suite anyone should read as a guarantee.

### D136 — HR is a grantable permission (`HR_MANAGE`), not a role gate. ✅

> **Decided 2026-09-22.** Migration `V64__hr_manage_permission.sql`.

Leave requests, leave balances, salaries and salary adjustments were gated on
`@securityService.isOwnerOrBranchManager()` at **class level** across four controllers, and
leave-type writes on `isOwner()` — 19 endpoints in total. A role gate cannot be delegated: an
`HR_MANAGER` could not be given HR and a trusted accountant could not be given payroll, whatever
the sysadmin panel displayed. All 19 now gate on `isSysAdmin() or hasPermission('HR_MANAGE')`.

**One code, not a fine-grained set.** A restaurant does not staff an HR department; it has one
person who does all of it. Splitting view from manage, or leave from payroll, would model an org
chart these tenants do not have.

> Consequence: `SecurityService.isOwner()` and `isOwnerOrBranchManager()` now have **zero**
> production call sites. They are left in place — the manifest generator still recognises them and
> a future gate may want them — but a reader should not assume they are live.

**The migration backfills existing users, and must.** D36 makes `user_permissions` a snapshot taken
at user creation, so seeding `role_permissions` alone does nothing for anyone who already exists.
Without the backfill, shipping the controller change silently removes HR from every current owner
and branch manager. The backfill grants `HR_MANAGE` to every user whose role is `OWNER` or
`BRANCH_MANAGER`, and is idempotent via `uk_user_permissions_tenant_user_permission`.

> `LiveAccountStateIntegrationTest.roleHelpersReadTheDatabaseNotTheClaim` probed
> `GET /api/hr/leave-requests` precisely because it was purely role-gated. It now probes
> `GET /sys-admin/rbac/roles`, since `isSysAdmin()` is the only role rule still gating an endpoint.

## Pointer edits into existing decisions

Per the doc's own rule — a decision keeps its number and text, and a pointer is added under its
heading:

### O30 — `subtotal + taxAmount ≠ totalAmount`. ✅ RESOLVED

Resolved by **D129**, via the option O30 called "the honest fix" — round the components at write
time so the three agree. They are rounded by the POS rather than by the server, because O30 was
looking at the wrong gap: the fractions that failed to add up in the reports were the visible
edge of a much larger one between the printed receipt and the stored order. The identity now
holds exactly on every row, so reports reconciling on `total_amount` (unchanged) agree with
`subtotal + taxAmount` column-wise as well.

O30 also asked that tax-compliance implications be checked before choosing. They are addressed
in D129 under "Tax is the plug, not a computation" — including the single-constant switch back
to 2-decimal precision if e-invoicing demands it.

### O49 — Receipt/document image on an expense. ✅ RESOLVED

Resolved by **D128**. The expense-specific purpose is still not built; D128 resolves O49 by
deciding the generic media attachment capability and leaves `EXPENSE_RECEIPT` to the expenses
implementation pass.

#### O49, as it was raised

Not built. There is no file-storage layer in the system today, and the intent is to design
document attachment as a general capability (any document carries an image) rather than bolting a
single-purpose upload onto expenses. Revisit when that design starts; the addition is a nullable
reference and does not disturb anything decided here.

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
> Expense half resolved by D115-D118. O16 stays open for the P&L report, COGS timing, and
> Fixed-Assets exclusion.

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

### O27 — Document numbering: a single sustainable scheme across all document types. ✅

**Resolved by D112, which is built.** Its raised text is preserved verbatim under D112 as *O27, as
it was raised* — moved, not deleted, so the question still reads in its original words next to the
answer.

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

### O30 — `subtotal + taxAmount ≠ totalAmount`: components at scale 6, total rounded to 2. ✅ RESOLVED

> **Resolved by D129** (2026-09-19) with option (a), rounded in the POS rather than the server.
> O30 was the visible edge of a much larger gap — between the receipt and the stored order — and
> is recorded below as it was raised.

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

Accepted given the narrowness.

**Remedy if it becomes real.** The netting window's two bounds sit on different columns — the
lower bound on `inventory_transaction.created_at` (D93), the upper bound on
`inventory_transaction.movement_date` compared against `physical_count_line.counted_at` (D90).
Converting one side alone breaks the comparison permanently, not just inside the ambiguous
hour: a `TIMESTAMPTZ` value compared against a wall-clock value is meaningless every day of the
year, not once. The remedy unit is therefore the inventory ledger's timestamp set, not a
three-column patch:

* `inventory_transaction.created_at`
* `inventory_transaction.movement_date`
* `stock_batch.movement_date`
* `physical_count.frozen_at`
* `physical_count_line.counted_at`

It also pulls in every write site populating `movement_date`. Note that
`receiptDate.atStartOfDay()` **does** become genuinely zone-dependent under `Instant`
(`LocalDate → Instant` requires a zone, unlike `LocalDate → LocalDateTime`) — the opposite of its
status under D101, where it is zone-free.

Scope is therefore "the inventory module moves to `TIMESTAMPTZ`" — roughly one module of the
originally-designed migration. Still not system-wide: orders, shifts, assets and RBAC stay on
wall-clock. **Not scheduled.**

### O35 — UOM mutation: what is editable at all, and what an update path would have to carry.

D102 normalizes on write, but only inside `createForTenant`. There is no update path anywhere: no
`PUT /uom/{id}`, no update method on `UomService`, and no edit surface in the admin web (one was
built during D102 and reverted — it fronted an endpoint that does not exist).

**The prior question is whether `factorToBase` should ever be editable.** If it is not, most of
this item disappears.

**O35a — surfacing deactivation. Frontend only.** The endpoint already exists —
`PATCH /api/uom/{id}/deactivate`, `UomController.java:58` — and is the path `loadUom` serves with a
null `tenantId`. What is missing is the control: the `StatusSwitch` that would have carried it was
removed along with the reverted edit path, so a wrongly-created unit currently has no exit from the
admin web even though the backend can retire it. A button on `TenantUomPage` calling the existing
endpoint closes this. No new endpoint, no normalization, no immutability guard, nothing touching
arithmetic.

**O35b — editing `factorToBase` / `baseUom` / `type`.** Only if O35a proves insufficient. Requires
three things shipping as one unit:

1. the endpoint, tenant-scoped, parent resolved through `resolveParentUom` exactly as create does
2. normalization on the update path — the same computation `createForTenant` does inline today;
   the second caller is what would justify extracting it (D13)
3. the immutability guard over the sixteen columns in D102's table, with a new
   `UOM_IMMUTABLE_IN_USE` in `InventoryErrorCode` (deliberately not added during D102 — it would
   have been dead code)

Any one of the three shipping alone is worse than nothing shipping.

**Leaning: O35a only, and O35b rejected.** A unit created with a wrong factor and no history is
cheaply replaced; deactivate + recreate loses nothing and keeps `factorToBase` immutable by
construction rather than by guard. Name, Arabic name, and symbol remain the open question — they
carry no arithmetic and are safe to edit, but nothing serves them: deactivate is the only mutation
the backend exposes, and it does not touch presentation fields. Decide whether O35a
covers them or whether presentation fields get their own narrow update path.

**Frontend follows, not leads.** No edit button, disabled control, or "coming soon" affordance
until a backend endpoint exists.

### O36 — Integration tests run against the dev database; there is no test datasource.

Discovered during D102: a test run applied `V46__uom_root_normalization.sql` to the live dev
database, because `src/test/resources` has no `application.yml` and the test profile falls through
to the default datasource. The outcome happened to be the intended migration and a pre-migration
backup exists (`scratchpad/uom_backup_pre_v44.sql`), so no damage — but the exposure is permanent
and applies to every integration test in the repo, not to that one run.

Any test that writes, any Flyway-managed schema change, and any `@Transactional` boundary that
does not roll back is currently operating on real data. The absence of production tenant data is
the only reason this has cost nothing so far.

**Fix before the next round of test writing.** Reports and the timezone overhaul both add
significant test surface and both touch schema. Testcontainers is the obvious candidate given the
Postgres-specific SQL already in use (`AT TIME ZONE`, recursive CTEs, `pg_constraint` reads) — an
H2 profile would diverge from the real dialect in exactly the places the tests need to be faithful.

Interim workaround, until this is fixed: point the suite at a throwaway database explicitly.

```bash
psql -h localhost -U postgres -d postgres \
  -c "DROP DATABASE IF EXISTS restaurant_saas_test;" -c "CREATE DATABASE restaurant_saas_test;"
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/restaurant_saas_test" mvn -o test
```

Two related traps worth recording: `validate-on-migrate` is `false`, so a migration whose version
is already recorded is skipped **silently** rather than reported as a conflict; and a renamed
migration leaves its old copy in `target/classes/db/migration`, so Flyway applies both until
`mvn clean`.

Infrastructure, unrelated to UOM. Filed here only because that is where it surfaced.

### O37 — SysAdmin UOM panel posts `baseCode`; the DTO declares `baseUom`, so every panel-created global UOM is a root.

`AdminUomFormModal.tsx` posts `baseCode` as a String where `UomRequest` declares `baseUom` as a
`Long` id. Jackson drops the unknown field, so the row lands with `base_uom_id = NULL` — a claimed
calibration root. The same modal also calls `PUT /sys-admin/uom/{id}`, which `PanelUomController`
does not implement (it has create and deactivate only).

Left unfixed deliberately during D102. With `ck_uom_root_factor` in place, a panel create carrying
a factor ≠ 1 now fails at the database, which is the correct loud failure on a sysadmin-only
screen — previously it silently produced an unconvertible unit. A panel create with factor 1 still
succeeds and is a legitimate root, so the constraint does not block the panel's valid use.

The practical consequence: the panel can no longer add a non-root global unit at all. Because
`baseCode` is always dropped, every panel create lands as a claimed root — so a create carrying a
real factor now fails at `ck_uom_root_factor`. Adding POUND, GALLON, or any new calibrated unit to
the global catalogue is blocked until this is fixed. Before D102 the same action "succeeded" and
produced a silently unconvertible unit, so this is the correct trade — a closed capability rather
than a corrupt row — but it is a closed capability, not merely a tolerated bug.

A root create (factor 1) still succeeds and is legitimate, and tenants can create their own
calibrated units against existing roots, which is why this is not urgent.

Fixing it means `baseCode` → `baseUom` in the modal, and either implementing the missing PUT or
removing the edit path — the latter interacts with O35, so decide O35 first.

> Note: earlier drafts of D102 and the V46 migration comment referred to this item as "O29", which
> in this document is the audit-user-columns item. Corrected to O37.

### O38 — Whether `uomId` alone satisfies D88.

Raised by D111. D88 requires a ledger-sourced quantity to carry a converted value **and** an
explicit UOM field, naming `uomId` + `uomSymbol` and stating that neither alone is sufficient. Its
stated reasoning is that "the next consumer will not have read the pull request" — a bare number
plus an opaque integer is exactly what it exists to prevent.

With a client-side lookup cache, the admin frontend *can* resolve `uomId`, so for that one consumer
the symbol is redundant. Other consumers cannot: a direct API caller, a future integration, and the
reports AI assistant (O21) all see an opaque id.

**Not decided:** whether D88's "explicit UOM field" is satisfied by an id that a *documented*
lookup endpoint resolves, or whether the human-readable symbol is the point. Three responses carry
`uomSymbol` today and keep it until this is settled — `PhysicalCountLineResponse`, the
order-consumption material/error rows, and `GET /{id}/post-freeze-movements`.

Low urgency: the cost is three redundant string fields. It is recorded so the inconsistency reads
as a deliberate carve-out rather than an oversight, and so nobody removes them as tidy-up.

### O39 — Report visibility: separate RBAC from screen permissions; one catalog for built-in and dynamic reports

Grew out of matching competitors' 300+-report catalogs without hand-writing 300 backend
endpoints. Extends O22's tenant-customization case (c) and narrows it: this is **not** the
rejected end-user query builder. The SQL itself (a database view) is written by a developer or
tenant admin with real DB access — exactly how a report query is written today. The "builder" is
a UI that binds a view's columns to slots on a pre-built report template (money/date/group-key/
etc.) and stores that mapping. No free-form SQL, joins, or filters are ever constructed from user
input.

**Catalog: one table for every report, built-in or dynamic.** `ReportDefinition` carries a
`sourceType` discriminator: `BUILTIN` (backed by the existing hand-written Java query + DTO per
D86 — unchanged) or `DYNAMIC_VIEW` (backed by a `viewName` + a field→template mapping). Both live
in the same catalog and share the same visibility mechanism below, so "can this user see this
report" has exactly one place to check regardless of origin.

**Visibility is separated from screen RBAC, and made per-report.** Reaffirms then narrows D86's
"one permission for all reports, no per-report split until a real need appears" — the real need
is now concrete: two users on the same tenant/role need different report subsets (e.g. Stock
Valuation but not Waste Report). Minting a `Permission` row per report would work for the small,
code-controlled set of `BUILTIN` reports, but recreates the clutter problem for a `DYNAMIC_VIEW`
catalog that can run into the hundreds. Instead, visibility gets its own two tables, mirroring
D36/D37/D38's existing shape one-for-one:

```
RoleReportAccess(roleId, reportDefinitionId)       -- defaults per role
UserReportAccess(userId, reportDefinitionId)       -- materialized per-user snapshot
```

- At user creation, the new user's role's `RoleReportAccess` rows are copied into
  `UserReportAccess` — the same moment D36 already copies `RolePermission` → `UserPermission`.
- Editing a user's report access is a **hard delete-all + bulk-insert** of the full new set,
  never a merge — same rule D36 already applies to `UserPermission`, for the same reason (no
  drift, no `GRANT|DENY` overlay to reconcile).
- A `reset-to-role-defaults` endpoint for report access mirrors D37 exactly, for the same reason:
  the only way to pull a drifted user back in sync.
- `Permission` / `RolePermission` / `UserPermission` (screens) are untouched — this is a
  parallel, report-specific mechanism, not a fork of general RBAC. Screens keep exactly the shape
  they have today.
  **Indexing.** `UNIQUE(user_id, report_definition_id)` on `UserReportAccess` — both the duplicate
  guard and the index the login-time fetch actually uses. A secondary index on
  `report_definition_id` alone only if an admin-facing "who can see report X" screen is built
  later. Also index `report_tenant_assignment(tenant_id)` (below).

**Tenant catalog stays a separate concern from user-level access.** Which reports exist *for a
tenant at all* (built-ins are implicitly all tenants; `DYNAMIC_VIEW` reports are assigned per
tenant) is catalog/assignment data, not RBAC — a `report_tenant_assignment(tenantId,
reportDefinitionId)` row. `UserReportAccess` only ever grants visibility into what the user's
tenant is assigned; the two checks are independent and both required.

**Tenant isolation for `DYNAMIC_VIEW` reports.** Every such view must expose a `tenant_id`
column; the generic execution endpoint injects `WHERE tenant_id = :currentTenant` itself
(derived from the authenticated `X-Tenant-Id`/JWT context, never a client-supplied value) rather
than trusting the view author to have written it correctly. The `report_tenant_assignment`
lookup is keyed off the same authenticated tenant, so a guessed/foreign report id fails at the
assignment check before the view ever executes — defense in depth, not a single guard.

**Client-side login-time fetch is presentation only.** The full `UserReportAccess` list is
fetched at login so the UI can build the reports menu/sidebar dynamically per user — this is
convenience, not the security boundary. Every report fetch is re-checked live against
`UserReportAccess` server-side, matching D36's existing "no JWT-embedded permission cache,
`hasPermission()` queries live on every request" principle. A stale or tampered client-side list
can at worst show a menu item that then 403s — it can never grant access the server wouldn't
otherwise give.

**Both source types share one auth check, not one execution path.** A `BUILTIN` report keeps its
own hand-written controller/service/DTO (D86 unchanged); a `DYNAMIC_VIEW` report is served by one
generic endpoint parameterized by `viewName`. Both call the same `UserReportAccess` check before
doing anything else.

**Not decided:**

- Whether a coarse tenant-wide "reports feature enabled at all" flag is still needed alongside
  `RoleReportAccess`/`UserReportAccess`, or whether an empty access set is sufficient signal on
  its own (leaning: the latter — no separate flag, per D13). See Item 2's `tenant_feature` table
  if one is wanted regardless.
- Caching strategy and TTL for `UserReportAccess` reads (agreed direction: cache it, since it
  will be read on every report request; mechanism and invalidation-on-edit are not designed).
- Sandboxing for `DYNAMIC_VIEW` execution: read-only DB role, statement timeout, row cap —
  carried over from the original ReportBuilder discussion, still open.
- Exact `ReportDefinition` / `UserReportAccess` naming and package placement.
---

### O40 — Tenant-level configuration: split typed settings from feature toggles

Explicitly deferred by agreement — pick up after current work, not now. Recorded so the
direction isn't re-litigated from scratch.

`Tenant.timezone` (D101) is precedent: a typed, validated column (`VARCHAR(64) NOT NULL`, IANA
zone id, no default) directly on `Tenant`, not a row in a generic settings table. Extending that
precedent, tenant-level configuration splits into two shapes that should **not** share one table:

- **Typed config values** (timezone, tax rate, service charge rate, currency, …) — each has its
  own data type and validation rule. These stay typed columns, either directly on `Tenant` or in
  a 1:1 `TenantSettings` companion entity if `Tenant` gets too wide — a normalization choice, not
  a schema-philosophy change. A generic `value VARCHAR` column would lose the DB-level type
  safety and constraints (`NOT NULL`, format checks) D101 already established for exactly this
  class of data.
- **Feature toggles** (module visibility, an `ALLOW_CUSTOMIZED_REPORTS`-style flag, and whatever
  else accumulates) — genuinely one shared shape (`tenantId`, `featureCode`, `enabled`), so a
  small `tenant_feature(tenant_id, feature_code, enabled)` junction table is justified per D13
  (≥2 concrete callers already: module visibility + a reports feature flag). `feature_code` is a
  fixed enum (`EnumType.STRING`, matching CONVENTIONS), not a free string.
  A single UI settings screen can still read from both sources and present them together — that's
  a presentation concern, not a reason to merge the underlying tables.

**Not decided:** the exact list of typed columns to add now vs. later; whether `TenantSettings`
is a separate table from day one or `Tenant` grows in place until it's visibly too wide; the full
enum of `feature_code` values.

### O41 — RESOLVED 2026-09-01: reading a UOM id off a lazy proxy is free, and the joins are gone. ✅

> **This entry was opened on a claim that turned out to be false.** It is kept, with the false
> claim visible, because the correction is the useful part.

**What was asserted, and was wrong.** That `Uom` maps its `@Id` by field access, so Hibernate cannot
short-circuit the identifier getter, so `uom.getId()` initializes the proxy, so dropping
`LEFT JOIN FETCH sb.uom` or `JOIN FETCH r.uom` would trade one join for a SELECT per unit. On that
reasoning phase 3 shipped with the joins left in place and claimed no query saving.

**What measurement showed.** `UomIdReadCostIntegrationTest` counts statements via Hibernate
`Statistics` around each read:

| Read on an uninitialized proxy | Statements | Proxy initialized after |
|---|---|---|
| `uom.getId()` | **0** | no |
| `uom.getSymbol()` | 1 | yes |

Hibernate does short-circuit the identifier read regardless of the field-access mapping. The joins
existed to serve `uom.getSymbol()` — the very field phase 3 deleted — so once the mappers emit only
`uomId`, the joins are dead weight. Both are now dropped, and the three responses with no join
(`PurchaseInvoiceLineResponse`, `PurchaseReturnLineResponse`, `WasteLineResponse`) were never paying
a per-line cost for the id either. Full suite green with no `LazyInitializationException`.

So phase 3 delivers the query saving after all, and the "payload only, not queries" line in earlier
commit messages on this branch is wrong.

**The read-only-column hazard is still real and still worth knowing**, even though it is no longer
needed here. A `@Column(name = "uom_id", insertable = false, updatable = false)` beside the
association reads `null` on an entity that has been `save()`d but not flushed, and `WasteService`,
`PurchaseInvoiceService` and `PurchaseReturnService` all map exactly that
(`mapper.toResponse(wasteRepository.save(doc))`). Anyone reaching for that pattern on a create path
in this codebase gets a silent null.

**The process lesson.** The original claim is textbook-plausible, is repeated widely, and was stated
confidently across three commits and this document before anyone measured it. A ten-line test
settled it. Performance claims about the ORM in this repo get measured, not reasoned.

### O42 — The Flutter app has no UOM lookup cache, and phase 3 broke two of its screens. ❌

> **Known broken, by decision.** The phase-3 cut was taken in full on 2026-09-01 with mobile
> explicitly descoped. This entry is the record of what that costs and how to repay it — it is not
> a proposal.

D111's phase-3 cut named five response DTOs. Two of them are consumed by
`restaurant_saas_mobile`, a second client that phase 2 never reached:

| Endpoint | DTO | Mobile model |
|---|---|---|
| `GET /api/inventory/warehouses/{id}/stocks` | `StockBalanceResponse` | `lib/data/models/stock_balance.dart` |
| `GET /inventory/purchase-invoices` | `PurchaseInvoiceLineResponse` | `lib/data/models/purchase_invoice.dart` |

Both models read `json['uomSymbol']` with an `?? ''` fallback and render the result straight beside a
quantity (`'${item.quantity} ${item.uomSymbol}'`). The app parses **no `uomId` anywhere** and calls no
UOM endpoint, so it cannot resolve an id. Cutting the field yields a bare number with a trailing
space — no exception, no log, and the mobile suite still passes because `models_test.dart` feeds it
a fixture rather than a live response. `stock_balance.dart` carries the comment "Rendered beside
every quantity, per D88 — a bare number is a defect even when correct", which is exactly the defect.

**Current state: both fields are gone from the API.** The two mobile screens now show a quantity
followed by a trailing space. Nothing throws, nothing logs, and `models_test.dart` still passes,
because it asserts against a hand-written fixture that includes `uomSymbol` rather than against a
live response — so the mobile suite is green and the app is wrong. That test is the thing to fix
first; it is currently evidence of nothing.

**To close this**, mobile needs the phase-2 equivalent: parse `uomId`, add a lookup cache against
the existing `GET /api/uom/lookup` (it already serves ETags and the version header), and resolve at
the two render sites — `inventory_screen.dart` and `inventory_document_details_screen.dart`. The
backend side needs no work; phase 1 is deployed and the endpoint is client-agnostic.

**The general lesson is worth more than the fix.** "Which clients consume this DTO?" is not answerable
from the backend repo, and D111 counted render sites in one frontend. Any future application of the
D111 rule has to enumerate consumers across all client repos first — `restaurant-pos`,
`restaurant_saas_mobile`, `restaurant-saas-panel`, `restaurant-saas-client-web`.

### O43 — a `StockBalance` settings write returns 500 on an optimistic-lock conflict.

Surfaced by the D113 implementation pass, on the pre-existing `minimumQuantity` write path that
D113's `maxAgeDays` deliberately mirrors.

`StockBalance` is the one entity carrying `@Version` (`CONVENTIONS.md`). Its settings write loads
the versioned entity, mutates it, and calls `save()` in one transaction — no retry, no targeted
UPDATE. A concurrent stock movement on the same `(material, warehouse)` row makes the save fail,
and the framework exception reaches the client as a structured **HTTP 500 `INTERNAL_ERROR`**.

Two things are wrong with that, and only the second is severe:

- A recoverable concurrency conflict is not a server fault. It should be **409** with a
  retry-signalling `errorCode`, so the UI can say "someone changed this, save again" instead of
  "something broke".
- A 500 means the failure never reached a structured `AppException` at all, so it is outside the
  D12 path rather than an unhelpful member of it.

**Not fixed in the D113 pass, deliberately** — it is pre-existing behaviour on a path that pass
only mirrored, and fixing it there would have mixed a concurrency-semantics change into a
read-model addition. Recorded because D113 **doubled its surface**: `maxAgeDays` now shares the
same write path, so the conflict window is hit more often than when `minimumQuantity` was alone
on it.

Whoever picks this up: the fix is one path, not two columns — do not solve it per-field.

### O44 — `StockBalance.updateSettings` has no decided full-replace or partial-update policy.

The settings write paths have chosen opposite null semantics for fields introduced together by
D113. `MaterialService` guards `expiryTracked` against null; `StockBalanceService.updateSettings`
now guards `maxAgeDays` because an omitted field was actively wiping the configured value, while
`minimumQuantity` still becomes zero and `maximumQuantity` still becomes null when omitted.

The endpoint-wide policy remains undecided: either it is a full replacement and every client must
send the complete settings object, or it is a partial update and every omitted field must be
preserved. The durable fix is one coherent path, not another field-specific exception. **O43 and
O44 are two open questions about the same method** — optimistic-lock conflict handling and null
semantics — so whoever picks up either must read both.

### O45 — the first-row `TenantSequenceService.increment` race survives for entity codes.

Deleting the unused `generateDocumentNumber` wrapper removes the server-year trap and one caller;
it does not remove the race in `increment`. `findForUpdate(...).orElseGet(newCounter)` can lock only
an existing row, so two concurrent first allocations for the same `(tenant, year, sequenceKey)`
both construct sequence 1 and one loses to the unique constraint without a recovery path.

`generateEntityCode` still calls that method with `year = 0`. The exposure has moved from document
numbers to entity codes on a new tenant's first Material, Supplier, or Warehouse, when concurrent
onboarding requests are plausible. D112's build-note claim that F7 was "retired along with the
service" was accurate about the retired document wrapper, not about the race. Fixing `increment`
is deliberately deferred because the method serves all six D75 entity types and needs its own
decision and regression coverage.

### O46 — integration seeds use `ON CONFLICT (id) DO NOTHING`, so a stale row silently replaces the fixture.

Surfaced on 2026-09-03, when `LowStockReportServiceIntegrationTest.computesShortfallAsMinimumMinusQuantity`
failed with `expected "2.000000" but was "0.000000"`. The test was not at fault and its code had
not changed. A leftover `stock_balance` row at the hardcoded id `993601`, belonging to a
hand-made `HTTP Live Check Tenant` (`993001`) left in `restaurant_saas_test` by a manual live
check, occupied the id the seed inserts. Because the seed is
`INSERT ... ON CONFLICT (id) DO NOTHING`, the insert became a no-op and the test asserted against
the stale row's values instead of its own.

**The confusing failure is the good case.** The same idiom fails the other way just as easily: if
a stale row happens to carry values that satisfy the assertions, the test **passes green while
never having exercised its own fixture**. A seed that cannot guarantee its values is a test whose
subject is unknown.

Two changes are needed, and neither is made here:

- **The seed must force its values** — `ON CONFLICT (id) DO UPDATE SET ...` covering every seeded
  column, or an explicit delete-then-insert. `DO NOTHING` is only safe for rows whose content is
  irrelevant to the assertions.
- **Hardcoded ids in a shared persistent database are the underlying hazard.** Every integration
  test picking its own "dedicated high range" is a convention with no enforcement: nothing stops
  two fixtures, or a fixture and a stray manual one, from choosing the same range. A per-test
  schema, a truncate-between-runs policy, or Testcontainers removes the class of problem;
  range conventions only reduce its frequency.

**Cross-reference O36**, which records that integration tests had no guaranteed datasource. The
two are the same failure in different layers: O36 is *"we do not know which database the tests
ran against"*, O46 is *"we do not know which rows the tests ran against"*. In both, the suite
reports green without a guarantee about what it exercised, which is the property that makes a
suite worth running. (O36's own text predates the committed test datasource in
`src/test/resources/application.yml`; its premise is stale, its lesson is not.)

Related, and the reason the leak existed at all: the manual check that created the fixture also
left a `users` row and a `user_permissions` row in the shared test database. A live check needs a
throwaway target or an explicit teardown, not a shared one.

### O47 — no `maximumPoolSize` is configured; the default of 10 is now load-bearing.

Neither `application.yml`, `src/test/resources/application.yml` nor `nixpacks.toml` sets
`spring.datasource.hikari.maximum-pool-size` or `minimum-idle`, so HikariCP's default
`maximumPoolSize = 10` applies in every environment including production.

This became worth recording when D112's allocator moved to
`@Transactional(propagation = REQUIRES_NEW)`: each document-number allocation now borrows a
**second** pooled connection for the duration of one upsert round-trip, while its caller still
holds the first. The cost is microseconds and the trade is correct — it is what stops the counter
row being held for the whole create transaction — but it does mean concurrent document creation
consumes pool slots at twice the previous rate for a brief window.

**No measurement supports a number yet.** The only figure observed is a resting count — 30 JDBC
connections across two idle application instances against the dev database, dropping to zero on
shutdown. That is not a load measurement and must not be read as headroom. The pool size should
be set explicitly, from a measurement under realistic concurrency, before production.

### D20 (moved from DECIDED) — Order module: cancellation carries a POS-supplied `cancellationStage`, never inferred. ✅

> **Implemented 2026-09-20 by D134**, at line level rather than order level. The stage→consequence
> mapping below is now real: cooked stages produce a waste consumption document, earlier ones
> produce nothing. The contradiction that moved this item to OPEN is resolved; it belongs back in
> DECIDED whenever the sections are next tidied.

> **Recorded as DECIDED, but not implemented.** Moved here on 2026-08-30 by the documentation
> drift audit. The stage-to-consumption mapping below was written as ground truth and **no part
> of it exists in code**: `OrderService` validates and persists `cancellationStage` and the
> cancellation reason, but performs no waste mapping for any cancelled order, and only a
> `COMPLETE` order triggers order consumption (`order/core/OrderService.java:120-138`, `:222-241`).
> **The open question is which cancellation stages consume stock, and as what** — waste,
> sale-equivalent consumption, or nothing. Until that is answered and built, the decision text
> below describes an intent, not the system. It is kept verbatim, and the gap is the point.

Populated only when `status = CANCELLED`, sent as-is by the POS:
`BEFORE_KITCHEN | IN_KITCHEN_COOKED | IN_KITCHEN_NOT_COOKED | AFTER_DONE`. No separate audit field for "last kitchen
stage before cancel" — rejected as unnecessary complexity;
`cancellationStage` alone is sufficient since it's already the POS's authoritative decision. Maps directly to
consumption behavior: `COMPLETE` → sale consumption. `IN_KITCHEN_COOKED` /
`AFTER_DONE` → waste consumption. `BEFORE_KITCHEN` / `IN_KITCHEN_NOT_COOKED` → no consumption, order excluded from
`OrderConsumptionDoc` entirely.

### O48 — Whether `AssetMaintenance` auto-posts an expense.

**Direction agreed, deliberately not built.** Maintenance cost lives on `AssetMaintenance` today
(D49). It is also, plainly, money spent — so leaving it out of Expenses makes "where did the money
go" wrong, while letting users type it into both places guarantees double counting in the eventual
P&L.

Agreed direction: **Expenses is the lower layer and Assets posts into it** — creating an
`AssetMaintenance` writes an expense row with `sourceType = ASSET_MAINTENANCE`, and such rows are
not creatable, editable or voidable from the Expenses screen; they follow their source document.

Held open because the maintenance flow has not been exercised in real use yet, and the shape of
the posting should follow what that use shows. Whoever picks this up owns: the `system_key` column
on `expense_category` (D116), the `MAINTENANCE` seeded key, the new Assets → Expenses dependency
direction, and the reversal path when a maintenance record is removed.

Until then, maintenance is entered by hand under the seeded Maintenance & repairs category, and
**users must not be told to record it in both places.**

### O50 — Whether payroll posts expenses, and at what grain.

Blocked twice over. The payroll module is not designed, and it carries a known blocker of its
own: **`Employee` has no `branchId`** (a consequence of D33), so a payroll-sourced expense has no
branch to be attributed to. Whichever pass resolves payroll's branch question also decides whether
a run posts one expense per branch, one per run, or one per line — and owns the `PAYROLL`
`system_key` from D116.

Until then, salaries are entered by hand under the seeded Salaries & wages category.

### O51 — `Expense.paidFromShiftId` and the shift-close freeze rules.

Deferred out of the Expenses pass by D118; now partially implemented. The nullable
`paid_from_shift_id` column, selectable-shifts API, expense-screen picker, frozen
`Shift.expensesAtClose`, and separate late-expense/explained-variance read model exist.
Shift detail converts tenant-local expense audit timestamps to branch time before classifying
late expenses. Completion still depends on coordinated expense creation/close ordering and
concurrent-close protection; those review findings await the user's next decision. See
[SHIFT_REVIEW_FOLLOWUP.md](SHIFT_REVIEW_FOLLOWUP.md).

### O52 — Folded into the shift rewrite.

Cashier identity from the header is fixed as part of the implementation, not separately. The
Phase 0 audit found that close does not verify ownership and order creation targets a shift by
header-supplied cashier id; those are live defects folded into the D120/D122 rewrite.

### O53 — Closed.

The existing shift module was read during the Phase 0 audit. Its findings are recorded above in
the shift implementation audit block.

### O54 — `PosShiftEvent`: the POS sends no event trail.

The backend sees only orders that arrive `COMPLETE` or `CANCELLED` (D100). Everything happening
inside the POS before payment -- a ticket opened and dropped, an item removed after it printed to
the kitchen, a discount, a price override, a no-sale drawer open, a receipt reprint -- **is
invisible**.

D125's cancellation ratio is therefore **structurally incomplete**, and will remain so until this
exists. That is the difference between a report that catches someone and a report that looks like
it might. This is not a neutral deferral: shipping the cashier performance report before this
exists would produce a screen that looks authoritative while seeing only part of the behaviour.

Designed shape, not built: `PosShiftEvent(shiftId, type, orderRef, amount?, reason?, userId,
occurredAt)`, append-only, no logic, counted in the exception report. Types for a first version:
`TICKET_VOID`, `LINE_DELETE_AFTER_FIRE`, `DISCOUNT_APPLIED`, `PRICE_OVERRIDE`,
`DRAWER_OPEN_NO_SALE`, `RECEIPT_REPRINT`. Depends on the offline/outbox path settling (D65).

### O55 — Card settlement and third-party providers.

Card takings never enter the drawer and are **not part of any shift figure**. They are a
receivable: the provider (Fawry, PayMob, Geidea) holds the money and remits it later, net of fee.

Designed, not built:

- `PaymentProvider` -- thin tenant-scoped master data, with `feePercent`
- `PaymentTerminal` -- the physical machine, `branchId` + `providerId`. **A branch may run
  machines from more than one provider**, so the terminal must be captured at payment time; a
  branch-level provider assumption does not hold
- `ProviderSettlementLine` -- generated per (shift x terminal) at close, frozen, append-only,
  with `status: OUTSTANDING | SETTLED`. A line settles whole or not at all -- no partial
  settlement of a line -- with any difference falling on the settlement header
- `ProviderSettlement` -- the collection document: expected gross, received net, date; computes
  `unexplained = expectedGross - receivedNet - expectedFee`, which is the figure that gets watched

Plus an ageing view per provider (0-7 / 8-14 / 15+ days outstanding). The fee becomes an expense
once P&L exists (O16).

### O56 — Spot counts.

A manager counts a drawer mid-shift, unannounced, without closing anything. Deferred.

**Its value is not the figure it produces -- it is that the cashier cannot predict when it
happens.** Without it, the only counted moments are open and close, both known in advance, and the
system is fully predictable to the person it is measuring.

Added after the Phase 0 audit: a spot count belongs to neither the opening nor the closing slot,
so it is **the change that justifies extracting counts into their own table** (D119). Whoever
builds it owns that extraction. It should be blocked while a payment is in flight, which bounds
the "contaminated by an in-flight sale" objection to a single transaction.

### O57 — `Branch.varianceTolerance`.

A per-branch threshold below which a variance is recorded but not flagged. Necessary because
cashiers routinely settle small shortfalls personally and reclaim later, so without it every shift
flags and the report becomes noise nobody opens -- which is worse than no report. Not yet
specified or agreed.

### O58 — Should the cashier enter the card terminal's own total at close?

Would catch cash being rung as card. Costs time at close. Undecided.

### O59 — Resolved by D126.

Close waits for an empty queue and requires connectivity, so an order cannot arrive after its
shift has closed. Nothing was built to solve it; the case is prevented rather than handled.

### O60 — The end of the day is unwatched.

The last thing the system knows is the final shift's closing count. Money then leaves the drawer
for the owner with **no record of the handover at all**. Nothing is proposed; recorded so nobody
assumes the chain is complete.

### O61 — Notifications.

There is **no notification infrastructure of any kind** -- no in-app, no push, no email. Anything
depending on a notification would sit unseen until someone opens the app.

Therefore: **a badge on the manager's home screen and a default filter on the shifts list** is the
mechanism now, and the screen remains the source of truth even after notifications exist. Push
notifications are additive, and are their own project.

### O62 — Branch scoping across the platform.

> **Largely decided 2026-09-21 by D135**: visibility is one branch or all, gated on
> `Role.branchScoped`, resolved server-side from the principal. The "partial access" case named
> below is **deliberately out** — no subsets, with an additive upgrade path if it becomes real.
> O62 stays open only for the write-side question D135 excludes and for the per-endpoint rollout
> list.

A multi-branch client needs staff who open on their own branch, staff with access to all branches,
and staff with partial access. This is **not a shifts concern** -- it will change most or all
endpoints -- but it determines **whose shifts a given user can see** in all three D125 surfaces,
so the reporting screens cannot be finalised before it is decided.

### O63 — Split payment.

`Order.paymentMethod` is a single enum (D25), so the system **cannot express** an order paid part
cash, part card. Any split is recorded entirely under one method, and both the drawer figure and
the card figure are then wrong.

Agreed direction if taken up: an `OrderPayment` child table `(orderId, method, terminalId?,
amount)`, with `Order.paymentMethod` derived or removed. Deferred until after shifts by explicit
decision -- but note that **every drawer reconciliation figure reads from this field**, so the
deferral is a known limit on their accuracy, not a neutral one.

### O64 — Moving a drawer's balance to a replacement device.

A device fails and is replaced. The cash is physically unchanged, but the new device has no prior
count: its first shift gets a null `handoverVariance` and the balance restarts from whatever is
counted. **The money in the drawer leaves the reconciliation silently.**

Agreed: rare, and handled by an **administrator-performed transfer**, not a user-facing flow.
Shape if built: an admin action recording an opening count on the new device that references the
old one, so the continuity is visible rather than inferred. Not built; without it the case is
resolved in the database by hand.

### O65 — Opening a shift offline.

Open requires connectivity (D126), so a branch with no network at the start of the day cannot
trade. This is the existing behaviour, not something introduced here. Revisit when offline
operation is extended (D65): whether a device may open a shift offline and reconcile later, and
what that does to the one-open-shift-per-device constraint, which cannot be enforced by a database
while the device is disconnected.

### O66 — The `V48` migration version is unused, and a migration arriving there would be skipped.

Found during the shift rewrite's Phase 0. The migration sequence runs `V47` -> `V49`; **no `V48`
exists**. Flyway is configured with `validate-on-migrate: false` and no `out-of-order` key, which
defaults to false (`application.yml:25-31`).

A version number lower than the current schema baseline is **ignored rather than applied**, and
with validation off there is no error and no warning. So a migration authored as `V48` -- by
someone filling the visible gap, or by a branch cut before `V49` landed -- **silently never
runs**. It surfaces much later as a missing column in production, with a schema history that
claims success.

Harmless as it stands: nothing occupies the slot and nothing needs it. Recorded rather than fixed
because the fix is a configuration decision with repo-wide blast radius (enabling `out-of-order`,
or turning validation on and resolving whatever it then reports), not a shifts concern. Related to
the same configuration's existing behaviour noted for out-of-order arrivals generally.

**Whoever picks this up owns:** whether `out-of-order` is enabled, whether `validate-on-migrate`
returns to true, and what the existing history does when validation is restored. Until then,
**never author a migration below the current maximum** -- shift migrations start at `V56`.

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
