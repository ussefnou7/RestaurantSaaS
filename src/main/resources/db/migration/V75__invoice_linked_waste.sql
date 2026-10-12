-- Invoice-linked waste preserves the exact purchase batch and its cost.
-- Historical waste remains readable; the EXPIRED source requirement is enforced on new writes
-- at COMPLETE/POST only, so existing drafts are not stranded. No backfill: a historical
-- document genuinely has no source.
ALTER TABLE waste_document ADD COLUMN IF NOT EXISTS invoice_id BIGINT;
ALTER TABLE waste_line ADD COLUMN IF NOT EXISTS invoice_line_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_waste_document_invoice ON waste_document(invoice_id);
-- Serves the per-line batch guard (WasteService.assertSourceBatchesCoverLines).
CREATE INDEX IF NOT EXISTS idx_waste_line_invoice_line ON waste_line(invoice_line_id);

-- ADD CONSTRAINT has no IF NOT EXISTS in Postgres; guard so a partial apply can be re-run.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_waste_document_invoice') THEN
        ALTER TABLE waste_document ADD CONSTRAINT fk_waste_document_invoice
            FOREIGN KEY (invoice_id) REFERENCES purchase_invoice(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_waste_line_invoice_line') THEN
        ALTER TABLE waste_line ADD CONSTRAINT fk_waste_line_invoice_line
            FOREIGN KEY (invoice_line_id) REFERENCES purchase_invoice_line(id);
    END IF;
END $$;
