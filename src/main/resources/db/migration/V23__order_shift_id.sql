-- =====================================================================
-- Wires orders to the cashier shift (D64).
-- Nullable so that all pre-shift-feature historical orders remain valid.
-- shift_id is resolved server-side by OrderService from the cashier's
-- current OPEN shift — never accepted from the client request body.
-- =====================================================================

ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS shift_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_orders_shift_id
    ON public.orders (shift_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_shift') THEN
        ALTER TABLE public.orders
            ADD CONSTRAINT fk_orders_shift
            FOREIGN KEY (shift_id) REFERENCES public.shift(id);
    END IF;
END $$;
