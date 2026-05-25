CREATE TABLE hr_job_titles (
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
    CONSTRAINT uk_hr_job_titles_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE hr_employees (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    job_title_id BIGINT NOT NULL REFERENCES hr_job_titles(id),
    app_user_id BIGINT REFERENCES users(id),
    employee_code VARCHAR(100) NOT NULL,
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
    CONSTRAINT uk_hr_employees_tenant_code UNIQUE (tenant_id, employee_code)
);

CREATE TABLE hr_leave_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE hr_leave_requests (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    leave_type_id BIGINT NOT NULL REFERENCES hr_leave_types(id),
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    days_count NUMERIC(8, 2) NOT NULL,
    reason TEXT,
    status VARCHAR(30) NOT NULL,
    status_note TEXT,
    status_changed_by BIGINT,
    status_changed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT chk_hr_leave_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_hr_leave_requests_days_count CHECK (days_count > 0),
    CONSTRAINT chk_hr_leave_requests_dates CHECK (from_date <= to_date)
);

CREATE TABLE hr_salary_additions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    title VARCHAR(255) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    salary_month DATE NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT chk_hr_salary_additions_amount CHECK (amount > 0)
);

CREATE INDEX idx_hr_job_titles_tenant_active ON hr_job_titles (tenant_id, is_active);
CREATE INDEX idx_hr_employees_tenant_branch ON hr_employees (tenant_id, branch_id);
CREATE INDEX idx_hr_employees_tenant_active ON hr_employees (tenant_id, is_active);
CREATE INDEX idx_hr_employees_app_user ON hr_employees (tenant_id, app_user_id);
CREATE INDEX idx_hr_leave_requests_tenant_branch ON hr_leave_requests (tenant_id, branch_id);
CREATE INDEX idx_hr_leave_requests_employee ON hr_leave_requests (employee_id);
CREATE INDEX idx_hr_salary_additions_tenant_branch ON hr_salary_additions (tenant_id, branch_id);
CREATE INDEX idx_hr_salary_additions_employee ON hr_salary_additions (employee_id);

INSERT INTO hr_leave_types (code, name, paid)
VALUES
    ('ANNUAL', 'Annual Leave', TRUE),
    ('SICK', 'Sick Leave', TRUE),
    ('UNPAID', 'Unpaid Leave', FALSE),
    ('EMERGENCY', 'Emergency Leave', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    paid = EXCLUDED.paid,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('HR_JOB_TITLES_VIEW', 'HR', 'View Job Titles', 'View HR job titles.', 'ACTION'),
    ('HR_JOB_TITLES_CREATE', 'HR', 'Create Job Titles', 'Create HR job titles.', 'ACTION'),
    ('HR_JOB_TITLES_UPDATE', 'HR', 'Update Job Titles', 'Update HR job titles.', 'ACTION'),
    ('HR_EMPLOYEES_VIEW', 'HR', 'View Employees', 'View employees.', 'ACTION'),
    ('HR_EMPLOYEES_CREATE', 'HR', 'Create Employees', 'Create employees.', 'ACTION'),
    ('HR_EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'Update employees.', 'ACTION'),
    ('HR_LEAVES_VIEW', 'HR', 'View Leave Requests', 'View employee leave requests.', 'ACTION'),
    ('HR_LEAVES_CREATE', 'HR', 'Create Leave Requests', 'Create employee leave requests.', 'ACTION'),
    ('HR_LEAVES_UPDATE_STATUS', 'HR', 'Update Leave Status', 'Approve, reject, or cancel leave requests.', 'ACTION'),
    ('HR_SALARY_ADDITIONS_VIEW', 'HR', 'View Salary Additions', 'View employee salary additions.', 'ACTION'),
    ('HR_SALARY_ADDITIONS_CREATE', 'HR', 'Create Salary Additions', 'Create employee salary additions.', 'ACTION'),
    ('HR_SALARY_ADDITIONS_UPDATE', 'HR', 'Update Salary Additions', 'Update employee salary additions.', 'ACTION')
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
    'HR_JOB_TITLES_VIEW',
    'HR_JOB_TITLES_CREATE',
    'HR_JOB_TITLES_UPDATE',
    'HR_EMPLOYEES_VIEW',
    'HR_EMPLOYEES_CREATE',
    'HR_EMPLOYEES_UPDATE',
    'HR_LEAVES_VIEW',
    'HR_LEAVES_CREATE',
    'HR_LEAVES_UPDATE_STATUS',
    'HR_SALARY_ADDITIONS_VIEW',
    'HR_SALARY_ADDITIONS_CREATE',
    'HR_SALARY_ADDITIONS_UPDATE'
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
    'HR_JOB_TITLES_VIEW',
    'HR_JOB_TITLES_CREATE',
    'HR_JOB_TITLES_UPDATE',
    'HR_EMPLOYEES_VIEW',
    'HR_EMPLOYEES_CREATE',
    'HR_EMPLOYEES_UPDATE',
    'HR_LEAVES_VIEW',
    'HR_LEAVES_CREATE',
    'HR_LEAVES_UPDATE_STATUS',
    'HR_SALARY_ADDITIONS_VIEW',
    'HR_SALARY_ADDITIONS_CREATE',
    'HR_SALARY_ADDITIONS_UPDATE'
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
