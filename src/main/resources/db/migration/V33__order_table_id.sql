-- =====================================================================
-- Completes the D76 transition: Order references a real RestaurantTable via
-- table_id (FK) instead of the plain table_no string (D26). Replaces table_no
-- entirely. Existing dine-in rows are best-effort backfilled by matching the
-- old free-text table_no to a table name within the same tenant + branch;
-- unmatched historical values are dropped along with the column.
--
-- Delete safety: the FK is RESTRICT (no cascade). A table (or a section's
-- tables) can only be deleted while no order references it — enforced at the
-- service layer with a friendly 409 and backstopped by this constraint.
-- =====================================================================

ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS table_id BIGINT;

-- Best-effort backfill: match the frozen free-text table_no to a current table
-- name in the same tenant + branch. restaurant_table.name is unique per
-- (tenant, branch) so the match is unambiguous when it exists.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'orders'
          AND column_name = 'table_no'
    ) THEN
        UPDATE public.orders o
        SET table_id = rt.id
        FROM public.restaurant_table rt
        WHERE o.table_id IS NULL
          AND o.table_no IS NOT NULL
          AND rt.tenant_id = o.tenant_id
          AND rt.branch_id = o.branch_id
          AND rt.name = o.table_no;
    END IF;
END $$;

-- Swap the type guard from table_no to table_id, then add the FK + index and
-- drop the old column.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_table_no_type') THEN
        ALTER TABLE public.orders
            DROP CONSTRAINT chk_orders_table_no_type;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_table_id_type') THEN
        ALTER TABLE public.orders
            ADD CONSTRAINT chk_orders_table_id_type
            CHECK (order_type = 'DINE_IN' OR table_id IS NULL);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_table') THEN
        ALTER TABLE public.orders
            ADD CONSTRAINT fk_orders_table
            FOREIGN KEY (table_id) REFERENCES public.restaurant_table(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_table_id
    ON public.orders (table_id);

ALTER TABLE public.orders
    DROP COLUMN IF EXISTS table_no;
