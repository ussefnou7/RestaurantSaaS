-- =====================================================================
-- Replaces V59's two instants with a duration the POS computes itself.
--
-- WHY V59 WAS WRONG, ONE DAY OLD
--
-- V59 stored sent_to_kitchen_at and ready_at, on the assumption that one
-- order is one firing cycle. That holds for takeaway. It breaks for a
-- dine-in table, which pays once for several tickets: there is no single
-- (sent, ready) pair to store, so the two columns cannot represent the
-- thing being measured at all.
--
-- The POS is the only place that holds each ticket's kitchen in and out,
-- so it is the only place that can total them. Same reasoning as D129 --
-- the POS is the authority on what it observed and the server records it
-- verbatim -- and it retires D132's original claim that a stored duration
-- is a second copy of the truth. With N tickets behind one order there is
-- no other copy for it to disagree with.
--
-- The duration is also immune to what the instants were exposed to: it is
-- a difference of epoch milliseconds taken on one device, so a wrong
-- clock and a DST repeat both cancel out. V59's comment had to inherit
-- O34's DST limitation; this one does not.
--
-- WHY A NEW FILE RATHER THAN AN EDIT TO V59
--
-- V59 is already applied, so editing it would change a checksum Flyway
-- has recorded. With validate-on-migrate: false the edit would be
-- silently skipped on every database that already ran it -- the exact
-- trap recorded as O66. The columns are dropped and replaced here
-- instead, which runs everywhere.
--
-- Safe to drop: nothing ever wrote to them outside a QA clone. Verified
-- 0 non-null values before writing this.
-- =====================================================================

ALTER TABLE public.orders DROP COLUMN IF EXISTS sent_to_kitchen_at;
ALTER TABLE public.orders DROP COLUMN IF EXISTS ready_at;

-- Seconds, not minutes: an item that takes 90 seconds is a real
-- measurement, and rounding it to "1" or "2" at the point of storage
-- throws away precision the device already had for no gain. Reports
-- render minutes.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS kitchen_time_seconds INTEGER;

-- When the order began -- in practice the first ticket's send to the
-- kitchen, named for what it measures rather than for the event that
-- happens to mark it.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS order_started_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE public.orders
    ADD CONSTRAINT chk_orders_kitchen_time_seconds
    CHECK (kitchen_time_seconds IS NULL OR kitchen_time_seconds >= 0);

COMMENT ON COLUMN public.orders.kitchen_time_seconds IS
    'Total kitchen time across every ticket on this order, summed by the POS (D132). '
    'Null when nothing was measured -- never zero, which would read as an instant kitchen.';
COMMENT ON COLUMN public.orders.order_started_at IS
    'When the order began: the first ticket''s send to the kitchen (D132). '
    'With order_date it gives table occupancy, which is a different figure from kitchen time '
    'and must not be presented as one -- two tickets cooking at once are counted twice by the '
    'sum and once by the span.';
