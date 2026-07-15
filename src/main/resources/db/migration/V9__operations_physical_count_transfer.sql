-- =====================================================================
-- Operations: physical count, transfers, consumption events
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.inventory_transfer (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    source_warehouse_id bigint NOT NULL,
    destination_warehouse_id bigint NOT NULL,
    requested_date date NOT NULL,
    dispatched_at timestamp without time zone,
    received_at timestamp without time zone,
    cancelled_at timestamp without time zone,
    dispatched_by bigint,
    received_by bigint,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_transfer_source_dest_diff CHECK ((source_warehouse_id <> destination_warehouse_id)),
    CONSTRAINT chk_transfer_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_TRANSIT'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.inventory_transfer_line (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    material_id bigint NOT NULL,
    requested_quantity numeric(18,6) NOT NULL,
    dispatched_quantity numeric(18,6),
    received_quantity numeric(18,6),
    uom_id bigint NOT NULL,
    unit_cost_snapshot numeric(18,6) DEFAULT 0 NOT NULL,
    dispatch_transaction_id bigint,
    receive_transaction_id bigint,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.order_consumption_event (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    order_id bigint,
    order_line_id bigint,
    material_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    quantity numeric(18,6) NOT NULL,
    uom_id bigint NOT NULL,
    unit_cost_snapshot numeric(18,6) DEFAULT 0 NOT NULL,
    total_cost_snapshot numeric(18,6) DEFAULT 0 NOT NULL,
    recipe_id bigint,
    business_date date NOT NULL,
    consumed_at timestamp without time zone NOT NULL,
    idempotency_key character varying(100),
    posted_to_ledger boolean DEFAULT false NOT NULL,
    posted_transaction_id bigint,
    reverses_event_id bigint,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.physical_count (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    scheduled_date date NOT NULL,
    started_at timestamp without time zone,
    frozen_at timestamp without time zone,
    reconciled_at timestamp without time zone,
    cancelled_at timestamp without time zone,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    reconciled_by bigint,
    cancelled_by bigint,
    cancel_reason text,
    has_large_variance boolean DEFAULT false NOT NULL,
    large_variance_value numeric(18,6),
    CONSTRAINT chk_physical_count_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_PROGRESS'::character varying, 'RECONCILED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.physical_count_line (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    physical_count_id bigint NOT NULL,
    material_id bigint NOT NULL,
    expected_quantity numeric(18,6) NOT NULL,
    counted_quantity numeric(18,6),
    uom_id bigint NOT NULL,
    variance numeric(18,6),
    unit_cost_at_freeze numeric(18,6) DEFAULT 0 NOT NULL,
    variance_value numeric(18,6),
    counted_at timestamp without time zone,
    notes text,
    adjustment_transaction_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    action_taken character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    waste_transaction_id bigint,
    adjusted_expected_quantity numeric(18,6)
);

CREATE SEQUENCE public.inventory_transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.inventory_transfer_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_consumption_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.physical_count_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.physical_count_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.inventory_transfer_id_seq OWNED BY public.inventory_transfer.id;

ALTER SEQUENCE public.inventory_transfer_line_id_seq OWNED BY public.inventory_transfer_line.id;

ALTER SEQUENCE public.order_consumption_event_id_seq OWNED BY public.order_consumption_event.id;

ALTER SEQUENCE public.physical_count_id_seq OWNED BY public.physical_count.id;

ALTER SEQUENCE public.physical_count_line_id_seq OWNED BY public.physical_count_line.id;

ALTER TABLE ONLY public.inventory_transfer ALTER COLUMN id SET DEFAULT nextval('public.inventory_transfer_id_seq'::regclass);

ALTER TABLE ONLY public.inventory_transfer_line ALTER COLUMN id SET DEFAULT nextval('public.inventory_transfer_line_id_seq'::regclass);

ALTER TABLE ONLY public.order_consumption_event ALTER COLUMN id SET DEFAULT nextval('public.order_consumption_event_id_seq'::regclass);

ALTER TABLE ONLY public.physical_count ALTER COLUMN id SET DEFAULT nextval('public.physical_count_id_seq'::regclass);

ALTER TABLE ONLY public.physical_count_line ALTER COLUMN id SET DEFAULT nextval('public.physical_count_line_id_seq'::regclass);

ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT inventory_transfer_line_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_transfer
    ADD CONSTRAINT inventory_transfer_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT order_consumption_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT physical_count_line_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.physical_count
    ADD CONSTRAINT physical_count_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_transfer
    ADD CONSTRAINT uk_inventory_transfer_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT uk_oce_tenant_idempotency UNIQUE (tenant_id, idempotency_key);

ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT uk_physical_count_line_count_material UNIQUE (physical_count_id, material_id);

ALTER TABLE ONLY public.physical_count
    ADD CONSTRAINT uk_physical_count_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT uk_transfer_line_transfer_material UNIQUE (transfer_id, material_id);

CREATE INDEX idx_oce_aggregation ON public.order_consumption_event USING btree (tenant_id, posted_to_ledger, warehouse_id, material_id, business_date);

CREATE INDEX idx_oce_material_consumed ON public.order_consumption_event USING btree (tenant_id, material_id, consumed_at);

CREATE INDEX idx_oce_order ON public.order_consumption_event USING btree (order_id);

CREATE INDEX idx_oce_posted_tx ON public.order_consumption_event USING btree (posted_transaction_id);

CREATE INDEX idx_oce_reverses ON public.order_consumption_event USING btree (reverses_event_id);

CREATE INDEX idx_pc_line_adjustment_tx ON public.physical_count_line USING btree (adjustment_transaction_id);

CREATE INDEX idx_pc_line_material ON public.physical_count_line USING btree (material_id);

CREATE INDEX idx_pc_line_tenant_count ON public.physical_count_line USING btree (tenant_id, physical_count_id);

CREATE INDEX idx_physical_count_scheduled_date ON public.physical_count USING btree (scheduled_date);

CREATE INDEX idx_physical_count_tenant_warehouse_status ON public.physical_count USING btree (tenant_id, warehouse_id, status);

CREATE INDEX idx_transfer_destination ON public.inventory_transfer USING btree (destination_warehouse_id);

CREATE INDEX idx_transfer_line_dispatch_tx ON public.inventory_transfer_line USING btree (dispatch_transaction_id);

CREATE INDEX idx_transfer_line_material ON public.inventory_transfer_line USING btree (material_id);

CREATE INDEX idx_transfer_line_receive_tx ON public.inventory_transfer_line USING btree (receive_transaction_id);

CREATE INDEX idx_transfer_line_tenant_transfer ON public.inventory_transfer_line USING btree (tenant_id, transfer_id);

CREATE INDEX idx_transfer_requested_date ON public.inventory_transfer USING btree (requested_date);

CREATE INDEX idx_transfer_source ON public.inventory_transfer USING btree (source_warehouse_id);

CREATE INDEX idx_transfer_tenant_status ON public.inventory_transfer USING btree (tenant_id, status);
