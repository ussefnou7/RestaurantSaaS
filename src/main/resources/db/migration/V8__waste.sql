-- =====================================================================
-- Waste module: stock write-off documents (spoiled, expired, damaged...)
-- Lifecycle DRAFT -> COMPLETE -> POSTED -> CANCELLED. On POST each line
-- issues a WASTE / OUT inventory transaction (FIFO-depleted, ledger-costed).
-- =====================================================================

CREATE TABLE public.waste_document (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    code character varying(100),
    waste_date date NOT NULL,
    reason_code character varying(50) NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    notes text,
    posted_to_inventory boolean DEFAULT false NOT NULL,
    completed_at timestamp without time zone,
    completed_by bigint,
    posted_at timestamp without time zone,
    posted_by bigint,
    cancelled_at timestamp without time zone,
    cancelled_by bigint,
    cancel_reason text,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_waste_document_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'COMPLETE'::character varying, 'POSTED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_waste_document_reason_code CHECK (((reason_code)::text = ANY ((ARRAY['EXPIRED'::character varying, 'DAMAGED'::character varying, 'SPOILED'::character varying, 'CONTAMINATED'::character varying, 'LOST'::character varying, 'THEFT'::character varying, 'OPERATIONAL_LOSS'::character varying, 'CUSTOMER_RETURN'::character varying, 'OTHER'::character varying])::text[])))
);

CREATE TABLE public.waste_line (
    id bigint NOT NULL,
    waste_document_id bigint NOT NULL,
    material_id bigint NOT NULL,
    quantity numeric(18,6) NOT NULL,
    uom_id bigint NOT NULL,
    notes text,
    CONSTRAINT chk_waste_line_quantity CHECK ((quantity > (0)::numeric))
);

CREATE SEQUENCE public.waste_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.waste_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.waste_document_id_seq OWNED BY public.waste_document.id;
ALTER SEQUENCE public.waste_line_id_seq OWNED BY public.waste_line.id;

ALTER TABLE ONLY public.waste_document ALTER COLUMN id SET DEFAULT nextval('public.waste_document_id_seq'::regclass);
ALTER TABLE ONLY public.waste_line ALTER COLUMN id SET DEFAULT nextval('public.waste_line_id_seq'::regclass);

ALTER TABLE ONLY public.waste_document
    ADD CONSTRAINT waste_document_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.waste_line
    ADD CONSTRAINT waste_line_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.waste_document
    ADD CONSTRAINT uk_waste_document_tenant_code UNIQUE (tenant_id, code);

CREATE INDEX idx_waste_document_tenant_waste_date ON public.waste_document USING btree (tenant_id, waste_date DESC);
CREATE INDEX idx_waste_document_tenant_status ON public.waste_document USING btree (tenant_id, status);
CREATE INDEX idx_waste_document_tenant_warehouse ON public.waste_document USING btree (tenant_id, warehouse_id);
CREATE INDEX idx_waste_line_document ON public.waste_line USING btree (waste_document_id);
CREATE INDEX idx_waste_line_material ON public.waste_line USING btree (material_id);

-- Foreign keys
ALTER TABLE ONLY public.waste_document
    ADD CONSTRAINT waste_document_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.waste_document
    ADD CONSTRAINT waste_document_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.waste_line
    ADD CONSTRAINT waste_line_waste_document_id_fkey FOREIGN KEY (waste_document_id) REFERENCES public.waste_document(id);
ALTER TABLE ONLY public.waste_line
    ADD CONSTRAINT waste_line_material_id_fkey FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.waste_line
    ADD CONSTRAINT waste_line_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES public.uom(id);
