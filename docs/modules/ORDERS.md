# Module: ORDERS (design — not yet built)

> Status: **design only.** No `Order` service/controller exists yet. The core decisions below
> are the intended shape; the items marked OPEN are **not** decided — see
> [../DECISIONS.md](../DECISIONS.md). Build against the existing stock engine (the ledger is the
> single writer); do not fork it.

## Purpose

Capture sales/consumption across every channel (dine-in, takeaway, delivery, online, and
third-party aggregators) and feed material consumption back into inventory **without** hammering
the ledger on every order line.

## 1. Unified `Order` entity

- One `Order` entity (tenant-owned, `TenantAwareEntity`) with discriminator fields:
  - `orderType` — the fulfilment kind (e.g. DINE_IN / TAKEAWAY / DELIVERY).
  - `orderSource` — where it came from (e.g. POS / ONLINE / TALABAT / OTLOB / NOON_FOOD / FAWRY).
- Behaviour branches on these fields; **do not** create one entity per channel.
- Order lines reference the sold catalog item; material consumption is resolved via the
  recipe/`recipeId` on consumption events (see §2).

## 2. Hybrid Ledger (stock consumption)

The hot path must not post one ledger transaction per order line.

- Each consumed material is written to **`OrderConsumptionEvent`** — the entity/table already
  exists (`inventory/core/OrderConsumptionEvent.java`), unique on `(tenant_id, idempotency_key)`,
  routed by `IdempotencyScope.ORDER_CONSUMPTION_EVENT`. Today it has a repository but **no
  posting service** — that is what this module adds.
- A **scheduled job** aggregates events per (tenant, warehouse, material, business date) and
  posts **summarized `CONSUMPTION_SUMMARY` / direction OUT** transactions through
  `InventoryLedgerService.record(...)`. The ledger then FIFO-depletes batches and re-derives the
  average exactly as for any other outbound movement — reuse it, don’t reimplement costing.
- `OrderConsumptionEvent.postedToLedger` / `postedTransactionId` mark what has been rolled up;
  `reversesEventId` supports corrections. Keep the aggregation idempotent (unique key) so a job
  re-run never double-posts.

## 3. Confirmation step

- Required for **online / aggregator** orders only (`orderSource` ∈ online/aggregator set): they
  land in a pending state and must be confirmed before they consume stock / print.
- Dine-in and POS walk-in orders **skip** confirmation.

## 4. POS

- **A single POS screen with 3 modes** (dine-in / takeaway / delivery) selectable in-screen — not
  three separate screens.
- **Offline capable**: the POS must keep taking orders when the backend is unreachable and
  reconcile on reconnect (design the sync/idempotency so replay is safe — the consumption-event
  unique key is the natural dedup boundary).
- **ESC/POS thermal printing** for customer receipts and kitchen tickets.

## 5. Aggregators

- Talabat, Otlob, Noon Food, Fawry — modeled via `orderSource`.
- **Manual entry first**: staff key the aggregator order into POS. Automated API/webhook
  ingestion comes later and its contract is OPEN.

## Reuse / guardrails (inherit from [../DECISIONS.md](../DECISIONS.md))

- Consumption posts go through `InventoryLedgerService.record(...)` — the **only** stock writer.
- Costing is unchanged: OPEN-batch-derived average (D2), FIFO by creation order (D10), shortfall
  at current average with **no retroactive COGS** today (D11).
- Exceptions use `errorCode` + `params` via a new `OrdersErrorCode` (D12); do not reuse
  `InventoryErrorCode`.

## OPEN questions (do not decide unilaterally)

- **O1** Retroactive COGS correction for shortfalls — deferred here; not decided.
- **O2** Aggregator API/webhook design (auth, dedup, field mapping).
- **O3** Whether order state transitions run through the configurable `ApprovalWorkflow`.
- Exact `orderType` / `orderSource` value sets; recipe→material resolution details; offline
  conflict-resolution policy.
