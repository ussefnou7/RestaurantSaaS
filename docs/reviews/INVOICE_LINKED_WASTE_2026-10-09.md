# Invoice-linked waste — implementation and verification

Date: 2026-10-09. Decision: D139. Changes are in the working trees; no commit or deployment performed.

## Implemented contract

- EXPIRED waste requires a posted purchase invoice in the same tenant and warehouse, with a receipt date no later than the waste date.
- Linked waste lines must reference a matching material and line from that invoice. Repeated material lines require explicit source-line selection.
- Posting depletes that purchase batch at its cost, never substitutes another batch when its remaining stock is insufficient.
- The materials endpoint accepts optional `invoiceId`, preserving the existing filters and requiring purchase-view permission for invoice-filtered reads.
- Before waste posting, same-warehouse incomplete consumption documents intersecting its materials are settled through the existing whole-document claim/process/recalculate operations, for ORDINARY and WASTE consumption alike.
- Every matched document must reach POSTED. PARTIAL, CONFLICT, or IN_PROGRESS blocks waste. The UI identifies the blocking consumption document and links to it.
- Successful consumption commits independently and survives a later waste failure. Waste posting is all-or-nothing; a document row lock and deterministic ledger keys protect against duplicate posting.
- No changes to OrderConsumptionService's allocation engine, FIFO policy, tenant policy settings, or dashboard.

## Automated verification

Backend command:

```sh
./mvnw -q -Dtest='WasteServiceTest,WasteControllerSecurityTest,WasteDocumentRepositoryJsonTest,ExpiryAndAgeIntegrationTest,StockBatchServiceTest,StockBatchOrderingIntegrationTest,InventoryLedgerServiceTest,MaterialControllerTest,OrderConsumption*Test' test
```

Result: 105 tests, zero failures, errors, or skips. Covers source batch and cost, source shortage despite other available batches, source validation, tenant/permission filtering, ordinary and waste consumption, whole-document partial failure, conflict retry, in-progress blocking, independent commits, and concurrent waste posting.

Frontend: `npm test` (58 passing tests) and `npm run build`. Source-line schema regressions cover repeated materials, unique-source auto-selection, unlinked waste, and locale parity. Existing unrelated TableLayoutPage hook dependency and bundle-size warnings remain.

Self-review: docs/REVIEW.md hard invariants checked. Source-batch waste is the explicit D139 exception to ordinary FIFO selection. Ledger remains the sole transaction writer; quantities enter as raw quantity/UOM; batch depletion converts to display UOM. Waste has no unpost/reversal. New errors have both Arabic and English translations.

## Manual acceptance still required

The admin frontend was started separately on port 5174; the browser reached the login screen. No authenticated session was available, so visual acceptance is not claimed.

1. Check the waste header and source-line controls at 1280, 1920, and 2560 px, in Arabic RTL and English LTR.
2. Create EXPIRED waste; verify invoice is mandatory and only posted same-warehouse invoices are selectable.
3. Select an invoice with repeated material lines; verify only invoice materials appear and the correct source line/expiry is selected.
4. Create overlapping incomplete consumption, including an unrelated deficient material in the same document. Verify the entire consumption document is attempted and incomplete status blocks waste with a link.
5. Resolve the consumption issue and retry. Verify no duplicate consumption or waste movement and the selected purchase batch is depleted.
6. Use a stock user without purchase-view permission: ordinary unlinked waste remains usable; invoice selection is disabled with a permission explanation for linked/expired waste.

## Boundaries

- No production migration was executed. V75 adds nullable historical source references; new EXPIRED writes enforce the mandatory invoice in application validation.
- There is no non-purchase-origin exception for EXPIRED waste (opening/count-origin stock).
- Invoice linking does not introduce a new automatic expiry-date eligibility rule.
- Full user acceptance of normal consumption remains a separate prerequisite before consumption-policy settings.

Suggested commit: `feat(inventory): link waste to purchase batches and settle consumption`
