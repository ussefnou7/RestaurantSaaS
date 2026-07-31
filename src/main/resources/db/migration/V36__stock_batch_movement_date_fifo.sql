-- Supports D10: FIFO follows the business movement date, with id as a deterministic tiebreaker.
DROP INDEX IF EXISTS public.idx_stock_batch_open_fifo;

CREATE INDEX IF NOT EXISTS idx_stock_batch_open_fifo
    ON public.stock_batch (stock_balance_id, status, movement_date, id);

CREATE INDEX IF NOT EXISTS idx_stock_batch_balance_movement_date
    ON public.stock_batch (stock_balance_id, movement_date, id);
