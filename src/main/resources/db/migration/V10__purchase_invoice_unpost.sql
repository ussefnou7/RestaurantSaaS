-- Purchase invoice unpost audit fields.

ALTER TABLE public.purchase_invoice
    ADD COLUMN unposted_at timestamp without time zone,
    ADD COLUMN unposted_by bigint;

CREATE INDEX idx_stock_batch_tenant_source_invoice
    ON public.stock_batch USING btree (tenant_id, source_invoice_id);
