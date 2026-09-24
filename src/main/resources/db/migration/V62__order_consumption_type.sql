-- D20: waste gets its own consumption document, same machine, different ledger movement.

ALTER TABLE public.order_consumption
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'ORDINARY';

ALTER TABLE public.order_consumption
    ADD CONSTRAINT chk_order_consumption_type CHECK (type IN ('ORDINARY', 'WASTE'));

-- One open doc per warehouse PER TYPE: an ordinary and a waste doc must be able to
-- accumulate side by side, which the old index forbade.
DROP INDEX IF EXISTS uk_order_consumption_pending_per_wh;

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_consumption_pending_per_wh_type
    ON public.order_consumption (tenant_id, warehouse_id, type)
    WHERE status = 'PENDING';
