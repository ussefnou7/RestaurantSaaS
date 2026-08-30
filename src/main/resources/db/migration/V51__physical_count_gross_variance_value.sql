-- Large-variance detection previously used only the signed sum of line variance values, so a
-- count 600 short on one material and 600 over on another netted to zero and was not flagged --
-- even though 1,200 of stock was unaccounted for. Offsetting entries are the natural shape of
-- both an honest pair of miscounts and a deliberate concealment, so the control has to see the
-- gross figure.
--
-- Net stays in large_variance_value (accounting impact -- what the count did to inventory value).
-- Gross is the new column (control exposure -- how much stock moved unexplained), and it is what
-- has_large_variance is now derived from.
ALTER TABLE physical_count
    ADD COLUMN IF NOT EXISTS gross_variance_value NUMERIC(18, 6);

-- Backfill is deliberately NOT attempted for already-RECONCILED counts. Gross cannot be
-- reconstructed from the stored net total, only from the per-line variance values, and a
-- RECONCILED count returns persisted values and is never recomputed on read (D90). Historical
-- rows keep a NULL gross, which reads as "evaluated under the old net-only rule" rather than as
-- a computed zero.
COMMENT ON COLUMN physical_count.gross_variance_value IS
    'Sum of ABS(line variance x unitCostAtFreeze). Drives has_large_variance. NULL for counts '
    'reconciled before V51, which were evaluated on the net total.';
