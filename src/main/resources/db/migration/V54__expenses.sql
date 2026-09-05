-- Supports D115-D118: flat append-only expenses, tenant/global categories, and split RBAC.

CREATE TABLE IF NOT EXISTS public.expense_category (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    name VARCHAR(255) NOT NULL,
    name_ar VARCHAR(255),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by BIGINT,
    updated_by BIGINT
);

CREATE TABLE IF NOT EXISTS public.expense (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT,
    category_id BIGINT NOT NULL,
    amount NUMERIC(18,6) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(500),
    payee_name VARCHAR(255),
    payment_source VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT,
    status VARCHAR(16) NOT NULL,
    voided_at TIMESTAMP WITHOUT TIME ZONE,
    voided_by BIGINT,
    void_reason VARCHAR(500),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT chk_expense_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_expense_payment_source
        CHECK (payment_source IN ('CASH_DRAWER', 'CASH_ON_HAND', 'BANK')),
    CONSTRAINT chk_expense_source_type CHECK (source_type IN ('MANUAL')),
    CONSTRAINT chk_expense_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT chk_expense_void_fields CHECK (
        (status = 'ACTIVE' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR
        (status = 'VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)
    ),
    CONSTRAINT chk_expense_source_id CHECK (source_type <> 'MANUAL' OR source_id IS NULL)
);

-- MaterialCategory has no name uniqueness. Expenses adds the requested case-insensitive
-- uniqueness separately for global defaults and for each tenant because NULL does not conflict
-- in a normal composite unique index.
CREATE UNIQUE INDEX IF NOT EXISTS uk_expense_category_global_name
    ON public.expense_category (LOWER(name))
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_expense_category_tenant_name
    ON public.expense_category (tenant_id, LOWER(name))
    WHERE tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_expense_tenant_date
    ON public.expense (tenant_id, expense_date);

CREATE INDEX IF NOT EXISTS idx_expense_tenant_branch_date
    ON public.expense (tenant_id, branch_id, expense_date);

CREATE INDEX IF NOT EXISTS idx_expense_tenant_category
    ON public.expense (tenant_id, category_id);

INSERT INTO public.expense_category
    (tenant_id, name, name_ar, active, created_at)
VALUES
    (NULL, 'Rent', 'إيجار', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Electricity', 'كهرباء', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Water', 'مياه', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Gas', 'غاز', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Salaries & wages', 'مرتبات وأجور', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Maintenance & repairs', 'صيانة وإصلاحات', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Cleaning & consumables', 'نظافة ومستهلكات', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Marketing & advertising', 'تسويق ودعاية', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Licences & government fees', 'رخص ورسوم حكومية', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Internet & phone', 'إنترنت وتليفون', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Transport & delivery', 'مواصلات وتوصيل', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Bank & payment fees', 'رسوم بنكية ومدفوعات', TRUE, CURRENT_TIMESTAMP),
    (NULL, 'Other', 'متنوع', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO public.permissions
    (code, module, name, description, type, is_active, created_at)
VALUES
    ('EXPENSES_VIEW', 'EXPENSES', 'View Expenses',
     'View expenses and expense categories.', 'ACTION', TRUE, CURRENT_TIMESTAMP),
    ('EXPENSES_CREATE', 'EXPENSES', 'Create Expenses',
     'Create expense records.', 'ACTION', TRUE, CURRENT_TIMESTAMP),
    ('EXPENSES_VOID', 'EXPENSES', 'Void Expenses',
     'Void expense records with a reason.', 'ACTION', TRUE, CURRENT_TIMESTAMP),
    ('EXPENSES_CATEGORY_MANAGE', 'EXPENSES', 'Manage Expense Categories',
     'Create, edit, activate, and deactivate tenant expense categories.', 'ACTION', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM public.roles r
JOIN public.permissions p ON p.code IN (
    'EXPENSES_VIEW',
    'EXPENSES_CREATE',
    'EXPENSES_VOID',
    'EXPENSES_CATEGORY_MANAGE'
)
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Foreign keys are last, matching the repository's squash convention.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expense_category_tenant') THEN
        ALTER TABLE public.expense_category
            ADD CONSTRAINT fk_expense_category_tenant
            FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expense_tenant') THEN
        ALTER TABLE public.expense
            ADD CONSTRAINT fk_expense_tenant
            FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expense_branch') THEN
        ALTER TABLE public.expense
            ADD CONSTRAINT fk_expense_branch
            FOREIGN KEY (branch_id) REFERENCES public.branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expense_category') THEN
        ALTER TABLE public.expense
            ADD CONSTRAINT fk_expense_category
            FOREIGN KEY (category_id) REFERENCES public.expense_category(id);
    END IF;
END $$;
