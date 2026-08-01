-- Supports D94: insufficient stock leaves an order consumption document PARTIAL.
DO $$
BEGIN
    ALTER TABLE public.order_consumption
        DROP CONSTRAINT IF EXISTS chk_order_consumption_status;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_order_consumption_status'
          AND conrelid = 'public.order_consumption'::regclass
    ) THEN
        ALTER TABLE public.order_consumption
            ADD CONSTRAINT chk_order_consumption_status
            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PARTIAL', 'POSTED', 'CONFLICT'));
    END IF;
END $$;
