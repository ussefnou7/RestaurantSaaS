-- Supports short-lived, one-time POS device pairing codes.

ALTER TABLE public.device
    ADD COLUMN IF NOT EXISTS pairing_code_expires_at TIMESTAMP WITHOUT TIME ZONE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'device'
          AND column_name = 'secret_key_hash'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'device'
          AND column_name = 'pairing_code_hash'
    ) THEN
        ALTER TABLE public.device RENAME COLUMN secret_key_hash TO pairing_code_hash;
    END IF;
END $$;

ALTER TABLE public.device
    ALTER COLUMN pairing_code_hash DROP NOT NULL;

-- Give any not-yet-used legacy registration credential a short transition window.
UPDATE public.device
SET pairing_code_expires_at = CURRENT_TIMESTAMP + INTERVAL '10 minutes'
WHERE pairing_code_hash IS NOT NULL
  AND pairing_code_expires_at IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_device_secret_key_hash') THEN
        ALTER TABLE public.device RENAME CONSTRAINT uk_device_secret_key_hash TO uk_device_pairing_code_hash;
    END IF;
END $$;
