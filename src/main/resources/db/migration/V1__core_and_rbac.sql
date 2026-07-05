-- =====================================================================
-- Core platform & RBAC: tenants, branches, users, roles, permissions
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.branches (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(100) NOT NULL,
    address text,
    phone character varying(50),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    name_en character varying(255),
    name_ar character varying(255),
    address_en text,
    address_ar text
);

CREATE TABLE public.document_history (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    document_type character varying(50) NOT NULL,
    document_id bigint NOT NULL,
    action character varying(30) NOT NULL,
    performed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    performed_by bigint,
    details text,
    CONSTRAINT chk_document_history_action CHECK (((action)::text = ANY ((ARRAY['COMPLETE'::character varying, 'CANCEL'::character varying])::text[]))),
    CONSTRAINT chk_document_history_document_type CHECK (((document_type)::text = 'PURCHASE_INVOICE'::text))
);

CREATE TABLE public.jobs (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(100) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    name_en character varying(255),
    name_ar character varying(255),
    description_en text,
    description_ar text
);

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    code character varying(150) NOT NULL,
    module character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    type character varying(30) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    name_en character varying(255),
    name_ar character varying(255),
    description_en text,
    description_ar text,
    CONSTRAINT chk_permissions_type CHECK (((type)::text = ANY ((ARRAY['ACCESS'::character varying, 'ACTION'::character varying])::text[])))
);

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint
);

CREATE TABLE public.roles (
    id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    name_en character varying(255),
    name_ar character varying(255),
    description_en text,
    description_ar text
);

CREATE TABLE public.tenants (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(100) NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_tenants_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DELETED'::character varying])::text[])))
);

CREATE TABLE public.user_permissions (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.user_roles (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    branch_id bigint,
    scope character varying(30) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_user_roles_scope CHECK (((scope)::text = ANY ((ARRAY['TENANT'::character varying, 'BRANCH'::character varying, 'OWN'::character varying])::text[])))
);

CREATE TABLE public.users (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    full_name character varying(255) NOT NULL,
    username character varying(100) NOT NULL,
    email character varying(255),
    phone character varying(50),
    password_hash text NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_login_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'LOCKED'::character varying, 'DELETED'::character varying])::text[])))
);

CREATE SEQUENCE public.branches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.document_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.tenants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.user_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.branches_id_seq OWNED BY public.branches.id;

ALTER SEQUENCE public.document_history_id_seq OWNED BY public.document_history.id;

ALTER SEQUENCE public.jobs_id_seq OWNED BY public.jobs.id;

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;

ALTER SEQUENCE public.tenants_id_seq OWNED BY public.tenants.id;

ALTER SEQUENCE public.user_permissions_id_seq OWNED BY public.user_permissions.id;

ALTER SEQUENCE public.user_roles_id_seq OWNED BY public.user_roles.id;

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;

ALTER TABLE ONLY public.branches ALTER COLUMN id SET DEFAULT nextval('public.branches_id_seq'::regclass);

ALTER TABLE ONLY public.document_history ALTER COLUMN id SET DEFAULT nextval('public.document_history_id_seq'::regclass);

ALTER TABLE ONLY public.jobs ALTER COLUMN id SET DEFAULT nextval('public.jobs_id_seq'::regclass);

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);

ALTER TABLE ONLY public.tenants ALTER COLUMN id SET DEFAULT nextval('public.tenants_id_seq'::regclass);

ALTER TABLE ONLY public.user_permissions ALTER COLUMN id SET DEFAULT nextval('public.user_permissions_id_seq'::regclass);

