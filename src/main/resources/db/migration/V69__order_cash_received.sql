-- D129/D124: retain the cash physically handed over separately from the sale total,
-- so the tender and change remain auditable without changing the net drawer movement.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS cash_received NUMERIC(18,2);
