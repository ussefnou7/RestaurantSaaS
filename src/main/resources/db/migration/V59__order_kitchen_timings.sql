-- =====================================================================
-- Kitchen timings on the order, so cook time survives the device.
--
-- The POS already records four instants per ticket -- created, sent to
-- kitchen, ready, paid -- and until now all four died locally. D130 made
-- that worse rather than better: settled tickets are deleted at shift
-- close, so every close now destroys a day of kitchen timing that can
-- never be reconstructed.
--
-- Two columns, not three. The completion instant is already carried by
-- orders.order_date, which the POS generates at payment time, so a third
-- column would be a second copy of a figure that already exists -- the
-- drift problem D119 refuses for the drawer balance and O27 is a live
-- example of.
--
--   cook time  = ready_at         - sent_to_kitchen_at
--   total time = order_date       - sent_to_kitchen_at
--
-- Total time is measured from the kitchen send, not from when the ticket
-- was opened: a dine-in ticket sits open while a table decides, so
-- counting from creation would measure the customer's deliberation rather
-- than the restaurant's speed.
--
-- WHY THESE ARE STORED AS WALL CLOCK, LIKE order_date
--
-- Same convention as every other timestamp here (D101): tenant-local wall
-- clock, no zone. Both instants come off the same device clock, so their
-- difference is right even when that clock is wrong -- a skewed device
-- misreports when a thing happened, never how long it took. The one
-- exception is a DST repeat, where an hour occurs twice and a duration
-- spanning it computes an hour out; that is the existing, documented
-- limitation in O34 and is inherited here rather than newly introduced.
--
-- NULLABLE, AND EXPECTED TO BE NULL OFTEN
--
-- A takeaway order paid without ever being sent to the kitchen has
-- neither value. An order cancelled before it was marked ready has a send
-- but no ready. Reports must treat null as "not measured" and exclude the
-- row, never as a zero -- a zero here would read as an instant kitchen.
-- =====================================================================

ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS sent_to_kitchen_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP WITHOUT TIME ZONE;

COMMENT ON COLUMN public.orders.sent_to_kitchen_at IS
    'Client-local instant the ticket was first sent to the kitchen (D132). Null when it never was.';
COMMENT ON COLUMN public.orders.ready_at IS
    'Client-local instant the ticket was marked ready (D132). Null when it never was.';

-- Reporting reads these by branch over a date window and never by value,
-- so the existing (tenant_id, branch_id, order_date) access path does the
-- work. A dedicated index is deliberately not added here: there is no
-- report yet to shape it, and an index chosen before its query is a guess
-- that still costs every write.
