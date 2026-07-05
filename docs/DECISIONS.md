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
