-- =====================================================================
-- HR module: employees, leave, salaries
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.employee_leave_balances (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    leave_type_id bigint NOT NULL,
    year integer NOT NULL,
    opening_balance numeric(8,2) DEFAULT 0 NOT NULL,
    assigned_days numeric(8,2) DEFAULT 0 NOT NULL,
    used_days numeric(8,2) DEFAULT 0 NOT NULL,
    remaining_days numeric(8,2) DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    notes text,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_employee_leave_balances_days CHECK (((opening_balance >= (0)::numeric) AND (assigned_days >= (0)::numeric) AND (used_days >= (0)::numeric) AND (remaining_days >= (0)::numeric))),
    CONSTRAINT chk_employee_leave_balances_year CHECK (((year >= 2000) AND (year <= 2100)))
);

CREATE TABLE public.employee_salaries (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    salary_amount numeric(14,2) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    active boolean DEFAULT true NOT NULL,
    notes text,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_employee_salaries_amount CHECK ((salary_amount > (0)::numeric)),
    CONSTRAINT chk_employee_salaries_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)))
);

CREATE TABLE public.employee_salary_adjustments (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    type character varying(30) NOT NULL,
    amount numeric(14,2) NOT NULL,
    adjustment_date date NOT NULL,
    reason text,
    notes text,
    active boolean DEFAULT true NOT NULL,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_employee_salary_adjustments_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_employee_salary_adjustments_type CHECK (((type)::text = ANY ((ARRAY['ADDITION'::character varying, 'DEDUCTION'::character varying])::text[])))
);

CREATE TABLE public.hr_employees (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    job_id bigint NOT NULL,
    user_id bigint,
    code character varying(100) NOT NULL,
    full_name character varying(255) NOT NULL,
    phone character varying(50),
    email character varying(255),
    national_id character varying(100),
    address text,
    hire_date date NOT NULL,
    salary numeric(14,2) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    full_name_en character varying(255),
    full_name_ar character varying(255),
    address_en text,
    address_ar text
);

CREATE TABLE public.hr_leave_request (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    leave_type_id bigint NOT NULL,
    leave_balance_id bigint NOT NULL,
    from_date date NOT NULL,
    to_date date NOT NULL,
    days_count numeric(8,2) NOT NULL,
    reason text,
    status character varying(30) NOT NULL,
    notes text,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_hr_leave_request_dates CHECK ((from_date <= to_date)),
    CONSTRAINT chk_hr_leave_request_days_count CHECK ((days_count > (0)::numeric)),
    CONSTRAINT chk_hr_leave_request_status CHECK (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.hr_leave_type (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name_en character varying(255),
    name_ar character varying(255),
    description_en text,
    description_ar text,
    default_days numeric(8,2) DEFAULT 0 NOT NULL,
    paid boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_hr_leave_type_default_days CHECK ((default_days >= (0)::numeric)),
    CONSTRAINT chk_hr_leave_type_name CHECK (((NULLIF(btrim((COALESCE(name_en, ''::character varying))::text), ''::text) IS NOT NULL) OR (NULLIF(btrim((COALESCE(name_ar, ''::character varying))::text), ''::text) IS NOT NULL)))
);

CREATE SEQUENCE public.employee_leave_balances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.employee_salaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.employee_salary_adjustments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.hr_employees_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.hr_leave_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.hr_leave_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.employee_leave_balances_id_seq OWNED BY public.employee_leave_balances.id;

ALTER SEQUENCE public.employee_salaries_id_seq OWNED BY public.employee_salaries.id;

ALTER SEQUENCE public.employee_salary_adjustments_id_seq OWNED BY public.employee_salary_adjustments.id;

ALTER SEQUENCE public.hr_employees_id_seq OWNED BY public.hr_employees.id;

ALTER SEQUENCE public.hr_leave_request_id_seq OWNED BY public.hr_leave_request.id;

ALTER SEQUENCE public.hr_leave_type_id_seq OWNED BY public.hr_leave_type.id;

ALTER TABLE ONLY public.employee_leave_balances ALTER COLUMN id SET DEFAULT nextval('public.employee_leave_balances_id_seq'::regclass);

ALTER TABLE ONLY public.employee_salaries ALTER COLUMN id SET DEFAULT nextval('public.employee_salaries_id_seq'::regclass);

ALTER TABLE ONLY public.employee_salary_adjustments ALTER COLUMN id SET DEFAULT nextval('public.employee_salary_adjustments_id_seq'::regclass);

ALTER TABLE ONLY public.hr_employees ALTER COLUMN id SET DEFAULT nextval('public.hr_employees_id_seq'::regclass);

ALTER TABLE ONLY public.hr_leave_request ALTER COLUMN id SET DEFAULT nextval('public.hr_leave_request_id_seq'::regclass);

ALTER TABLE ONLY public.hr_leave_type ALTER COLUMN id SET DEFAULT nextval('public.hr_leave_type_id_seq'::regclass);

ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT employee_leave_balances_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_salaries
    ADD CONSTRAINT employee_salaries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_salary_adjustments
    ADD CONSTRAINT employee_salary_adjustments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.hr_leave_type
    ADD CONSTRAINT hr_leave_type_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT uk_employee_leave_balances_employee_type_year UNIQUE (tenant_id, employee_id, leave_type_id, year);

ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT uk_hr_employees_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.hr_leave_type
    ADD CONSTRAINT uk_hr_leave_type_tenant_code UNIQUE (tenant_id, code);

CREATE INDEX idx_employee_leave_balances_employee_year ON public.employee_leave_balances USING btree (tenant_id, employee_id, year);

CREATE INDEX idx_employee_salaries_employee ON public.employee_salaries USING btree (tenant_id, employee_id);

CREATE INDEX idx_employee_salary_adjustments_employee ON public.employee_salary_adjustments USING btree (tenant_id, employee_id, adjustment_date);

CREATE INDEX idx_hr_employees_tenant_active ON public.hr_employees USING btree (tenant_id, is_active);

CREATE INDEX idx_hr_employees_tenant_branch ON public.hr_employees USING btree (tenant_id, branch_id);

CREATE INDEX idx_hr_employees_user ON public.hr_employees USING btree (tenant_id, user_id);

CREATE INDEX idx_hr_leave_request_balance ON public.hr_leave_request USING btree (leave_balance_id);

CREATE INDEX idx_hr_leave_request_employee ON public.hr_leave_request USING btree (tenant_id, employee_id, id);

CREATE UNIQUE INDEX ux_employee_salaries_active ON public.employee_salaries USING btree (tenant_id, employee_id) WHERE (active = true);
