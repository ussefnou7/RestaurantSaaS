-- =====================================================================
-- Purchasing: invoices, returns, sequence
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.invoice_sequence (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    year smallint NOT NULL,
    last_seq integer DEFAULT 0 NOT NULL,
    doc_type character varying(20) DEFAULT 'PINV'::character varying NOT NULL
);

CREATE TABLE public.purchase_invoice (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    supplier_id bigint,
    warehouse_id bigint NOT NULL,
    invoice_number character varying(100),
    invoice_date date NOT NULL,
    receipt_date date NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    subtotal numeric(18,6) DEFAULT 0 NOT NULL,
    discount_amount numeric(18,6) DEFAULT 0 NOT NULL,
    tax_amount numeric(18,6) DEFAULT 0 NOT NULL,
    total_amount numeric(18,6) DEFAULT 0 NOT NULL,
    paid_amount numeric(18,6) DEFAULT 0 NOT NULL,
    payment_status character varying(30) DEFAULT 'UNPAID'::character varying NOT NULL,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    posted_at timestamp without time zone,
    posted_by bigint,
    posted_to_inventory boolean DEFAULT false NOT NULL,
    completed_at timestamp without time zone,
    completed_by bigint,
    cancelled_at timestamp without time zone,
    cancelled_by bigint,
    cancel_reason text,
    updated_by bigint,
    discount_percent numeric(10,4) DEFAULT 0 NOT NULL,
    tax_percent numeric(10,4) DEFAULT 0 NOT NULL,
    CONSTRAINT chk_purchase_invoice_discount_amount CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_paid_amount CHECK ((paid_amount >= (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_payment_status CHECK (((payment_status)::text = ANY ((ARRAY['UNPAID'::character varying, 'PARTIALLY_PAID'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT chk_purchase_invoice_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'COMPLETE'::character varying, 'POSTED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_purchase_invoice_subtotal CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_tax_amount CHECK ((tax_amount >= (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_total_amount CHECK ((total_amount >= (0)::numeric))
);

CREATE TABLE public.purchase_invoice_line (
    id bigint NOT NULL,
    purchase_invoice_id bigint NOT NULL,
    material_id bigint NOT NULL,
    quantity numeric(18,6) NOT NULL,
    uom_id bigint NOT NULL,
    unit_cost numeric(18,6) NOT NULL,
    line_total numeric(18,6) NOT NULL,
    notes text,
    created_by bigint,
    updated_by bigint,
    discount_percent numeric(10,4) DEFAULT 0 NOT NULL,
    discount_amount numeric(18,6) DEFAULT 0 NOT NULL,
    line_net_total numeric(18,6) DEFAULT 0 NOT NULL,
    CONSTRAINT chk_purchase_invoice_line_quantity CHECK ((quantity > (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_line_total CHECK ((line_total >= (0)::numeric)),
    CONSTRAINT chk_purchase_invoice_line_unit_cost CHECK ((unit_cost >= (0)::numeric))
);

CREATE TABLE public.purchase_return (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    original_invoice_id bigint NOT NULL,
    supplier_id bigint,
    warehouse_id bigint NOT NULL,
    return_number character varying(100),
    return_date date NOT NULL,
    reason character varying(50) NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    subtotal numeric(18,6) DEFAULT 0 NOT NULL,
    total_amount numeric(18,6) DEFAULT 0 NOT NULL,
    notes text,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    completed_at timestamp without time zone,
    completed_by bigint,
    posted_at timestamp without time zone,
    posted_by bigint,
    cancelled_at timestamp without time zone,
    cancelled_by bigint,
    cancel_reason text,
    posted_to_inventory boolean DEFAULT false NOT NULL
);

CREATE TABLE public.purchase_return_line (
    id bigint NOT NULL,
    purchase_return_id bigint NOT NULL,
    original_line_id bigint NOT NULL,
    material_id bigint NOT NULL,
    quantity numeric(18,6) NOT NULL,
    uom_id bigint NOT NULL,
    unit_cost numeric(18,6) NOT NULL,
    line_total numeric(18,6) NOT NULL,
    notes text
);

CREATE SEQUENCE public.invoice_sequence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.purchase_invoice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.purchase_invoice_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.purchase_return_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.purchase_return_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.invoice_sequence_id_seq OWNED BY public.invoice_sequence.id;

ALTER SEQUENCE public.purchase_invoice_id_seq OWNED BY public.purchase_invoice.id;

ALTER SEQUENCE public.purchase_invoice_line_id_seq OWNED BY public.purchase_invoice_line.id;

ALTER SEQUENCE public.purchase_return_id_seq OWNED BY public.purchase_return.id;

ALTER SEQUENCE public.purchase_return_line_id_seq OWNED BY public.purchase_return_line.id;

ALTER TABLE ONLY public.invoice_sequence ALTER COLUMN id SET DEFAULT nextval('public.invoice_sequence_id_seq'::regclass);

ALTER TABLE ONLY public.purchase_invoice ALTER COLUMN id SET DEFAULT nextval('public.purchase_invoice_id_seq'::regclass);

ALTER TABLE ONLY public.purchase_invoice_line ALTER COLUMN id SET DEFAULT nextval('public.purchase_invoice_line_id_seq'::regclass);

ALTER TABLE ONLY public.purchase_return ALTER COLUMN id SET DEFAULT nextval('public.purchase_return_id_seq'::regclass);

ALTER TABLE ONLY public.purchase_return_line ALTER COLUMN id SET DEFAULT nextval('public.purchase_return_line_id_seq'::regclass);

ALTER TABLE ONLY public.invoice_sequence
    ADD CONSTRAINT invoice_sequence_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.purchase_invoice_line
    ADD CONSTRAINT purchase_invoice_line_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.purchase_invoice
    ADD CONSTRAINT purchase_invoice_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.purchase_return_line
    ADD CONSTRAINT purchase_return_line_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.purchase_return
    ADD CONSTRAINT purchase_return_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invoice_sequence
    ADD CONSTRAINT uk_invoice_sequence_tenant_year_doctype UNIQUE (tenant_id, year, doc_type);

ALTER TABLE ONLY public.purchase_invoice
    ADD CONSTRAINT uk_purchase_invoice_tenant_invoice_number UNIQUE (tenant_id, invoice_number);

ALTER TABLE ONLY public.purchase_return
    ADD CONSTRAINT uk_purchase_return_tenant_number UNIQUE (tenant_id, return_number);

CREATE INDEX idx_purchase_invoice_line_invoice ON public.purchase_invoice_line USING btree (purchase_invoice_id);

CREATE INDEX idx_purchase_invoice_line_material ON public.purchase_invoice_line USING btree (material_id);

CREATE INDEX idx_purchase_invoice_tenant_invoice_date ON public.purchase_invoice USING btree (tenant_id, invoice_date DESC);

CREATE INDEX idx_purchase_invoice_tenant_payment_status ON public.purchase_invoice USING btree (tenant_id, payment_status);

CREATE INDEX idx_purchase_invoice_tenant_status ON public.purchase_invoice USING btree (tenant_id, status);

CREATE INDEX idx_purchase_invoice_tenant_supplier ON public.purchase_invoice USING btree (tenant_id, supplier_id);

CREATE INDEX idx_purchase_invoice_tenant_warehouse ON public.purchase_invoice USING btree (tenant_id, warehouse_id);

CREATE INDEX idx_purchase_return_line_original ON public.purchase_return_line USING btree (original_line_id);

CREATE INDEX idx_purchase_return_line_return ON public.purchase_return_line USING btree (purchase_return_id);

CREATE INDEX idx_purchase_return_tenant_invoice ON public.purchase_return USING btree (tenant_id, original_invoice_id);

-- Purchase invoice unpost audit fields.

ALTER TABLE public.purchase_invoice
    ADD COLUMN unposted_at timestamp without time zone,
    ADD COLUMN unposted_by bigint;

CREATE INDEX idx_stock_batch_tenant_source_invoice
    ON public.stock_batch USING btree (tenant_id, source_invoice_id);

-- Purchase return unpost audit fields.

ALTER TABLE public.purchase_return
    ADD COLUMN unposted_at timestamp without time zone,
    ADD COLUMN unposted_by bigint;

CREATE INDEX idx_stock_batch_tenant_source_invoice_line
    ON public.stock_batch USING btree (tenant_id, source_invoice_line_id);

-- Dedicated UnComplete permissions and audit fields for purchase documents.
-- Interim default grants follow the existing inventory action-permission pattern
-- until approval workflows own assignment of document actions.

ALTER TABLE public.purchase_invoice
    ADD COLUMN IF NOT EXISTS uncompleted_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS uncompleted_by bigint;

ALTER TABLE public.purchase_return
    ADD COLUMN IF NOT EXISTS uncompleted_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS uncompleted_by bigint;

