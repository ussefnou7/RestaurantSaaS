ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS order_no VARCHAR(50);

-- Supports exact-match filter on GET /api/orders?orderNo=…
CREATE INDEX IF NOT EXISTS idx_orders_tenant_order_no ON public.orders (tenant_id, order_no);
