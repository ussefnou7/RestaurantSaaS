-- L039: persist failed login state so account throttling survives restarts and multiple nodes.
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_failed_login_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS locked_until timestamp without time zone;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_failed_login_attempts_non_negative'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT chk_users_failed_login_attempts_non_negative
            CHECK (failed_login_attempts >= 0);
    END IF;
END $$;
