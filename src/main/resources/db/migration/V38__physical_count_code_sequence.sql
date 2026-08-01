-- Supports unique physical-count codes without restoring the removed warehouse/day creation guard.
CREATE TABLE IF NOT EXISTS public.physical_count_code_sequence (
    tenant_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    scheduled_date date NOT NULL,
    last_seq integer DEFAULT 0 NOT NULL,
    CONSTRAINT uk_physical_count_code_sequence_scope
        UNIQUE (tenant_id, warehouse_id, scheduled_date),
    CONSTRAINT chk_physical_count_code_sequence_nonnegative
        CHECK (last_seq >= 0)
);
