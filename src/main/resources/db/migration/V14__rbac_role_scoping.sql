-- Supports RBAC role scoping: users hold their role/branch directly and roles declare branch scope.

ALTER TABLE public.roles
    ADD COLUMN IF NOT EXISTS tenant_id bigint;

ALTER TABLE public.roles
    ADD COLUMN IF NOT EXISTS is_branch_scoped boolean NOT NULL DEFAULT false;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS role_id bigint;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS branch_id bigint;

DO $$
BEGIN
    IF to_regclass('public.user_roles') IS NOT NULL THEN
        UPDATE public.users u
        SET role_id = ur.role_id,
            branch_id = ur.branch_id
        FROM public.user_roles ur
        WHERE u.id = ur.user_id
          AND u.tenant_id = ur.tenant_id
          AND u.role_id IS NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.users
        WHERE role_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot enforce users.role_id NOT NULL while users without roles exist';
    END IF;
END $$;

ALTER TABLE public.users
    ALTER COLUMN role_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'users_role_id_fkey'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'users_branch_id_fkey'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'roles_tenant_id_fkey'
          AND conrelid = 'public.roles'::regclass
    ) THEN
        ALTER TABLE public.roles
            ADD CONSTRAINT roles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_tenant_role
    ON public.users (tenant_id, role_id);

CREATE INDEX IF NOT EXISTS idx_users_tenant_branch
    ON public.users (tenant_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_roles_tenant_active
    ON public.roles (tenant_id, is_active);

DROP TABLE IF EXISTS public.user_roles CASCADE;
DROP SEQUENCE IF EXISTS public.user_roles_id_seq;
