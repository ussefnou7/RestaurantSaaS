-- =====================================================================
-- Covering index for the date-ranged, reference-scoped ledger reports
-- (Shrinkage = PHYSICAL_COUNT, Waste Analysis = WASTE_DOCUMENT).
--
-- Those reports filter (tenant_id =, reference_type =, movement_date BETWEEN).
-- The closest existing index, idx_inventory_transaction_reference
-- (tenant_id, reference_type, reference_id), matches the two equalities but its
-- third column is reference_id, so the date range degrades to a heap filter over
-- every waste/count row the tenant has ever written.
--
-- movement_date, NOT transaction_date: these are business-date reports and
-- movement_date is what the writers stamp (WasteService uses wasteDate.atStartOfDay(),
-- PhysicalCountService uses line.countedAt). transaction_date is the record timestamp
-- and would silently misbucket a backdated document.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_inv_tx_tenant_reference_movement_date
    ON public.inventory_transaction USING btree (tenant_id, reference_type, movement_date);
