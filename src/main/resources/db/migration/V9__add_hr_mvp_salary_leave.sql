CREATE TABLE IF NOT EXISTS employee_salaries (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    salary_amount NUMERIC(14, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_employee_salaries_amount CHECK (salary_amount > 0),
    CONSTRAINT chk_employee_salaries_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_salaries_active
    ON employee_salaries (tenant_id, employee_id)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_employee_salaries_employee
    ON employee_salaries (tenant_id, employee_id);

CREATE TABLE IF NOT EXISTS employee_salary_adjustments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    adjustment_date DATE NOT NULL,
    reason TEXT,
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_employee_salary_adjustments_type CHECK (type IN ('ADDITION', 'DEDUCTION')),
    CONSTRAINT chk_employee_salary_adjustments_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_employee_salary_adjustments_employee
    ON employee_salary_adjustments (tenant_id, employee_id, adjustment_date);

CREATE TABLE IF NOT EXISTS hr_leave_type (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name_en VARCHAR(255),
    name_ar VARCHAR(255),
    description_en TEXT,
    description_ar TEXT,
    default_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_hr_leave_type_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_hr_leave_type_name CHECK (
        NULLIF(BTRIM(COALESCE(name_en, '')), '') IS NOT NULL
        OR NULLIF(BTRIM(COALESCE(name_ar, '')), '') IS NOT NULL
    ),
    CONSTRAINT chk_hr_leave_type_default_days CHECK (default_days >= 0)
);

CREATE TABLE IF NOT EXISTS employee_leave_balances (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    leave_type_id BIGINT NOT NULL REFERENCES hr_leave_type(id),
    year INTEGER NOT NULL,
    opening_balance NUMERIC(8, 2) NOT NULL DEFAULT 0,
    assigned_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    used_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    remaining_days NUMERIC(8, 2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_employee_leave_balances_employee_type_year UNIQUE (tenant_id, employee_id, leave_type_id, year),
    CONSTRAINT chk_employee_leave_balances_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_employee_leave_balances_days CHECK (
        opening_balance >= 0
        AND assigned_days >= 0
        AND used_days >= 0
        AND remaining_days >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_employee_leave_balances_employee_year
    ON employee_leave_balances (tenant_id, employee_id, year);

CREATE TABLE IF NOT EXISTS hr_leave_request (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES hr_employees(id),
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    leave_type_id BIGINT NOT NULL REFERENCES hr_leave_type(id),
    leave_balance_id BIGINT NOT NULL REFERENCES employee_leave_balances(id),
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    days_count NUMERIC(8, 2) NOT NULL,
    reason TEXT,
    status VARCHAR(30) NOT NULL,
    notes TEXT,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_hr_leave_request_status CHECK (status IN ('APPROVED', 'CANCELLED')),
    CONSTRAINT chk_hr_leave_request_days_count CHECK (days_count > 0),
    CONSTRAINT chk_hr_leave_request_dates CHECK (from_date <= to_date)
);

CREATE INDEX IF NOT EXISTS idx_hr_leave_request_employee
    ON hr_leave_request (tenant_id, employee_id, id);

CREATE INDEX IF NOT EXISTS idx_hr_leave_request_balance
    ON hr_leave_request (leave_balance_id);
