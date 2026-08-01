-- Supports D93: physical-count windows start at the ledger registration timestamp.
CREATE INDEX IF NOT EXISTS idx_inv_tx_tenant_wh_created_at
    ON public.inventory_transaction (tenant_id, warehouse_id, created_at);
