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

