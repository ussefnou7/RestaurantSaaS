-- Supports the RestaurantTable master-data/layout decision: table identity is backend data,
-- while occupancy/status remains out of scope.

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS name VARCHAR(255);

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS section VARCHAR(255);

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS shape VARCHAR(30) NOT NULL DEFAULT 'SQUARE';

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS pos_x NUMERIC(10,2);

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS pos_y NUMERIC(10,2);

ALTER TABLE public.restaurant_table
    ADD COLUMN IF NOT EXISTS rotation INTEGER;

UPDATE public.restaurant_table
SET name = COALESCE(NULLIF(name, ''), table_no)
WHERE name IS NULL
  AND table_no IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'restaurant_table'
          AND column_name = 'name'
          AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE public.restaurant_table
            ALTER COLUMN name SET NOT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_restaurant_table_tenant_branch_no') THEN
        ALTER TABLE public.restaurant_table
            DROP CONSTRAINT uk_restaurant_table_tenant_branch_no;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'restaurant_table'
          AND column_name = 'table_no'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.restaurant_table
            ALTER COLUMN table_no DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'restaurant_table'
          AND column_name = 'capacity'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.restaurant_table
            ALTER COLUMN capacity DROP NOT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_restaurant_table_shape') THEN
        ALTER TABLE public.restaurant_table
            ADD CONSTRAINT chk_restaurant_table_shape
            CHECK (shape IN ('ROUND', 'SQUARE', 'RECTANGLE'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_restaurant_table_tenant_branch_section
    ON public.restaurant_table (tenant_id, branch_id, section);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('TABLES_VIEW', 'TABLES', 'View Tables', 'View restaurant tables and layouts.', 'ACTION'),
    ('TABLES_MANAGE', 'TABLES', 'Manage Tables', 'Create, update, deactivate, and lay out restaurant tables.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN ('TABLES_VIEW', 'TABLES_MANAGE')
WHERE r.code IN ('OWNER', 'SYS_ADMIN', 'BRANCH_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'CASHIER'
  AND p.code IN ('TABLES_VIEW', 'TABLES_MANAGE');
