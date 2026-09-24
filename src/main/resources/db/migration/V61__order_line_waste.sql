-- =====================================================================
-- D20, finally implemented: an item cooked and then taken off the order
-- is waste, and the stock it consumed has to leave the ledger.
--
-- Until now nothing handled it. Order status is final-only (COMPLETE or
-- CANCELLED), consumption runs on COMPLETE and a CANCELLED order consumes
-- nothing, so a grilled chicken the customer sent back was never deducted:
-- the balance still counted it and the loss appeared in no report. D20
-- decided the rule long ago and PROJECT.md records that no code carried it
-- out.
--
-- THE WASTED ITEM STAYS ON THE ORDER
--
-- It is a real order line carrying its product and quantity, marked with a
-- type. That keeps the audit trail where an investigator looks -- the order
-- shows what was made for this table and binned -- and it means
-- order_consumption_line needs no change at all: it still points at one
-- order line, and only the routing differs.
--
-- IT KEEPS ITS PRICE, AND IS KEPT OUT OF THE TOTAL
--
-- The line carries the menu price it had at that moment. Two different
-- figures are wanted from waste and only one of them already exists:
--
--   * what it COST -- the materials it consumed, valued by the ledger at
--     FIFO/average (D1, D11). Already captured by the waste posting.
--   * what it was WORTH -- the money that never arrived because the dish
--     was binned instead of sold. That is this price, and nothing else
--     records it. Menu prices move, so it has to be frozen on the line at
--     the time rather than looked up later from the product.
--
-- Zeroing the line was considered and rejected for exactly that reason: it
-- would have kept D129's reconciliation a plain sum of every line, at the
-- cost of destroying the figure the feature exists to produce.
--
-- CONSEQUENCE, STATED SO IT IS NOT DISCOVERED IN A REPORT
--
-- The money on a WASTE line is real and must never reach a sales figure.
-- Two places have to exclude it, and they are the whole blast radius:
--
--   1. D129's reconciliation in OrderService, which compares the sum of the
--      lines against the header -- it now sums SALE lines only, because the
--      customer paid for those and only those.
--   2. The sales-by-product report, the one query that reads order_line
--      directly. Without the filter a binned dish counts as a sale.
--
-- Everything else reaches lines through the order, where showing them is
-- the point.
-- =====================================================================

ALTER TABLE public.order_line
    ADD COLUMN IF NOT EXISTS line_type VARCHAR(20) NOT NULL DEFAULT 'SALE';

ALTER TABLE public.order_line
    ADD CONSTRAINT chk_order_line_type
    CHECK (line_type IN ('SALE', 'WASTE'));

-- Why it was wasted, in D20's own vocabulary. Null on a sale line.
-- Only the two cooked stages can appear: an item cancelled before the
-- kitchen started it consumed nothing, so it is not waste and never
-- reaches this table at all.
ALTER TABLE public.order_line
    ADD COLUMN IF NOT EXISTS waste_stage VARCHAR(40);

ALTER TABLE public.order_line
    ADD CONSTRAINT chk_order_line_waste_stage CHECK (
        (line_type = 'SALE'  AND waste_stage IS NULL)
        OR
        (line_type = 'WASTE' AND waste_stage IN ('IN_KITCHEN_COOKED', 'AFTER_DONE'))
    );

-- No money constraint on either kind. A waste line's price is a real
-- figure that a loss report reads, and D129 deliberately logs rather than
-- rejects money that does not reconcile -- a sale is already paid by the
-- time it arrives, so nothing here may refuse it.

-- Both readers that must exclude waste filter on this column, and the
-- sales-by-product report groups over a date range -- so the index carries
-- the discriminator for the rare rows rather than the common ones.
CREATE INDEX IF NOT EXISTS idx_order_line_type
    ON public.order_line (line_type)
    WHERE line_type <> 'SALE';

COMMENT ON COLUMN public.order_line.line_type IS
    'SALE or WASTE (D20). A WASTE line was cooked and then removed: it consumes stock through the '
    'waste consumption document, and keeps its menu price so the loss can be valued -- but is '
    'excluded from the order total and from every sales figure.';
