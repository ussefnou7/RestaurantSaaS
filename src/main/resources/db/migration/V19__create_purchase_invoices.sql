CREATE TABLE IF NOT EXISTS purchase_invoice (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    supplier_id BIGINT REFERENCES supplier(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    invoice_number VARCHAR(100),
    invoice_date DATE NOT NULL,
    receipt_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    subtotal NUMERIC(18, 6) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(18, 6) NOT NULL DEFAULT 0,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    posted_at TIMESTAMP,
    posted_by BIGINT,
    CONSTRAINT uk_purchase_invoice_tenant_invoice_number UNIQUE (tenant_id, invoice_number),
    CONSTRAINT chk_purchase_invoice_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    CONSTRAINT chk_purchase_invoice_payment_status CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT chk_purchase_invoice_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_purchase_invoice_discount_amount CHECK (discount_amount >= 0),
    CONSTRAINT chk_purchase_invoice_tax_amount CHECK (tax_amount >= 0),
    CONSTRAINT chk_purchase_invoice_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_purchase_invoice_paid_amount CHECK (paid_amount >= 0)
);

CREATE TABLE IF NOT EXISTS purchase_invoice_line (
    id BIGSERIAL PRIMARY KEY,
    purchase_invoice_id BIGINT NOT NULL REFERENCES purchase_invoice(id) ON DELETE CASCADE,
    material_id BIGINT NOT NULL REFERENCES material(id),
    quantity NUMERIC(18, 6) NOT NULL,
    uom_id BIGINT NOT NULL REFERENCES uom(id),
    unit_cost NUMERIC(18, 6) NOT NULL,
    line_total NUMERIC(18, 6) NOT NULL,
    notes TEXT,
    CONSTRAINT chk_purchase_invoice_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_purchase_invoice_line_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT chk_purchase_invoice_line_total CHECK (line_total >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_tenant_status
    ON purchase_invoice (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_tenant_payment_status
    ON purchase_invoice (tenant_id, payment_status);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_tenant_supplier
    ON purchase_invoice (tenant_id, supplier_id);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_tenant_warehouse
    ON purchase_invoice (tenant_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_tenant_invoice_date
    ON purchase_invoice (tenant_id, invoice_date DESC);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_line_invoice
    ON purchase_invoice_line (purchase_invoice_id);

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_line_material
    ON purchase_invoice_line (material_id);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('INVENTORY_PURCHASE_VIEW', 'INVENTORY', 'View Purchase Invoices', 'View inventory purchase invoices.', 'ACTION'),
    ('INVENTORY_PURCHASE_MANAGE', 'INVENTORY', 'Manage Purchase Invoices', 'Create, edit, post, and cancel inventory purchase invoices.', 'ACTION')
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
JOIN permissions p ON p.code = 'INVENTORY_PURCHASE_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'INVENTORY_PURCHASE_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('INVENTORY_PURCHASE_VIEW', 'INVENTORY_PURCHASE_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_PURCHASE_VIEW'
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code = 'INVENTORY_PURCHASE_MANAGE'
WHERE r.code IN ('OWNER', 'INVENTORY_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT ur.tenant_id, ur.user_id, p.id, CURRENT_TIMESTAMP
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN ('INVENTORY_PURCHASE_VIEW', 'INVENTORY_PURCHASE_MANAGE')
WHERE r.code = 'SYS_ADMIN'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;
