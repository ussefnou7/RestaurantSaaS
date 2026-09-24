-- =====================================================================
-- D128: media attachments. One generic link table, cardinality enforced
-- by the database, deletion permanent.
--
-- Three tables carry three different things and are deliberately not
-- collapsed into fewer:
--
--   media_file     the bytes' identity. Knows nothing about who owns it.
--   media_variant  one row per stored rendition, including the original.
--   media_link     the ownership edge, polymorphic by owner_type.
--
-- media_variant is a table rather than four nullable columns on
-- media_file because a future rendition size is a certainty, not a
-- possibility. When one is added the backfill has to know which files
-- already have it; nullable columns answer that only by accident.
--
-- WHAT THE POLYMORPHIC LINK COSTS, STATED HERE RATHER THAN DISCOVERED
--
-- owner_id carries no foreign key, so the database cannot enforce that
-- it names a live row. That is the price of letting any record become
-- attachable without a migration. It is bought back in two places: the
-- partial unique index below, and MediaOwnerResolver.exists() in the
-- application, which is called with the REQUEST's tenant so an owner_id
-- belonging to another tenant fails as not-found.
--
-- This migration ships two purposes. PURCHASE_INVOICE_ATTACHMENT and
-- EXPENSE_RECEIPT are named in D128 and deliberately absent: a purpose
-- whose owner module is not built is dormant schema with no producer.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. media_file -- the bytes' identity.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.media_file (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    width               INT,
    height              INT,
    checksum_sha256     VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    CONSTRAINT chk_media_file_size_positive CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_media_file_tenant ON public.media_file (tenant_id);

COMMENT ON COLUMN public.media_file.original_filename IS
    'As uploaded, for display only. Never used to build a storage key (D128 §7).';
COMMENT ON COLUMN public.media_file.width IS
    'NULL for content types that have no pixel dimensions.';
COMMENT ON COLUMN public.media_file.checksum_sha256 IS
    'SHA-256 of the ORIGINAL bytes. Served as the ETag (D128 §8). Nothing reads it for
     deduplication -- its presence is not an implemented dedup feature (D128 §9).';

-- ---------------------------------------------------------------------
-- 2. media_variant -- one row per stored rendition.
--
-- A pure child of media_file with no independent lifecycle, so it carries
-- no tenant_id and no audit columns: it is never queried except through
-- its parent, whose tenant_id already confines it.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.media_variant (
    id              BIGSERIAL PRIMARY KEY,
    media_file_id   BIGINT       NOT NULL,
    variant         VARCHAR(20)  NOT NULL,
    storage_key     VARCHAR(512) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    width           INT,
    height          INT,
    size_bytes      BIGINT       NOT NULL,
    CONSTRAINT fk_media_variant_file FOREIGN KEY (media_file_id)
        REFERENCES public.media_file (id) ON DELETE CASCADE,
    CONSTRAINT uk_media_variant_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_media_variant_file_variant UNIQUE (media_file_id, variant),
    CONSTRAINT chk_media_variant_variant CHECK (variant IN ('ORIGINAL', 'LARGE', 'MEDIUM', 'THUMB'))
);

COMMENT ON COLUMN public.media_variant.storage_key IS
    'Provider-independent key, layout t{tenantId}/{ownerType}/{uuid}/{variant}.{ext} (D128 §7).
     No URL is ever stored -- every URL is built at read time, so changing storage provider is a
     tree copy rather than a data migration.';

-- ---------------------------------------------------------------------
-- 3. media_link -- the generic ownership edge.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.media_link (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL,
    media_file_id   BIGINT      NOT NULL,
    owner_type      VARCHAR(40) NOT NULL,
    owner_id        BIGINT      NOT NULL,
    purpose         VARCHAR(60) NOT NULL,
    sort_order      INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT fk_media_link_file FOREIGN KEY (media_file_id)
        REFERENCES public.media_file (id),
    CONSTRAINT chk_media_link_owner_type CHECK (owner_type IN ('PRODUCT', 'EMPLOYEE')),
    CONSTRAINT chk_media_link_purpose CHECK (purpose IN ('PRODUCT_IMAGE', 'EMPLOYEE_PHOTO'))
);

-- The read every owning screen performs: "what is attached to this row".
CREATE INDEX IF NOT EXISTS idx_media_link_owner
    ON public.media_link (owner_type, owner_id);

CREATE INDEX IF NOT EXISTS idx_media_link_file
    ON public.media_link (media_file_id);

-- ---------------------------------------------------------------------
-- 4. Cardinality is a database constraint, not a service check (D128 §3).
--
-- A partial unique index gives single-valued and multi-valued purposes
-- different guarantees inside one table. Both purposes shipping today are
-- single-valued, so the predicate currently covers every row -- it is
-- still written as a partial index because that is the shape that stays
-- correct when the first multi-valued purpose arrives, and rewriting an
-- index later is how the guarantee gets dropped by accident.
--
-- Uploading a second file to a single-valued purpose REPLACES; the
-- service deletes the incumbent link in the same transaction rather than
-- letting this index raise.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_media_link_single
    ON public.media_link (owner_type, owner_id, purpose)
    WHERE purpose IN ('PRODUCT_IMAGE', 'EMPLOYEE_PHOTO');

-- ---------------------------------------------------------------------
-- 5. media_deletion_queue -- the transactional handle on byte deletion.
--
-- Deletion commits first and removes bytes second (D128 §5): the storage
-- keys are written here in the same transaction as the row deletion, and
-- a job drains them afterwards. Deleting bytes inside the transaction
-- means a rollback destroys a file whose row came back.
--
-- It is transactional by being an ordinary table. That is the entire
-- mechanism and it needs no more.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.media_deletion_queue (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    enqueued_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_deletion_queue_enqueued
    ON public.media_deletion_queue (enqueued_at);
