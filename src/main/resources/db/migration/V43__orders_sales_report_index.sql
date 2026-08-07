-- =====================================================================
-- Covering index for the sales reports (over time, by product, by payment method).
--
-- All three scan orders by (tenant_id, status = 'COMPLETE', order_date range).
-- The existing indexes each serve two of the three columns and no more:
--   idx_orders_tenant_status      (tenant_id, status)             -- no date
--   idx_orders_tenant_branch_date (tenant_id, branch_id, order_date)
--       -- has the date, but branch_id sits in front of it, so the range is only
--          usable when a branch filter is supplied; branch is optional here.
--
-- Ordering status ahead of order_date is deliberate: status is an equality
-- predicate and order_date a range, so the range must come last to stay usable.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_orders_tenant_status_order_date
    ON public.orders USING btree (tenant_id, status, order_date);
