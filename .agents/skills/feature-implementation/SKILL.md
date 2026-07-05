---
name: feature-implementation
description: Implement a feature, endpoint, or component (backend or frontend). Use when writing new code — "implement", "add", "build" an endpoint/service/entity/page/component.
---

Read `docs/CONVENTIONS.md` (Backend + Frontend) and `docs/DECISIONS.md` before writing. Follow
them; do not duplicate their content here.

## Backend
- Place code in the correct feature sub-package
  (`inventory/{warehouse,material,category,uom,stock,purchase,physicalcount,transfer,core}`).
- Stock writes obey the sole-writer rule: `inventory_transaction` **only** via
  `InventoryLedgerService`; `stock_balance` quantity/averageCost **only** via
  `StockBalanceService`; `stock_batch` **only** via `StockBatchService`. Move stock by building
  a `LedgerCommand` and calling `InventoryLedgerService.record(...)`.
- Entered→stock **UOM conversion only inside `InventoryLedgerService.record()`** — pass raw
  entered qty/cost, never pre-convert (D3).
- Exceptions: the six-branch hierarchy + per-module `ErrorCode` enum; emit `errorCode` +
  `ErrorParams`; the `message` is English/logs-only, never user-facing (D12). Do not use legacy
  `ApiException` / `BusinessException(String)`.
- Money/qty: `BigDecimal`, `scale=6`, `HALF_UP`.
- **New error condition ⇒ add the `ErrorCode` enum value AND the FE translation key in the same
  change** → invoke `i18n-sync`.

## Frontend
- BEM class names, `--color-*` vars only, Lucide outline icons, RTL-safe layout, Hub Page nav
  pattern.
- All user-facing text via `useTranslation()` — no hardcoded strings. Backend errors rendered
  via `translateApiError` (`errorCode` + `params`), never the server `message`.

## Always
- No premature abstraction (D13 / §1.4): the simplest working implementation; mirror the nearest
  sibling file.
- Before handoff, run the **pr-ready** self-check.