ALTER TABLE ONLY public.user_roles ALTER COLUMN id SET DEFAULT nextval('public.user_roles_id_seq'::regclass);

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.document_history
    ADD CONSTRAINT document_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_code_key UNIQUE (code);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_code_key UNIQUE (code);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_code_key UNIQUE (code);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT uk_branches_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT uk_jobs_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT uk_user_permissions_tenant_user_permission UNIQUE (tenant_id, user_id, permission_id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT uk_user_roles_tenant_user UNIQUE (tenant_id, user_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);

ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_branches_tenant_active ON public.branches USING btree (tenant_id, is_active);

CREATE INDEX idx_branches_tenant_id ON public.branches USING btree (tenant_id);

CREATE INDEX idx_document_history_tenant_document ON public.document_history USING btree (tenant_id, document_type, document_id, performed_at DESC);

CREATE INDEX idx_jobs_tenant_active ON public.jobs USING btree (tenant_id, is_active);

CREATE INDEX idx_permissions_module ON public.permissions USING btree (module);

CREATE INDEX idx_role_permissions_permission_id ON public.role_permissions USING btree (permission_id);

CREATE INDEX idx_user_permissions_permission_id ON public.user_permissions USING btree (permission_id);

CREATE INDEX idx_user_permissions_tenant_user ON public.user_permissions USING btree (tenant_id, user_id);

CREATE INDEX idx_user_roles_tenant_role ON public.user_roles USING btree (tenant_id, role_id);

CREATE INDEX idx_users_tenant_id ON public.users USING btree (tenant_id);

CREATE INDEX idx_users_tenant_phone ON public.users USING btree (tenant_id, phone);

CREATE INDEX idx_users_tenant_status ON public.users USING btree (tenant_id, status);

CREATE INDEX idx_users_tenant_username ON public.users USING btree (tenant_id, username);

-- =====================================================================
-- Seed: Roles, Permissions, Role-Permission mappings (idempotent)
-- =====================================================================
INSERT INTO roles (code, name, description)
VALUES
    ('OWNER', 'Owner', 'Full tenant owner role with all permissions.'),
    ('BRANCH_MANAGER', 'Branch Manager', 'Manages branch operations, orders, inventory, users, and reports.'),
    ('CASHIER', 'Cashier', 'Handles orders and shifts.'),
    ('ACCOUNTANT', 'Accountant', 'Reviews accounting, payments, cash movement, reports, and shifts.'),
    ('HR_MANAGER', 'HR Manager', 'Manages users and employee records.'),
    ('INVENTORY_MANAGER', 'Inventory Manager', 'Manages products and inventory documents.')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('USERS_ACCESS', 'USERS', 'Users Access', 'Access users module.', 'ACCESS'),
    ('USERS_VIEW', 'USERS', 'View Users', 'View tenant users.', 'ACTION'),
    ('USERS_CREATE', 'USERS', 'Create Users', 'Create tenant users.', 'ACTION'),
    ('USERS_UPDATE', 'USERS', 'Update Users', 'Update tenant users.', 'ACTION'),
    ('USERS_DELETE', 'USERS', 'Delete Users', 'Delete tenant users.', 'ACTION'),
    ('USERS_CHANGE_STATUS', 'USERS', 'Change User Status', 'Activate, deactivate, lock, or delete tenant users.', 'ACTION'),
    ('USERS_ASSIGN_ROLE', 'USERS', 'Assign User Role', 'Assign or change a user base role.', 'ACTION'),
    ('USER_PERMISSIONS_UPDATE', 'USERS', 'Update User Permissions', 'Replace direct user permissions.', 'ACTION'),
    ('BRANCHES_ACCESS', 'BRANCHES', 'Branches Access', 'Access branches module.', 'ACCESS'),
    ('BRANCHES_VIEW', 'BRANCHES', 'View Branches', 'View branches.', 'ACTION'),
    ('BRANCHES_CREATE', 'BRANCHES', 'Create Branches', 'Create branches.', 'ACTION'),
    ('BRANCHES_UPDATE', 'BRANCHES', 'Update Branches', 'Update branches.', 'ACTION'),
    ('BRANCHES_CHANGE_STATUS', 'BRANCHES', 'Change Branch Status', 'Activate or deactivate branches.', 'ACTION'),
    ('HR_ACCESS', 'HR', 'HR Access', 'Access HR module.', 'ACCESS'),
    ('EMPLOYEES_VIEW', 'HR', 'View Employees', 'View employees.', 'ACTION'),
    ('EMPLOYEES_CREATE', 'HR', 'Create Employees', 'Create employees.', 'ACTION'),
    ('EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'Update employees.', 'ACTION'),
    ('EMPLOYEES_DELETE', 'HR', 'Delete Employees', 'Delete employees.', 'ACTION'),
    ('EMPLOYEES_CHANGE_STATUS', 'HR', 'Change Employee Status', 'Activate or deactivate employees.', 'ACTION'),
    ('PRODUCTS_ACCESS', 'PRODUCTS', 'Products Access', 'Access products module.', 'ACCESS'),
    ('PRODUCTS_VIEW', 'PRODUCTS', 'View Products', 'View products.', 'ACTION'),
    ('PRODUCTS_CREATE', 'PRODUCTS', 'Create Products', 'Create products.', 'ACTION'),
    ('PRODUCTS_UPDATE', 'PRODUCTS', 'Update Products', 'Update products.', 'ACTION'),
    ('PRODUCTS_DELETE', 'PRODUCTS', 'Delete Products', 'Delete products.', 'ACTION'),
    ('PRODUCTS_CHANGE_STATUS', 'PRODUCTS', 'Change Product Status', 'Activate or deactivate products.', 'ACTION'),
    ('INVENTORY_ACCESS', 'INVENTORY', 'Inventory Access', 'Access inventory module.', 'ACCESS'),
    ('INVENTORY_VIEW', 'INVENTORY', 'View Inventory', 'View inventory.', 'ACTION'),
    ('INVENTORY_DOC_CREATE', 'INVENTORY', 'Create Inventory Documents', 'Create inventory documents.', 'ACTION'),
    ('INVENTORY_DOC_UPDATE', 'INVENTORY', 'Update Inventory Documents', 'Update inventory documents.', 'ACTION'),
    ('INVENTORY_DOC_APPROVE', 'INVENTORY', 'Approve Inventory Documents', 'Approve inventory documents.', 'ACTION'),
    ('ORDERS_ACCESS', 'ORDERS', 'Orders Access', 'Access orders module.', 'ACCESS'),
    ('ORDERS_VIEW', 'ORDERS', 'View Orders', 'View orders.', 'ACTION'),
    ('ORDERS_CREATE', 'ORDERS', 'Create Orders', 'Create orders.', 'ACTION'),
    ('ORDERS_CANCEL', 'ORDERS', 'Cancel Orders', 'Cancel orders.', 'ACTION'),
    ('ORDERS_REFUND', 'ORDERS', 'Refund Orders', 'Refund orders.', 'ACTION'),
    ('ORDERS_DISCOUNT', 'ORDERS', 'Discount Orders', 'Apply order discounts.', 'ACTION'),
    ('SHIFTS_ACCESS', 'SHIFTS', 'Shifts Access', 'Access shifts module.', 'ACCESS'),
    ('SHIFTS_VIEW', 'SHIFTS', 'View Shifts', 'View shifts.', 'ACTION'),
    ('SHIFTS_OPEN', 'SHIFTS', 'Open Shifts', 'Open shifts.', 'ACTION'),
    ('SHIFTS_CLOSE', 'SHIFTS', 'Close Shifts', 'Close shifts.', 'ACTION'),
    ('ACCOUNTING_ACCESS', 'ACCOUNTING', 'Accounting Access', 'Access accounting module.', 'ACCESS'),
    ('PAYMENTS_VIEW', 'ACCOUNTING', 'View Payments', 'View payments.', 'ACTION'),
    ('PAYMENTS_CREATE', 'ACCOUNTING', 'Create Payments', 'Create payments.', 'ACTION'),
    ('CASH_MOVEMENTS_VIEW', 'ACCOUNTING', 'View Cash Movements', 'View cash movements.', 'ACTION'),
    ('CASH_MOVEMENTS_CREATE', 'ACCOUNTING', 'Create Cash Movements', 'Create cash movements.', 'ACTION'),
    ('REPORTS_ACCESS', 'REPORTS', 'Reports Access', 'Access reports module.', 'ACCESS'),
    ('REPORTS_VIEW_SALES', 'REPORTS', 'View Sales Reports', 'View sales reports.', 'ACTION'),
    ('REPORTS_VIEW_CASHIER', 'REPORTS', 'View Cashier Reports', 'View cashier reports.', 'ACTION'),
    ('REPORTS_VIEW_PRODUCTS', 'REPORTS', 'View Product Reports', 'View product reports.', 'ACTION'),
    ('ROLES_ACCESS', 'ROLES', 'Roles Access', 'Access roles module.', 'ACCESS'),
    ('ROLES_VIEW', 'ROLES', 'View Roles', 'View roles.', 'ACTION'),
    ('ROLES_UPDATE_DEFAULTS', 'ROLES', 'Update Role Defaults', 'Update default permissions for system roles.', 'ACTION'),
    ('PERMISSIONS_ACCESS', 'PERMISSIONS', 'Permissions Access', 'Access permissions module.', 'ACCESS'),
    ('PERMISSIONS_VIEW', 'PERMISSIONS', 'View Permissions', 'View permissions.', 'ACTION'),
    ('HR_EMPLOYEES_VIEW', 'HR', 'View HR Employees', 'View HR employees.', 'ACTION'),
    ('HR_EMPLOYEES_CREATE', 'HR', 'Create HR Employees', 'Create HR employees.', 'ACTION'),
    ('HR_EMPLOYEES_UPDATE', 'HR', 'Update HR Employees', 'Update HR employees.', 'ACTION'),
    ('HR_LEAVES_VIEW', 'HR', 'View Leaves', 'View HR leave types and requests.', 'ACTION'),
    ('INVENTORY_SETUP_VIEW', 'INVENTORY', 'View Inventory Setup', 'View inventory setup (materials, categories, UOMs).', 'ACTION'),
    ('INVENTORY_SETUP_MANAGE', 'INVENTORY', 'Manage Inventory Setup', 'Manage inventory setup (materials, categories, UOMs).', 'ACTION'),
    ('INVENTORY_STOCK_VIEW', 'INVENTORY', 'View Inventory Stock', 'View inventory stock and warehouses.', 'ACTION'),
    ('INVENTORY_STOCK_MANAGE', 'INVENTORY', 'Manage Inventory Stock', 'Manage inventory stock and warehouses.', 'ACTION'),
    ('INVENTORY_PURCHASE_VIEW', 'INVENTORY', 'View Inventory Purchases', 'View purchase invoices and returns.', 'ACTION'),
    ('INVENTORY_PURCHASE_MANAGE', 'INVENTORY', 'Manage Inventory Purchases', 'Manage purchase invoices and returns.', 'ACTION'),
    ('JOBS_VIEW', 'JOBS', 'View Jobs', 'View job titles.', 'ACTION'),
    ('JOBS_CREATE', 'JOBS', 'Create Jobs', 'Create job titles.', 'ACTION'),
    ('JOBS_UPDATE', 'JOBS', 'Update Jobs', 'Update job titles.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.is_active = TRUE
WHERE r.code = 'OWNER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'USERS_ACCESS','USERS_VIEW','BRANCHES_ACCESS','BRANCHES_VIEW','HR_ACCESS','EMPLOYEES_VIEW',
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','PRODUCTS_UPDATE','INVENTORY_ACCESS','INVENTORY_VIEW',
    'INVENTORY_DOC_CREATE','ORDERS_ACCESS','ORDERS_VIEW','ORDERS_CREATE','ORDERS_CANCEL',
    'ORDERS_DISCOUNT','SHIFTS_ACCESS','SHIFTS_VIEW','REPORTS_ACCESS','REPORTS_VIEW_SALES',
    'REPORTS_VIEW_CASHIER','REPORTS_VIEW_PRODUCTS')
WHERE r.code = 'BRANCH_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','ORDERS_ACCESS','ORDERS_CREATE','SHIFTS_ACCESS',
    'SHIFTS_VIEW','SHIFTS_OPEN','SHIFTS_CLOSE')
WHERE r.code = 'CASHIER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ORDERS_ACCESS','ORDERS_VIEW','ACCOUNTING_ACCESS','PAYMENTS_VIEW','CASH_MOVEMENTS_VIEW',
    'REPORTS_ACCESS','REPORTS_VIEW_SALES','REPORTS_VIEW_CASHIER','REPORTS_VIEW_PRODUCTS',
    'SHIFTS_ACCESS','SHIFTS_VIEW')
