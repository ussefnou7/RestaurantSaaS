ALTER TABLE purchase_invoice
    ADD COLUMN IF NOT EXISTS posted_to_inventory BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_by BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_by BIGINT,
    ADD COLUMN IF NOT EXISTS cancel_reason TEXT;

UPDATE purchase_invoice
SET status = 'COMPLETED'
WHERE status = 'POSTED';

UPDATE purchase_invoice
SET posted_to_inventory = TRUE
WHERE status = 'COMPLETED'
  AND posted_at IS NOT NULL
  AND posted_to_inventory = FALSE;

ALTER TABLE purchase_invoice DROP CONSTRAINT IF EXISTS chk_purchase_invoice_status;

ALTER TABLE purchase_invoice
    ADD CONSTRAINT chk_purchase_invoice_status
        CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED'));

CREATE TABLE IF NOT EXISTS document_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    document_type VARCHAR(50) NOT NULL,
    document_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by BIGINT,
    details TEXT,
    CONSTRAINT chk_document_history_document_type CHECK (document_type IN ('PURCHASE_INVOICE')),
    CONSTRAINT chk_document_history_action CHECK (action IN ('COMPLETE', 'CANCEL'))
);

CREATE INDEX IF NOT EXISTS idx_document_history_tenant_document
    ON document_history (tenant_id, document_type, document_id, performed_at DESC);

UPDATE permissions
SET description = 'Create, edit, complete, and cancel inventory purchase invoices.',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'INVENTORY_PURCHASE_MANAGE';
