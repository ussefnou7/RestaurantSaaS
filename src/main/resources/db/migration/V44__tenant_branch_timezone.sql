-- =====================================================================
-- D101 — the tenant's wall clock becomes explicit.
--
-- Every LocalDateTime column in this schema already stores wall-clock time.
-- Until now that clock was the JVM's, so which wall clock a row recorded
-- depended on where the server happened to sit. These two columns make it a
-- property of the tenant instead.
--
-- IANA zone id ('Africa/Cairo'), never a numeric offset: an offset cannot be
-- validated, carries no region identity, and silently encodes a DST choice the
-- writer never made. ZoneId.of(...) on the write path is the real validator;
-- the CHECK here only stops the empty string that NOT NULL alone permits.
--
-- The finished tenants.timezone column carries NO DEFAULT. A default is a
-- silent runtime fallback and D101 decision 3 forbids one — a tenant without a
-- zone must fail loudly rather than quietly become Cairo. The backfill below is
-- a historical statement about rows that already exist (those tenants ARE
-- Egyptian), not a fallback for rows written later.
-- =====================================================================

ALTER TABLE public.tenants ADD COLUMN IF NOT EXISTS timezone varchar(64);

UPDATE public.tenants SET timezone = 'Africa/Cairo' WHERE timezone IS NULL;

ALTER TABLE public.tenants ALTER COLUMN timezone SET NOT NULL;

-- No default was used for the backfill (the UPDATE above did the work), but drop
-- unconditionally so the finished state is asserted rather than assumed. No-op
-- when absent.
ALTER TABLE public.tenants ALTER COLUMN timezone DROP DEFAULT;

-- Nullable by design: null means "inherit the tenant's zone". Resolution is
-- branch.timezone -> tenant.timezone with no third fallback.
ALTER TABLE public.branches ADD COLUMN IF NOT EXISTS timezone varchar(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_tenants_timezone_not_blank'
          AND conrelid = 'public.tenants'::regclass
    ) THEN
        ALTER TABLE public.tenants
            ADD CONSTRAINT chk_tenants_timezone_not_blank
            CHECK (btrim(timezone) <> '');
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_branches_timezone_not_blank'
          AND conrelid = 'public.branches'::regclass
    ) THEN
        ALTER TABLE public.branches
            ADD CONSTRAINT chk_branches_timezone_not_blank
            CHECK (timezone IS NULL OR btrim(timezone) <> '');
    END IF;
END $$;
