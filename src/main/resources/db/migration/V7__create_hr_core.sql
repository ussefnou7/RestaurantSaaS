CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_jobs_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE hr_employees (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    job_id BIGINT NOT NULL REFERENCES jobs(id),
    user_id BIGINT REFERENCES users(id),
    code VARCHAR(100) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    national_id VARCHAR(100),
    address TEXT,
    hire_date DATE NOT NULL,
    salary NUMERIC(14, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_hr_employees_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_jobs_tenant_active ON jobs (tenant_id, is_active);
CREATE INDEX idx_hr_employees_tenant_branch ON hr_employees (tenant_id, branch_id);
CREATE INDEX idx_hr_employees_tenant_active ON hr_employees (tenant_id, is_active);
CREATE INDEX idx_hr_employees_user ON hr_employees (tenant_id, user_id);

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('JOBS_VIEW', 'JOB', 'View Jobs', 'View jobs.', 'ACTION'),
    ('JOBS_CREATE', 'JOB', 'Create Jobs', 'Create jobs.', 'ACTION'),
    ('JOBS_UPDATE', 'JOB', 'Update Jobs', 'Update jobs.', 'ACTION'),
    ('HR_EMPLOYEES_VIEW', 'HR', 'View Employees', 'View employees.', 'ACTION'),
    ('HR_EMPLOYEES_CREATE', 'HR', 'Create Employees', 'Create employees.', 'ACTION'),
    ('HR_EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'Update employees.', 'ACTION'),
    ('HR_LEAVES_VIEW', 'HR', 'View Leave Requests', 'View employee leave requests.', 'ACTION'),
    ('HR_LEAVES_CREATE', 'HR', 'Create Leave Requests', 'Create employee leave requests.', 'ACTION'),
    ('HR_LEAVES_UPDATE_STATUS', 'HR', 'Update Leave Status', 'Approve, reject, or cancel leave requests.', 'ACTION')
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
JOIN permissions p ON p.code IN (
    'JOBS_VIEW',
    'JOBS_CREATE',
    'JOBS_UPDATE',
    'HR_EMPLOYEES_VIEW',
    'HR_EMPLOYEES_CREATE',
    'HR_EMPLOYEES_UPDATE',
    'HR_LEAVES_VIEW',
    'HR_LEAVES_CREATE',
    'HR_LEAVES_UPDATE_STATUS'
)
WHERE r.code IN ('OWNER', 'HR_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('HR_LEAVES_VIEW', 'HR_LEAVES_CREATE')
WHERE r.code = 'BRANCH_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id)
SELECT ur.tenant_id, ur.user_id, p.id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN (
    'JOBS_VIEW',
    'JOBS_CREATE',
    'JOBS_UPDATE',
    'HR_EMPLOYEES_VIEW',
    'HR_EMPLOYEES_CREATE',
    'HR_EMPLOYEES_UPDATE',
    'HR_LEAVES_VIEW',
    'HR_LEAVES_CREATE',
    'HR_LEAVES_UPDATE_STATUS'
)
WHERE r.code IN ('OWNER', 'HR_MANAGER')
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id)
SELECT ur.tenant_id, ur.user_id, p.id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN permissions p ON p.code IN ('HR_LEAVES_VIEW', 'HR_LEAVES_CREATE')
WHERE r.code = 'BRANCH_MANAGER'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;
