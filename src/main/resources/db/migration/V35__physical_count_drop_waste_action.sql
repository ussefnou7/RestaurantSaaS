-- =====================================================================
-- Physical Count: a count produces exactly one kind of movement.
--
-- The per-line "adjustment or waste" choice is gone. Every variance now posts a
-- COUNT_ADJUSTMENT and the direction carries the meaning: a shortage leaves as OUT
-- (FIFO-consumed at open-batch cost), a surplus enters as IN (opening a batch at the
-- current average). A count never writes a waste-typed row or a waste document, so
-- physical_count_line.waste_transaction_id can never be populated again.
--
-- Historical rows are NOT backfilled or migrated. The waste transactions themselves stay
-- untouched in inventory_transaction and remain traceable to their count through
-- reference_type = 'PHYSICAL_COUNT' + reference_id, so dropping the per-line column loses
-- no ledger history.
--
-- action_taken keeps its legacy 'WASTE' value for rows reconciled before this migration —
-- it records the outcome, not the choice, and is still written (PENDING / NO_DIFFERENCE /
-- ADJUSTMENT).
-- =====================================================================

ALTER TABLE public.physical_count_line
    DROP COLUMN IF EXISTS waste_transaction_id;
