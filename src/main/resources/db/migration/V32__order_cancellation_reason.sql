-- Supports D20: POS-supplied cancellation reason details on orders.

ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(30);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS cancellation_reason_note VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_cancellation_reason') THEN
        ALTER TABLE public.orders
            ADD CONSTRAINT chk_orders_cancellation_reason
            CHECK (cancellation_reason IS NULL OR cancellation_reason IN
                ('CUSTOMER_REQUEST', 'ITEM_UNAVAILABLE', 'WRONG_ORDER', 'PAYMENT_ISSUE', 'KITCHEN_DELAY', 'OTHER'));
    END IF;
END $$;