WHERE r.code = 'ACCOUNTANT' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'USERS_ACCESS','USERS_VIEW','HR_ACCESS','EMPLOYEES_VIEW','EMPLOYEES_CREATE',
    'EMPLOYEES_UPDATE','EMPLOYEES_CHANGE_STATUS')
WHERE r.code = 'HR_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','PRODUCTS_CREATE','PRODUCTS_UPDATE','PRODUCTS_CHANGE_STATUS',
    'INVENTORY_ACCESS','INVENTORY_VIEW','INVENTORY_DOC_CREATE','INVENTORY_DOC_UPDATE','INVENTORY_DOC_APPROVE')
WHERE r.code = 'INVENTORY_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =====================================================================
-- Seed: System tenant + SYS_ADMIN bootstrap user (nou7 / secret123)
-- =====================================================================
INSERT INTO tenants (id, name, code, status, created_at)
VALUES (0, 'System', 'system', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('tenants_id_seq', GREATEST((SELECT MAX(id) FROM tenants), 1));

INSERT INTO roles (code, name, description, is_active, created_at)
VALUES ('SYS_ADMIN', 'System Admin', 'Full system admin access', TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SYS_ADMIN' ON CONFLICT DO NOTHING;

-- Login username: Nou7 or nou7  |  Password: secret123
INSERT INTO users (tenant_id, full_name, username, email, password_hash, status, created_at)
VALUES (0, 'Nou7', 'nou7', 'nou7@test.com',
        '$2a$10$QjCk73uhV24E.Tqs/34ic.8IyxZ25bAiiZWc68FZfoCh/Jqe33EzG',
        'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (tenant_id, username) DO NOTHING;

INSERT INTO user_roles (tenant_id, user_id, role_id, scope, branch_id, created_at)
SELECT 0, u.id, r.id, 'TENANT', NULL, CURRENT_TIMESTAMP
FROM users u JOIN roles r ON r.code = 'SYS_ADMIN'
WHERE u.tenant_id = 0 AND u.username = 'nou7'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET role_id = EXCLUDED.role_id, scope = EXCLUDED.scope,
    branch_id = NULL, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT 0, u.id, rp.permission_id, CURRENT_TIMESTAMP
FROM users u JOIN roles r ON r.code = 'SYS_ADMIN'
JOIN role_permissions rp ON rp.role_id = r.id
WHERE u.tenant_id = 0 AND u.username = 'nou7'
ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;

