# Module: ORDERS

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155`, POS `03b0e81`
> on 2026-08-30 by Claude Code (doc drift audit — [../../claude/DOC_DRIFT_AUDIT.md](../../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as those commits.

> Status: **built**, package `com.smart.restaurant_saas.order`. This file was previously a
> design document for an unbuilt module; it now describes what exists, and names what does not.
> Companion: [PROJECT](../PROJECT.md), [ROADMAP](../ROADMAP.md), [DECISIONS](../DECISIONS.md).
> The ledger remains the single stock writer — do not fork it.

## Purpose

Capture sales/consumption across every channel (dine-in, takeaway, delivery, online, and
third-party aggregators) and feed material consumption back into inventory **without** hammering
the ledger on every order line.

## 1. Unified `Order` entity — built

One `orders` table, entity `Order` (`@Entity(name = "RestaurantOrder")`, `TenantAwareEntity`).
Behaviour branches on discriminator fields; there is no entity per channel.

| Enum | Values |
|---|---|
| `OrderType` | `DINE_IN`, `TAKEAWAY`, `DELIVERY` |
| `OrderSource` | `POS`, `ONLINE`, `AGGREGATOR` (plus a nullable `aggregatorName` string) |
| `OrderStatus` | `COMPLETE`, `CANCELLED` — **final states only**, per D19 |
| `PaymentMethod` | `CASH`, `CARD`, `WALLET`, `AGGREGATOR` |
| `CancellationStage` | `BEFORE_KITCHEN`, `IN_KITCHEN_COOKED`, `IN_KITCHEN_NOT_COOKED`, `AFTER_DONE` |
| `OrderCancellationReason` | `CUSTOMER_REQUEST`, `ITEM_UNAVAILABLE`, `WRONG_ORDER`, `PAYMENT_ISSUE`, `KITCHEN_DELAY`, `OTHER` |

Notable header fields: `branch` and `warehouse` (both required), optional `table` for `DINE_IN`
only (D76 — a non-`DINE_IN` order carrying `tableId` is rejected), `shift` resolved **server-side**
from the authenticated cashier's OPEN shift and never accepted from the request body, client-local
`orderDate`, client-generated `idempotencyKey` (unique per tenant, V24), display `orderNo`,
nullable `customerId`, and `externalOrderReference`.

`OrderLine` freezes `product`, `recipe`, `quantity`, and `unitPrice` at completion time (D21).
An order line whose product has no active recipe is rejected with
`OrderErrorCode.PRODUCT_HAS_NO_ACTIVE_RECIPE`.

Totals: `subtotal` and `taxAmount` at scale 6, `totalAmount` at scale 2, VAT applied as a single
service-level rate over the subtotal.

**Endpoints:** permission-protected `/api/orders` (create, get, summary, list with filters) and
`/api/orders/reports/*` — sales over time, by hour, by product, by payment method.

## 2. Hybrid Ledger (stock consumption) — built

The hot path does not post one ledger transaction per order line. **`OrderConsumptionEvent` does
not exist and never did** — the staging model is a document, not an event row:

```
OrderConsumption           (order_consumption)       — one doc per (tenant, warehouse), status-driven
  └─ OrderConsumptionLine  (order_consumption_line)  — one row per OrderLine (unique on order_line_id)
  └─ OrderConsumptionMaterial (order_consumption_material) — aggregated per material at batching time
```

- On save of a `COMPLETE` order, `OrderService` synchronously calls
  `OrderConsumptionService.recordCompletedOrder`, which appends lines to the warehouse's open
  `PENDING` doc. A `CANCELLED` order creates **no** consumption of any kind.
- `OrderConsumptionBatchingScheduler` polls every 60s and batches a doc when its line count
  reaches 50 **or** its oldest pending line is at least 8 hours old (D58's dual trigger). Both
  thresholds, the poll interval, and enablement are **application properties**
  (`order-consumption.batching.*` in `application.yml`) — not per-tenant settings. The age arm is
  evaluated against each tenant's own wall clock (D101), with a 2h widened pre-filter.
- Batching aggregates recipe items into per-material rows, then posts one
  `CONSUMPTION_SUMMARY` / direction `OUT` `LedgerCommand` per material through
  `InventoryLedgerService.record(...)`. The ledger FIFO-depletes batches and re-derives the
  average exactly as for any other outbound movement.
- Idempotency is the ledger key `ORDER_CONSUMPTION_DOC:<docId>:MATERIAL:<materialId>`, not an
  `IdempotencyScope` value — `IdempotencyScope` has exactly one constant,
  `INVENTORY_TRANSACTION`. There are no `postedToLedger` / `postedTransactionId` /
  `reversesEventId` columns.

**Document status** (`OrderConsumptionStatus`): `PENDING` → `IN_PROGRESS` → `POSTED`, or
`PARTIAL` / `CONFLICT` on failure. Per-material `OrderConsumptionFailureReason` drives the derived
status: `TECHNICAL_FAILURE` → `CONFLICT` (takes precedence), `INSUFFICIENT_STOCK` → `PARTIAL` (D94).
Manual retry is `POST /api/inventory/order-consumption-docs/{id}/recalculate`; the doc surface is
read-only otherwise (list, detail, materials summary), gated on `INVENTORY_STOCK_MANAGE`.

**Availability** is posted balance minus outstanding consumption, computed by
`OrderConsumptionAvailabilityService` and consumed only by `StockBalanceService`: recipes for
`PENDING`, unconsumed material rows for `PARTIAL`/`CONFLICT`, none for `POSTED`, `IN_PROGRESS`
excluded (D43/D94).

## 3. Online / aggregator intake — wired, untested

`order/intake/` holds a **generic** intake workflow, not per-brand connectors:

- `IncomingOrderRequest` with `IncomingOrderSource` (`ONLINE`, `AGGREGATOR`) and
  `IncomingOrderRequestStatus` `RECEIVED → SENT_TO_POS → LINKED`.
- Permission-protected `/api/order-requests` create/list/transition, plus an explicit
  `PATCH /api/order-requests/{id}/link` carrying a completed order id, which sets the one-way
  `completedOrderId`.
- **No test file references `IncomingOrderRequest`**, so this does not meet the "built" bar.
- There is **no** POS echo of the request id in `POST /api/orders`, and no automatic linking —
  a material divergence from the mechanism D24 assumed. See [ROADMAP](../ROADMAP.md) §1.
- There is **no** confirmation gate that blocks stock consumption: an order consumes on
  `COMPLETE` regardless of source. The "confirmation step required for online/aggregator orders"
  described in earlier revisions of this file is **not implemented**.

## 4. POS

The cashier POS is the separate `restaurant-pos` repo (React/TS, OPFS-backed SQLite). See
[PROJECT](../PROJECT.md) → POS boundary for the payload and the boundary rules.

- **Single screen, 3 modes** — built. Dine-in / takeaway / delivery map onto the unified payload.
- **Offline capable** — built. Completion and cancellation generate an idempotency key before the
  fast attempt and fall back to an idempotent SQLite outbox on network failure.
- **ESC/POS thermal printing** — **not built.** "Print/reprint" opens a visual receipt preview
  only; no printer adapter exists. Tracked in [ROADMAP](../ROADMAP.md) §1.

## 5. Aggregators

`orderSource = AGGREGATOR` + `aggregatorName` models the origin, and the generic intake API
above accepts requests. **No branded connector exists** for Talabat, Otlob, Noon Food, or Fawry —
no webhook controller, no per-brand client. The admin app can list and link intake requests but
has no manual-create surface. See [ROADMAP](../ROADMAP.md) §2.

## Reuse / guardrails (inherit from [../DECISIONS.md](../DECISIONS.md))

- Consumption posts go through `InventoryLedgerService.record(...)` — the **only** stock writer.
- Costing is unchanged: OPEN-batch-derived average (D2), FIFO by `movementDate ASC, id ASC`
  (D10), shortfall at current average with **no retroactive COGS** today (D11).
- Exceptions use `errorCode` + `params` via `OrderErrorCode` (D12); it does not reuse
  `InventoryErrorCode`.

## Known gaps and defects (not intended behaviour)

- **D20 is not implemented.** The decision maps `IN_KITCHEN_COOKED` / `AFTER_DONE` cancellations
  to waste consumption. The code performs no waste mapping for any cancellation stage; only
  `COMPLETE` consumes. D20 has been moved to [DECISIONS](../DECISIONS.md) → OPEN, and which
  stages should consume stock is the open question.
- **No `cancelledAt`.** A cancellation's only event time is the client-generated `orderDate`.
- **The `recalculate` status guard is commented out** in `OrderConsumptionService.recalculate`,
  so a doc in any status can be pushed back to `IN_PROGRESS` and reprocessed — not just `PARTIAL`
  and `CONFLICT` as the surrounding javadoc and D45/D94 state.

## OPEN questions (do not decide unilaterally)

- **O1** Retroactive COGS correction for shortfalls — deferred; not decided.
- **O2** Aggregator API/webhook design (auth, dedup, field mapping).
- **O3** Whether order state transitions run through the configurable `ApprovalWorkflow`.
- **O6** POS ingestion transport is now settled as `POST /api/orders`; whether the POS should echo
  an intake request id back, and how connector→POS delivery works, remain open.
- **O26** Whether order consumption should exit early on repeated technical failures.
- Offline conflict-resolution policy beyond the outbox's idempotency key.
