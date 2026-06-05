CREATE TABLE IF NOT EXISTS warehouse (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    branch_id BIGINT REFERENCES branches(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_warehouse_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_warehouse_type CHECK (type IN ('CENTRAL', 'BRANCH', 'KITCHEN', 'FREEZER', 'BAR', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_warehouse_tenant_branch
    ON warehouse (tenant_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_warehouse_tenant_type
    ON warehouse (tenant_id, type);

CREATE INDEX IF NOT EXISTS idx_warehouse_tenant_active
    ON warehouse (tenant_id, active);
