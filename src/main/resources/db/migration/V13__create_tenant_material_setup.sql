ALTER TABLE material_category
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);

ALTER TABLE material_category
    DROP CONSTRAINT IF EXISTS uk_material_category_code;

CREATE UNIQUE INDEX IF NOT EXISTS ux_material_category_global_code
    ON material_category (code)
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_material_category_tenant_code
    ON material_category (tenant_id, code)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_material_category_tenant
    ON material_category (tenant_id);

CREATE TABLE IF NOT EXISTS material (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    catalog_id BIGINT REFERENCES material_catalog(id),
    category_id BIGINT NOT NULL REFERENCES material_category(id),
    default_uom_id BIGINT NOT NULL REFERENCES uom(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    minimum_stock_level NUMERIC(18, 6) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_material_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_material_minimum_stock_level CHECK (minimum_stock_level >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_material_tenant_catalog
    ON material (tenant_id, catalog_id)
    WHERE catalog_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_material_tenant_category
    ON material (tenant_id, category_id);

CREATE INDEX IF NOT EXISTS idx_material_tenant_default_uom
    ON material (tenant_id, default_uom_id);

CREATE INDEX IF NOT EXISTS idx_material_tenant_active
    ON material (tenant_id, active);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('INVENTORY_SETUP_MANAGE', 'INVENTORY', 'Manage Inventory Setup', 'Create and update tenant inventory setup records.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'INVENTORY_SETUP_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'INVENTORY_SETUP_MANAGE'
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_SETUP_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_SETUP_MANAGE'
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;
