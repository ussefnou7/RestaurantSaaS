BEGIN;

-- Manual DB alignment for the current source migrations:
-- - folds job title schema/data into the Job module
-- - folds V8 user-permission rename into V4
-- - leaves Flyway at version 7 as the latest applied source migration

-- Rename the job title table to the new jobs table.
DO $$
BEGIN
    IF to_regclass('public.jobs') IS NULL THEN
        IF to_regclass('public.hr_job_titles') IS NOT NULL THEN
            ALTER TABLE hr_job_titles RENAME TO jobs;
        ELSIF to_regclass('public.hr_jobs') IS NOT NULL THEN
            ALTER TABLE hr_jobs RENAME TO jobs;
        END IF;
    END IF;
END $$;

-- Rename table constraints/indexes left behind by the table rename.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_hr_job_titles_tenant_code'
    ) THEN
        ALTER TABLE jobs RENAME CONSTRAINT uk_hr_job_titles_tenant_code TO uk_jobs_tenant_code;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_hr_jobs_tenant_code'
    ) THEN
        ALTER TABLE jobs RENAME CONSTRAINT uk_hr_jobs_tenant_code TO uk_jobs_tenant_code;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relname = 'idx_hr_job_titles_tenant_active'
    ) THEN
        ALTER INDEX idx_hr_job_titles_tenant_active RENAME TO idx_jobs_tenant_active;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relname = 'idx_hr_jobs_tenant_active'
    ) THEN
        ALTER INDEX idx_hr_jobs_tenant_active RENAME TO idx_jobs_tenant_active;
    END IF;
END $$;

-- Rename employee foreign-key column.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'hr_employees'
          AND column_name = 'job_title_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'hr_employees'
          AND column_name = 'job_id'
    ) THEN
        ALTER TABLE hr_employees RENAME COLUMN job_title_id TO job_id;
    END IF;
END $$;

-- Rename the employee FK constraint if it has the old generated/default name.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'hr_employees_job_title_id_fkey'
    ) THEN
        ALTER TABLE hr_employees
            RENAME CONSTRAINT hr_employees_job_title_id_fkey TO hr_employees_job_id_fkey;
    END IF;
END $$;

-- Merge old permission codes into the new JOB module permission codes while preserving ids/grants.
DO $$
DECLARE
    existing_new_id BIGINT;
    existing_old_id BIGINT;
BEGIN
    -- VIEW
    SELECT id INTO existing_old_id FROM permissions WHERE code = 'HR_JOB_TITLES_VIEW';
    SELECT id INTO existing_new_id FROM permissions WHERE code = 'JOBS_VIEW';

    IF existing_old_id IS NOT NULL AND existing_new_id IS NULL THEN
        UPDATE permissions
        SET code = 'JOBS_VIEW',
            module = 'JOB',
            name = 'View Jobs',
            description = 'View jobs.',
            type = 'ACTION',
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = existing_old_id;
    ELSIF existing_old_id IS NOT NULL THEN
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT role_id, existing_new_id
        FROM role_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        INSERT INTO user_permissions (tenant_id, user_id, permission_id)
        SELECT tenant_id, user_id, existing_new_id
        FROM user_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        DELETE FROM role_permissions WHERE permission_id = existing_old_id;
        DELETE FROM user_permissions WHERE permission_id = existing_old_id;
        DELETE FROM permissions WHERE id = existing_old_id;
    ELSE
        INSERT INTO permissions (code, module, name, description, type)
        VALUES ('JOBS_VIEW', 'JOB', 'View Jobs', 'View jobs.', 'ACTION')
        ON CONFLICT (code) DO UPDATE
        SET module = EXCLUDED.module,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            type = EXCLUDED.type,
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP;
    END IF;

    -- CREATE
    SELECT id INTO existing_old_id FROM permissions WHERE code = 'HR_JOB_TITLES_CREATE';
    SELECT id INTO existing_new_id FROM permissions WHERE code = 'JOBS_CREATE';

    IF existing_old_id IS NOT NULL AND existing_new_id IS NULL THEN
        UPDATE permissions
        SET code = 'JOBS_CREATE',
            module = 'JOB',
            name = 'Create Jobs',
            description = 'Create jobs.',
            type = 'ACTION',
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = existing_old_id;
    ELSIF existing_old_id IS NOT NULL THEN
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT role_id, existing_new_id
        FROM role_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        INSERT INTO user_permissions (tenant_id, user_id, permission_id)
        SELECT tenant_id, user_id, existing_new_id
        FROM user_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        DELETE FROM role_permissions WHERE permission_id = existing_old_id;
        DELETE FROM user_permissions WHERE permission_id = existing_old_id;
        DELETE FROM permissions WHERE id = existing_old_id;
    ELSE
        INSERT INTO permissions (code, module, name, description, type)
        VALUES ('JOBS_CREATE', 'JOB', 'Create Jobs', 'Create jobs.', 'ACTION')
        ON CONFLICT (code) DO UPDATE
        SET module = EXCLUDED.module,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            type = EXCLUDED.type,
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP;
    END IF;

    -- UPDATE
    SELECT id INTO existing_old_id FROM permissions WHERE code = 'HR_JOB_TITLES_UPDATE';
    SELECT id INTO existing_new_id FROM permissions WHERE code = 'JOBS_UPDATE';

    IF existing_old_id IS NOT NULL AND existing_new_id IS NULL THEN
        UPDATE permissions
        SET code = 'JOBS_UPDATE',
            module = 'JOB',
            name = 'Update Jobs',
            description = 'Update jobs.',
            type = 'ACTION',
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = existing_old_id;
    ELSIF existing_old_id IS NOT NULL THEN
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT role_id, existing_new_id
        FROM role_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        INSERT INTO user_permissions (tenant_id, user_id, permission_id)
        SELECT tenant_id, user_id, existing_new_id
        FROM user_permissions
        WHERE permission_id = existing_old_id
        ON CONFLICT DO NOTHING;

        DELETE FROM role_permissions WHERE permission_id = existing_old_id;
        DELETE FROM user_permissions WHERE permission_id = existing_old_id;
        DELETE FROM permissions WHERE id = existing_old_id;
    ELSE
        INSERT INTO permissions (code, module, name, description, type)
        VALUES ('JOBS_UPDATE', 'JOB', 'Update Jobs', 'Update jobs.', 'ACTION')
        ON CONFLICT (code) DO UPDATE
        SET module = EXCLUDED.module,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            type = EXCLUDED.type,
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP;
    END IF;
