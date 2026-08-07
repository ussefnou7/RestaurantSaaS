-- =====================================================================
-- Covering index for the purchase price drift report.
--
-- That report scans stock_batch by (tenant_id, movement_date range) and
-- considers only purchase-origin batches. The existing indexes do not serve it:
--   idx_stock_batch_open_fifo            (stock_balance_id, status, movement_date, id)
--   idx_stock_batch_balance_movement_date(stock_balance_id, movement_date, id)
--     -> both lead with stock_balance_id, which the report does not filter on.
--   idx_stock_batch_tenant_source_invoice(tenant_id, source_invoice_id)
--     -> leads correctly but carries no date, so the range degrades to a heap filter
--        over every purchase batch the tenant has ever received.
--
-- Partial, not full: batches are also opened by opening balances, transfers in, and
-- physical-count surpluses (StockBatchService.BATCH_OPENING_TYPES). None of those are
-- purchases and the report excludes them, so indexing them would only add write cost
-- and bloat. source_invoice_id is set only for referenceType = 'PURCHASE_INVOICE',
-- which makes "IS NOT NULL" exactly the purchase-origin predicate.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_stock_batch_tenant_purchase_movement_date
    ON public.stock_batch USING btree (tenant_id, movement_date)
    WHERE source_invoice_id IS NOT NULL;
