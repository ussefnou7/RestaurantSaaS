CREATE TABLE IF NOT EXISTS uom (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    type VARCHAR(30) NOT NULL,
    base_code VARCHAR(100) NOT NULL,
    factor_to_base NUMERIC(18, 6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_uom_code UNIQUE (code),
    CONSTRAINT chk_uom_type CHECK (type IN ('WEIGHT', 'VOLUME', 'COUNT')),
    CONSTRAINT chk_uom_factor_to_base CHECK (factor_to_base > 0)
);

CREATE TABLE IF NOT EXISTS material_category (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_material_category_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS material_catalog (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES material_category(id),
    default_uom_id BIGINT NOT NULL REFERENCES uom(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_material_catalog_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_material_catalog_category
    ON material_catalog (category_id);

CREATE INDEX IF NOT EXISTS idx_material_catalog_default_uom
    ON material_catalog (default_uom_id);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('INVENTORY_SETUP_VIEW', 'INVENTORY', 'View Inventory Setup', 'View inventory setup reference catalog.', 'ACTION'),
    ('SYSTEM_INVENTORY_CATALOG_MANAGE', 'SYSTEM', 'Manage Inventory Catalog', 'Manage global inventory catalog records.', 'ACTION')
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
JOIN permissions p ON p.code = 'INVENTORY_SETUP_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('INVENTORY_SETUP_VIEW', 'SYSTEM_INVENTORY_CATALOG_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_SETUP_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN ('INVENTORY_SETUP_VIEW', 'SYSTEM_INVENTORY_CATALOG_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO uom (code, name, symbol, type, base_code, factor_to_base, sort_order)
VALUES
    ('GRAM', 'Gram', 'g', 'WEIGHT', 'GRAM', 1, 10),
    ('KG', 'Kilogram', 'kg', 'WEIGHT', 'GRAM', 1000, 20),
    ('ML', 'Milliliter', 'ml', 'VOLUME', 'ML', 1, 30),
    ('LITER', 'Liter', 'L', 'VOLUME', 'ML', 1000, 40),
    ('PCS', 'Piece', 'pcs', 'COUNT', 'PCS', 1, 50)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    symbol = EXCLUDED.symbol,
    type = EXCLUDED.type,
    base_code = EXCLUDED.base_code,
    factor_to_base = EXCLUDED.factor_to_base,
    active = TRUE,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO material_category (code, name, sort_order)
VALUES
    ('VEGETABLES', 'Vegetables', 10),
    ('MEAT', 'Meat', 20),
    ('CHICKEN', 'Chicken', 30),
    ('SEAFOOD', 'Seafood', 40),
    ('DAIRY', 'Dairy', 50),
    ('BAKERY', 'Bakery', 60),
    ('SAUCES', 'Sauces', 70),
    ('SPICES', 'Spices', 80),
    ('PACKAGING', 'Packaging', 90),
    ('CLEANING', 'Cleaning', 100),
    ('DRINKS', 'Drinks', 110)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    active = TRUE,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO material_catalog (code, name, category_id, default_uom_id, sort_order)
SELECT material.code, material.name, category.id, uom.id, material.sort_order
FROM (
    VALUES
        ('TOMATO', 'Tomato', 'VEGETABLES', 'GRAM', 10),
        ('POTATO', 'Potato', 'VEGETABLES', 'GRAM', 20),
        ('ONION', 'Onion', 'VEGETABLES', 'GRAM', 30),
        ('LETTUCE', 'Lettuce', 'VEGETABLES', 'GRAM', 40),
        ('CHICKEN_BREAST', 'Chicken Breast', 'CHICKEN', 'GRAM', 50),
        ('BEEF', 'Beef', 'MEAT', 'GRAM', 60),
        ('CHEDDAR_CHEESE', 'Cheddar Cheese', 'DAIRY', 'GRAM', 70),
        ('BURGER_BREAD', 'Burger Bread', 'BAKERY', 'PCS', 80),
        ('COOKING_OIL', 'Cooking Oil', 'SAUCES', 'ML', 90),
        ('PAPER_CUP', 'Paper Cup', 'PACKAGING', 'PCS', 100),
        ('TAKEAWAY_BAG', 'Takeaway Bag', 'PACKAGING', 'PCS', 110)
) AS material(code, name, category_code, uom_code, sort_order)
JOIN material_category category ON category.code = material.category_code
JOIN uom ON uom.code = material.uom_code
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    category_id = EXCLUDED.category_id,
    default_uom_id = EXCLUDED.default_uom_id,
    active = TRUE,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;
