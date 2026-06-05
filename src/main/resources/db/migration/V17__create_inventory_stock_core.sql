CREATE TABLE IF NOT EXISTS stock_balance (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    material_id BIGINT NOT NULL REFERENCES material(id),
    quantity NUMERIC(18, 6) NOT NULL DEFAULT 0,
    uom_id BIGINT NOT NULL REFERENCES uom(id),
    average_cost NUMERIC(18, 6) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_stock_balance_tenant_warehouse_material UNIQUE (tenant_id, warehouse_id, material_id),
    CONSTRAINT chk_stock_balance_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_stock_balance_average_cost CHECK (average_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_balance_tenant_warehouse
    ON stock_balance (tenant_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_stock_balance_tenant_material
    ON stock_balance (tenant_id, material_id);

CREATE TABLE IF NOT EXISTS inventory_transaction (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    material_id BIGINT NOT NULL REFERENCES material(id),
    transaction_type VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    entered_quantity NUMERIC(18, 6) NOT NULL,
    entered_uom_id BIGINT NOT NULL REFERENCES uom(id),
    stock_quantity NUMERIC(18, 6) NOT NULL,
    stock_uom_id BIGINT NOT NULL REFERENCES uom(id),
    unit_cost NUMERIC(18, 6),
    total_cost NUMERIC(18, 6),
    reference_type VARCHAR(100),
    reference_id BIGINT,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_transaction_type CHECK (
        transaction_type IN (
            'OPENING_BALANCE',
            'MANUAL_IN',
            'MANUAL_OUT',
            'PURCHASE_IN',
            'WASTE',
            'TRANSFER_IN',
            'TRANSFER_OUT',
            'ORDER_CONSUMPTION',
            'RETURN_TO_SUPPLIER',
            'STOCKTAKE_ADJUSTMENT'
        )
    ),
    CONSTRAINT chk_inventory_transaction_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_inventory_transaction_entered_quantity CHECK (entered_quantity > 0),
    CONSTRAINT chk_inventory_transaction_stock_quantity CHECK (stock_quantity > 0),
    CONSTRAINT chk_inventory_transaction_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT chk_inventory_transaction_total_cost CHECK (total_cost IS NULL OR total_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_tenant_date
    ON inventory_transaction (tenant_id, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_tenant_warehouse
    ON inventory_transaction (tenant_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_tenant_material
    ON inventory_transaction (tenant_id, material_id);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_tenant_type
    ON inventory_transaction (tenant_id, transaction_type);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_reference
    ON inventory_transaction (tenant_id, reference_type, reference_id);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('INVENTORY_STOCK_VIEW', 'INVENTORY', 'View Inventory Stock', 'View stock balances and inventory transactions.', 'ACTION'),
    ('INVENTORY_STOCK_MANAGE', 'INVENTORY', 'Manage Inventory Stock', 'Create manual inventory transactions.', 'ACTION')
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
JOIN permissions p ON p.code = 'INVENTORY_STOCK_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'INVENTORY_STOCK_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('INVENTORY_STOCK_VIEW', 'INVENTORY_STOCK_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_STOCK_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_STOCK_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN ('INVENTORY_STOCK_VIEW', 'INVENTORY_STOCK_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;
