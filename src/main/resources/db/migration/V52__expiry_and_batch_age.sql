-- D113: add shelf-life tracking without changing FIFO ordering or persisting computed age.
ALTER TABLE material
    ADD COLUMN IF NOT EXISTS expiry_tracked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE stock_balance
    ADD COLUMN IF NOT EXISTS max_age_days INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stock_batch
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

ALTER TABLE purchase_invoice_line
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

-- Existing batches predate transfers, so movement_date is exactly the date on which each batch
-- entered its current warehouse. Keep this as three statements so the backfill precedes NOT NULL.
ALTER TABLE stock_batch
    ADD COLUMN IF NOT EXISTS warehouse_entry_date DATE;

UPDATE stock_batch
SET warehouse_entry_date = movement_date
WHERE warehouse_entry_date IS NULL;

ALTER TABLE stock_batch
    ALTER COLUMN warehouse_entry_date SET NOT NULL;
