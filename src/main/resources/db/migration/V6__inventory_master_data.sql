-- =====================================================================
-- Inventory setup: UOM, materials, categories, warehouses, suppliers
-- Consolidated migration (squashed from legacy V1..V32)
-- =====================================================================

CREATE TABLE public.material (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    catalog_id bigint,
    category_id bigint NOT NULL,
    stock_uom_id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    name_ar character varying(255),
    display_uom_id bigint NOT NULL,
    created_by bigint,
    updated_by bigint,
    minimum_stock_level numeric(18,6)
);

CREATE TABLE public.material_catalog (
    id bigint NOT NULL,
    category_id bigint NOT NULL,
    default_stock_uom_id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    sort_order integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    default_display_uom_id bigint NOT NULL,
    name_ar character varying(255),
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.material_category (
    id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    sort_order integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    tenant_id bigint,
    name_ar character varying(255),
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.supplier (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    tax_number character varying(100),
    active boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    name_ar character varying(255),
    created_by bigint,
    updated_by bigint
);

CREATE TABLE public.uom (
    id bigint NOT NULL,
    tenant_id bigint,
    base_uom_id bigint,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    name_ar character varying(255),
    symbol character varying(50) NOT NULL,
    type character varying(30) NOT NULL,
    factor_to_base numeric(18,6) DEFAULT 1 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_uom_factor_to_base CHECK ((factor_to_base > (0)::numeric)),
    CONSTRAINT chk_uom_type CHECK (((type)::text = ANY ((ARRAY['WEIGHT'::character varying, 'VOLUME'::character varying, 'COUNT'::character varying, 'LENGTH'::character varying])::text[])))
);

CREATE TABLE public.warehouse (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    branch_id bigint,
    code character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(30) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    name_ar character varying(255),
    created_by bigint,
    updated_by bigint,
    CONSTRAINT chk_warehouse_type CHECK (((type)::text = ANY ((ARRAY['CENTRAL'::character varying, 'BRANCH'::character varying, 'KITCHEN'::character varying, 'FREEZER'::character varying, 'BAR'::character varying, 'OTHER'::character varying])::text[])))
);

CREATE SEQUENCE public.material_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.material_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.material_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.supplier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.uom_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.warehouse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.material_catalog_id_seq OWNED BY public.material_catalog.id;

ALTER SEQUENCE public.material_category_id_seq OWNED BY public.material_category.id;

ALTER SEQUENCE public.material_id_seq OWNED BY public.material.id;

ALTER SEQUENCE public.supplier_id_seq OWNED BY public.supplier.id;

ALTER SEQUENCE public.uom_id_seq OWNED BY public.uom.id;

ALTER SEQUENCE public.warehouse_id_seq OWNED BY public.warehouse.id;

ALTER TABLE ONLY public.material ALTER COLUMN id SET DEFAULT nextval('public.material_id_seq'::regclass);

ALTER TABLE ONLY public.material_catalog ALTER COLUMN id SET DEFAULT nextval('public.material_catalog_id_seq'::regclass);

ALTER TABLE ONLY public.material_category ALTER COLUMN id SET DEFAULT nextval('public.material_category_id_seq'::regclass);

ALTER TABLE ONLY public.supplier ALTER COLUMN id SET DEFAULT nextval('public.supplier_id_seq'::regclass);

ALTER TABLE ONLY public.uom ALTER COLUMN id SET DEFAULT nextval('public.uom_id_seq'::regclass);

ALTER TABLE ONLY public.warehouse ALTER COLUMN id SET DEFAULT nextval('public.warehouse_id_seq'::regclass);

ALTER TABLE ONLY public.material_catalog
    ADD CONSTRAINT material_catalog_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.material_category
    ADD CONSTRAINT material_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.material
    ADD CONSTRAINT material_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.supplier
    ADD CONSTRAINT supplier_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.material_catalog
    ADD CONSTRAINT uk_material_catalog_code UNIQUE (code);

ALTER TABLE ONLY public.material
    ADD CONSTRAINT uk_material_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.supplier
    ADD CONSTRAINT uk_supplier_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT uk_warehouse_tenant_code UNIQUE (tenant_id, code);

ALTER TABLE ONLY public.uom
    ADD CONSTRAINT uom_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT warehouse_pkey PRIMARY KEY (id);

CREATE INDEX idx_material_catalog_category ON public.material_catalog USING btree (category_id);

CREATE INDEX idx_material_catalog_default_display_uom ON public.material_catalog USING btree (default_display_uom_id);

CREATE INDEX idx_material_catalog_default_stock_uom ON public.material_catalog USING btree (default_stock_uom_id);

CREATE INDEX idx_material_catalog_default_uom ON public.material_catalog USING btree (default_stock_uom_id);

CREATE INDEX idx_material_category_tenant ON public.material_category USING btree (tenant_id);

CREATE INDEX idx_material_tenant_active ON public.material USING btree (tenant_id, active);

CREATE INDEX idx_material_tenant_category ON public.material USING btree (tenant_id, category_id);

CREATE INDEX idx_material_tenant_default_uom ON public.material USING btree (tenant_id, stock_uom_id);

CREATE INDEX idx_material_tenant_display_uom ON public.material USING btree (tenant_id, display_uom_id);

CREATE INDEX idx_material_tenant_stock_uom ON public.material USING btree (tenant_id, stock_uom_id);

CREATE INDEX idx_supplier_tenant_active ON public.supplier USING btree (tenant_id, active);

CREATE INDEX idx_uom_base_uom ON public.uom USING btree (base_uom_id);

CREATE INDEX idx_uom_tenant ON public.uom USING btree (tenant_id);

CREATE INDEX idx_warehouse_tenant_active ON public.warehouse USING btree (tenant_id, active);

CREATE INDEX idx_warehouse_tenant_branch ON public.warehouse USING btree (tenant_id, branch_id);

CREATE INDEX idx_warehouse_tenant_type ON public.warehouse USING btree (tenant_id, type);

CREATE UNIQUE INDEX uk_uom_global_code ON public.uom USING btree (code) WHERE (tenant_id IS NULL);

CREATE UNIQUE INDEX uk_uom_tenant_code ON public.uom USING btree (tenant_id, code) WHERE (tenant_id IS NOT NULL);

CREATE UNIQUE INDEX ux_material_category_global_code ON public.material_category USING btree (code) WHERE (tenant_id IS NULL);

CREATE UNIQUE INDEX ux_material_category_tenant_code ON public.material_category USING btree (tenant_id, code) WHERE (tenant_id IS NOT NULL);

CREATE UNIQUE INDEX ux_material_tenant_catalog ON public.material USING btree (tenant_id, catalog_id) WHERE (catalog_id IS NOT NULL);

-- =====================================================================
-- Seed: Global UOM reference units (tenant_id = NULL)
-- Base units first (base_uom_id = NULL), then derived units.
-- =====================================================================
INSERT INTO uom (id, tenant_id, base_uom_id, code, name, name_ar, symbol, type, factor_to_base, active, created_at)
VALUES (1, NULL, NULL, 'GRAM',       'Gram',       'جرام',     'g',   'WEIGHT', 1,       true, CURRENT_TIMESTAMP),
       (2, NULL, NULL, 'MILLILITRE', 'Millilitre', 'مليلتر',   'ml',  'VOLUME', 1,       true, CURRENT_TIMESTAMP),
       (3, NULL, NULL, 'PIECE',      'Piece',      'حبة',      'pcs', 'COUNT',  1,       true, CURRENT_TIMESTAMP),
       (4, NULL, 1,    'KILOGRAM',   'Kilogram',   'كيلوجرام', 'kg',  'WEIGHT', 1000,    true, CURRENT_TIMESTAMP),
       (5, NULL, 1,    'TON',        'Ton',        'طن',       't',   'WEIGHT', 1000000, true, CURRENT_TIMESTAMP),
       (6, NULL, 2,    'LITRE',      'Litre',      'لتر',      'L',   'VOLUME', 1000,    true, CURRENT_TIMESTAMP);

SELECT setval('uom_id_seq', (SELECT MAX(id) FROM uom));

-- =====================================================================
-- Seed: Global material categories (tenant_id = NULL)
-- =====================================================================
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (1, 'VEGETABLES', 'Vegetables', true, 10, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (2, 'MEAT', 'Meat', true, 20, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (3, 'CHICKEN', 'Chicken', true, 30, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (4, 'SEAFOOD', 'Seafood', true, 40, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (5, 'DAIRY', 'Dairy', true, 50, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (6, 'BAKERY', 'Bakery', true, 60, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (7, 'SAUCES', 'Sauces', true, 70, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (8, 'SPICES', 'Spices', true, 80, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (9, 'PACKAGING', 'Packaging', true, 90, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (10, 'CLEANING', 'Cleaning', true, 100, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);
INSERT INTO public.material_category (id, code, name, active, sort_order, created_at, updated_at, tenant_id, name_ar, created_by, updated_by) VALUES (11, 'DRINKS', 'Drinks', true, 110, '2026-06-27 20:53:11.980177', NULL, NULL, NULL, NULL, NULL);

SELECT setval('material_category_id_seq', (SELECT MAX(id) FROM material_category));
