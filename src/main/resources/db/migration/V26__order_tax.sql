-- Forward-only: existing rows keep their historical total_amount unchanged
-- (no backfill). New orders will have all three columns populated server-side.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS subtotal   NUMERIC(18, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18, 6) NOT NULL DEFAULT 0;
