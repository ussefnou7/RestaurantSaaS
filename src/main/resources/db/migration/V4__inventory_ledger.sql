-- =====================================================================
-- Inventory ledger: stock balance, batches, transactions
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.inventory_transaction (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    material_id bigint NOT NULL,
    transaction_type character varying(50) NOT NULL,
    direction character varying(10) NOT NULL,
    entered_quantity numeric(18,6) NOT NULL,
    entered_uom_id bigint NOT NULL,
    stock_quantity numeric(18,6) NOT NULL,
    stock_uom_id bigint NOT NULL,
    unit_cost numeric(18,6),
    total_cost numeric(18,6),
    reference_type character varying(100),
    reference_id bigint,
    source_invoice_line_id bigint,
    transaction_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    notes text,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    idempotency_key character varying(100),
    reverses_transaction_id bigint,
    reason_code character varying(50),
    batch_number character varying(100),
    expiry_date date,
    shift_id bigint,
    movement_date timestamp without time zone NOT NULL,
    CONSTRAINT chk_inventory_transaction_direction CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[]))),
    CONSTRAINT chk_inventory_transaction_entered_quantity CHECK ((entered_quantity > (0)::numeric)),
    CONSTRAINT chk_inventory_transaction_stock_quantity CHECK ((stock_quantity > (0)::numeric)),
    CONSTRAINT chk_inventory_transaction_total_cost CHECK (((total_cost IS NULL) OR (total_cost >= (0)::numeric))),
    CONSTRAINT chk_inventory_transaction_type CHECK (((transaction_type)::text = ANY ((ARRAY['OPENING_BALANCE'::character varying, 'PURCHASE'::character varying, 'PURCHASE_RETURN'::character varying, 'TRANSFER_OUT'::character varying, 'TRANSFER_IN'::character varying, 'CONSUMPTION_SUMMARY'::character varying, 'MANUAL_CONSUMPTION'::character varying, 'WASTE'::character varying, 'ADJUSTMENT'::character varying, 'COUNT_ADJUSTMENT'::character varying])::text[]))),
    CONSTRAINT chk_inventory_transaction_unit_cost CHECK (((unit_cost IS NULL) OR (unit_cost >= (0)::numeric)))
);

CREATE TABLE public.stock_balance (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    material_id bigint NOT NULL,
    quantity numeric(18,6) DEFAULT 0 NOT NULL,
    uom_id bigint NOT NULL,
    average_cost numeric(18,6) DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    minimum_quantity numeric(18,6) DEFAULT 0 NOT NULL,
    maximum_quantity numeric(18,6),
    version bigint DEFAULT 0 NOT NULL,
    last_purchase_price numeric(18,6),
    last_purchase_date timestamp without time zone,
    last_count_date timestamp without time zone,
    last_count_quantity numeric(18,6),
    opening_quantity numeric(18,6) DEFAULT 0 NOT NULL,
    CONSTRAINT chk_stock_balance_average_cost CHECK ((average_cost >= (0)::numeric)),
    CONSTRAINT chk_stock_balance_quantity CHECK ((quantity >= (0)::numeric))
);

CREATE TABLE public.stock_batch (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    stock_balance_id bigint NOT NULL,
    original_quantity numeric(18,6) NOT NULL,
    remaining_quantity numeric(18,6) NOT NULL,
    unit_cost numeric(18,6),
    movement_date timestamp without time zone NOT NULL,
    source_transaction_id bigint NOT NULL,
    source_invoice_id bigint,
    source_invoice_line_id bigint,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_stock_batch_original_quantity CHECK ((original_quantity > (0)::numeric)),
    CONSTRAINT chk_stock_batch_remaining_quantity CHECK ((remaining_quantity >= (0)::numeric)),
    CONSTRAINT chk_stock_batch_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT chk_stock_batch_unit_cost CHECK (((unit_cost IS NULL) OR (unit_cost >= (0)::numeric)))
);

CREATE SEQUENCE public.inventory_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.stock_balance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.stock_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.inventory_transaction_id_seq OWNED BY public.inventory_transaction.id;

ALTER SEQUENCE public.stock_balance_id_seq OWNED BY public.stock_balance.id;

ALTER SEQUENCE public.stock_batch_id_seq OWNED BY public.stock_batch.id;

ALTER TABLE ONLY public.inventory_transaction ALTER COLUMN id SET DEFAULT nextval('public.inventory_transaction_id_seq'::regclass);

ALTER TABLE ONLY public.stock_balance ALTER COLUMN id SET DEFAULT nextval('public.stock_balance_id_seq'::regclass);

ALTER TABLE ONLY public.stock_batch ALTER COLUMN id SET DEFAULT nextval('public.stock_batch_id_seq'::regclass);

ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT stock_balance_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.stock_batch
    ADD CONSTRAINT stock_batch_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT uk_inventory_transaction_tenant_idempotency UNIQUE (tenant_id, idempotency_key);

ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT uk_stock_balance_tenant_warehouse_material UNIQUE (tenant_id, warehouse_id, material_id);

CREATE INDEX idx_inv_tx_reference ON public.inventory_transaction USING btree (reference_type, reference_id);

CREATE INDEX idx_inv_tx_reverses ON public.inventory_transaction USING btree (reverses_transaction_id);

CREATE INDEX idx_inv_tx_tenant_type_date ON public.inventory_transaction USING btree (tenant_id, transaction_type, transaction_date);

CREATE INDEX idx_inv_tx_tenant_wh_material_date ON public.inventory_transaction USING btree (tenant_id, warehouse_id, material_id, movement_date);

CREATE INDEX idx_inventory_transaction_reference ON public.inventory_transaction USING btree (tenant_id, reference_type, reference_id);

CREATE INDEX idx_inventory_transaction_tenant_date ON public.inventory_transaction USING btree (tenant_id, transaction_date DESC);

CREATE INDEX idx_inventory_transaction_tenant_material ON public.inventory_transaction USING btree (tenant_id, material_id);

CREATE INDEX idx_inventory_transaction_tenant_type ON public.inventory_transaction USING btree (tenant_id, transaction_type);

CREATE INDEX idx_inventory_transaction_tenant_warehouse ON public.inventory_transaction USING btree (tenant_id, warehouse_id);

CREATE INDEX idx_stock_balance_tenant_material ON public.stock_balance USING btree (tenant_id, material_id);

CREATE INDEX idx_stock_balance_tenant_warehouse ON public.stock_balance USING btree (tenant_id, warehouse_id);

CREATE INDEX idx_stock_batch_open_fifo ON public.stock_batch USING btree (stock_balance_id, status, id);