END $$;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('JOBS_VIEW', 'JOBS_CREATE', 'JOBS_UPDATE')
WHERE r.code IN ('OWNER', 'HR_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id)
SELECT ur.tenant_id, ur.user_id, p.id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN ('JOBS_VIEW', 'JOBS_CREATE', 'JOBS_UPDATE')
WHERE r.code IN ('OWNER', 'HR_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

-- Merge the old user-permissions management permission into the new update permission.
DO $$
DECLARE
    old_permission_id BIGINT;
    new_permission_id BIGINT;
BEGIN
    SELECT id INTO old_permission_id
    FROM permissions
    WHERE code = 'USERS_MANAGE_PERMISSIONS';

    SELECT id INTO new_permission_id
    FROM permissions
    WHERE code = 'USER_PERMISSIONS_UPDATE';

    IF old_permission_id IS NOT NULL AND new_permission_id IS NULL THEN
        UPDATE permissions
        SET code = 'USER_PERMISSIONS_UPDATE',
            module = 'USERS',
            name = 'Update User Permissions',
            description = 'Replace direct user permissions.',
            type = 'ACTION',
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = old_permission_id;
        new_permission_id := old_permission_id;
    ELSIF old_permission_id IS NOT NULL AND new_permission_id IS NOT NULL THEN
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT role_id, new_permission_id
        FROM role_permissions
        WHERE permission_id = old_permission_id
        ON CONFLICT DO NOTHING;

        INSERT INTO user_permissions (tenant_id, user_id, permission_id)
        SELECT tenant_id, user_id, new_permission_id
        FROM user_permissions
        WHERE permission_id = old_permission_id
        ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

        DELETE FROM role_permissions WHERE permission_id = old_permission_id;
        DELETE FROM user_permissions WHERE permission_id = old_permission_id;
        DELETE FROM permissions WHERE id = old_permission_id;
    ELSE
        INSERT INTO permissions (code, module, name, description, type)
        VALUES (
            'USER_PERMISSIONS_UPDATE',
            'USERS',
            'Update User Permissions',
            'Replace direct user permissions.',
            'ACTION'
        )
        ON CONFLICT (code) DO UPDATE
        SET module = EXCLUDED.module,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            type = EXCLUDED.type,
            is_active = TRUE,
            updated_at = CURRENT_TIMESTAMP;
    END IF;
END $$;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'USER_PERMISSIONS_UPDATE'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id)
SELECT ur.tenant_id, ur.user_id, p.id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'USER_PERMISSIONS_UPDATE'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

-- Keep Flyway validation aligned with this intentional rewrite:
-- V4 and V7 were edited in-place, and V8/V9 were removed from the codebase.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        UPDATE flyway_schema_history
        SET checksum = -1930987230
        WHERE version = '4';

        UPDATE flyway_schema_history
        SET checksum = -1510360029
        WHERE version = '7';

        DELETE FROM flyway_schema_history
        WHERE version IN ('8', '9');
    END IF;
END $$;

COMMIT;
