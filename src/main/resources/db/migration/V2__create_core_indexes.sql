CREATE INDEX idx_branches_tenant_id ON branches (tenant_id);
CREATE INDEX idx_branches_tenant_active ON branches (tenant_id, is_active);
CREATE INDEX idx_users_tenant_id ON users (tenant_id);
CREATE INDEX idx_users_tenant_status ON users (tenant_id, status);
CREATE INDEX idx_users_tenant_username ON users (tenant_id, username);
CREATE INDEX idx_users_tenant_phone ON users (tenant_id, phone);
