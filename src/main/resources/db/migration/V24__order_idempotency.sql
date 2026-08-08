-- =====================================================================
-- Adds idempotency support to POST /api/orders (O16).
--
-- The POS client generates a key once, client-side, at the moment an order
-- completes locally, and resends the same key on every retry attempt
-- (network failures during an outage are retried indefinitely). Without a
-- server-side uniqueness constraint, a retry after a lost response would
-- create a second real order for the same sale. Nullable so historical
-- orders (created before this column existed) remain valid; OrderService
-- always sets it for new orders going forward.
-- =====================================================================

ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_orders_tenant_idempotency') THEN
        ALTER TABLE public.orders
            ADD CONSTRAINT uk_orders_tenant_idempotency
            UNIQUE (tenant_id, idempotency_key);
    END IF;
END $$;
