-- Supports D112: shared per-tenant, per-document-type, per-year document numbers.
CREATE TABLE IF NOT EXISTS public.document_sequence (
    tenant_id bigint NOT NULL,
    document_type varchar(50) NOT NULL,
    year integer NOT NULL,
    seq integer DEFAULT 0 NOT NULL,
    CONSTRAINT uk_document_sequence_scope
        UNIQUE (tenant_id, document_type, year),
    CONSTRAINT chk_document_sequence_seq_nonnegative
        CHECK (seq >= 0),
    CONSTRAINT chk_document_sequence_year_valid
        CHECK (year >= 0)
);

DROP TABLE IF EXISTS public.physical_count_code_sequence;
