    -- =====================================================================
    -- Demo seed: materials + full menu (categories, products, sizes, recipes,
    -- add-ons) for TENANT ID 8.
    --
    -- MASTER DATA ONLY. It writes no warehouse stock: no opening balances, no
    -- inventory_transaction, no stock_balance, no stock_batch. Every product is
    -- orderable as far as the menu and recipe are concerned; the first order will
    -- simply consume against whatever stock you enter yourself.
    --
    -- TARGET TENANT: the id is written in exactly two places — the WHERE in
    -- section 0 and the RESET block below. Everything else (entity-code prefix,
    -- wall clock, acting user) is derived from that tenant's own row.
    --
    -- HOW TO RUN
    --   psql "postgresql://postgres:postgres@localhost:5432/restaurant-saas" \
    --        -v ON_ERROR_STOP=1 -f scripts/manual/seed_demo_menu.sql
    --
    -- WHAT IT RESPECTS (schema + service invariants, not just columns)
    --   * A variant child carries parent_product_id, both variant labels, and
    --     is_menu = FALSE (chk_product_variant_not_menu + ProductService).
    --   * A variant parent is a shell: no recipe, never orderable
    --     (RecipeService.PARENT_PRODUCT_HAS_NO_RECIPE). Its selling_price is
    --     only a placeholder; the POS shows min/max of its variants.
    --   * Every orderable product (standalone, variant child, add-on product)
    --     gets exactly one ACTIVE recipe — OrderService refuses a line whose
    --     product has none.
    --   * Add-on products are is_menu = FALSE standalones, linked through
    --     product_add_on to a TOP-LEVEL host (a variant child cannot host).
    --     The link is menu-side only: the POS sells the add-on as its own line,
    --     which is why each add-on carries its own recipe.
    --   * Product names are unique per tenant (ProductService.assertNameAvailable).
    --   * Entity codes come off the same counter the app uses
    --     (invoice_sequence, year = 0, doc_type = 'MAT'/'CAT'), so codes created
    --     later from the UI will not collide.
    --   * Timestamps are stamped in the TENANT's wall clock (D101).
    --   * Every material gets stock_uom = display_uom (kg / L / pcs), so a
    --     quantity means the same thing on every screen and in the ledger.
    --     Recipes are still written in grams / millilitres / pieces, which the
    --     consumption engine converts through the shared UOM root.
    --
    -- IT REFUSES TO RUN TWICE (product names would duplicate). To reset the
    -- seeded data, uncomment the RESET block below.
    -- =====================================================================

    BEGIN;

    -- ---------------------------------------------------------------------
    -- RESET (optional) — deletes ONLY the menu/material master data below.
    -- Will fail if orders or stock already reference these rows, which is correct.
    -- ---------------------------------------------------------------------
    -- DELETE FROM product_add_on WHERE tenant_id = 8;
    -- DELETE FROM recipe_item WHERE tenant_id = 8;
    -- DELETE FROM recipe      WHERE tenant_id = 8;
    -- DELETE FROM product     WHERE tenant_id = 8 AND parent_product_id IS NOT NULL;
    -- DELETE FROM product     WHERE tenant_id = 8;
    -- DELETE FROM menu_category WHERE tenant_id = 8;
    -- DELETE FROM material    WHERE tenant_id = 8;
    -- DELETE FROM material_category WHERE tenant_id = 8;
    -- DELETE FROM invoice_sequence WHERE tenant_id = 8 AND year = 0 AND doc_type IN ('MAT','CAT');


    -- =====================================================================
    -- 0. Context: tenant, acting user, tenant clock, code counters
    --    No warehouse is resolved — this script writes no stock.
    -- =====================================================================
    CREATE TEMP TABLE seed_ctx ON COMMIT DROP AS
    SELECT t.id                                        AS tenant_id,
           upper(t.code)                               AS tenant_code,
           (SELECT u.id FROM users u
             WHERE u.tenant_id = t.id ORDER BY u.id LIMIT 1) AS actor_id,
           date_trunc('second', now() AT TIME ZONE t.timezone)::timestamp AS ts,
           COALESCE((SELECT s.last_seq FROM invoice_sequence s
                      WHERE s.tenant_id = t.id AND s.year = 0 AND s.doc_type = 'CAT'), 0) AS cat_last,
           COALESCE((SELECT s.last_seq FROM invoice_sequence s
                      WHERE s.tenant_id = t.id AND s.year = 0 AND s.doc_type = 'MAT'), 0) AS mat_last
    FROM tenants t
    WHERE t.id = 8;                             -- <<< target tenant id

    DO $$
    DECLARE v_tenant bigint;
    BEGIN
        SELECT tenant_id INTO v_tenant FROM seed_ctx;
        IF v_tenant IS NULL THEN
            RAISE EXCEPTION 'Tenant 8 does not exist — create the tenant first';
        END IF;
        IF EXISTS (SELECT 1 FROM product WHERE tenant_id = v_tenant) THEN
            RAISE EXCEPTION 'Tenant % already has products. Run the RESET block at the top of this file first.', v_tenant;
        END IF;
        IF (SELECT actor_id FROM seed_ctx) IS NULL THEN
            RAISE EXCEPTION 'Tenant % has no user to attribute created_by to', v_tenant;
        END IF;
    END $$;


    -- =====================================================================
    -- 1. One tenant-owned material category (the global set has no dry goods)
    -- =====================================================================
    INSERT INTO material_category
           (tenant_id, code, name, name_ar, active, sort_order, created_at, created_by)
    SELECT c.tenant_id,
           c.tenant_code || '-CAT-' || lpad((c.cat_last + 1)::text, 4, '0'),
           'Dry Goods', 'بقالة جافة', TRUE, 120, c.ts, c.actor_id
    FROM seed_ctx c;

    INSERT INTO invoice_sequence (tenant_id, year, last_seq, doc_type)
    SELECT c.tenant_id, 0, c.cat_last + 1, 'CAT' FROM seed_ctx c
    ON CONFLICT (tenant_id, year, doc_type)
    DO UPDATE SET last_seq = GREATEST(invoice_sequence.last_seq, EXCLUDED.last_seq);


    -- =====================================================================
    -- 2. Materials
    --    cat_name matches material_category.name (global rows, plus the
    --    'Dry Goods' row created above). min_level is the material's reorder
    --    threshold — a master-data field, not a stock quantity.
    -- =====================================================================
    CREATE TEMP TABLE seed_material_src (
        ord       int PRIMARY KEY,
        cat_name  text NOT NULL,
        uom_code  text NOT NULL,
        name      text NOT NULL,
        name_ar   text NOT NULL,
        min_level numeric
    ) ON COMMIT DROP;

    INSERT INTO seed_material_src VALUES
     -- poultry
     ( 1, 'Chicken',    'KILOGRAM', 'Whole chicken',        'فرخة كاملة',            20),
     ( 2, 'Chicken',    'KILOGRAM', 'Chicken breast',       'صدور فراخ',             15),
     ( 3, 'Chicken',    'KILOGRAM', 'Chicken thigh',        'أوراك فراخ',            15),
     ( 4, 'Chicken',    'KILOGRAM', 'Chicken drumstick',    'دبابيس فراخ',           10),
     ( 5, 'Chicken',    'KILOGRAM', 'Chicken wings',        'أجنحة فراخ',             8),
     -- red meat
     ( 6, 'Meat',       'KILOGRAM', 'Minced beef',          'لحمة مفرومة',           10),
     ( 7, 'Meat',       'KILOGRAM', 'Beef cubes',           'لحمة كندوز مكعبات',      8),
     ( 8, 'Meat',       'KILOGRAM', 'Lamb chops',           'ريش ضاني',               5),
     ( 9, 'Meat',       'KILOGRAM', 'Beef sausage',         'سجق بلدي',               5),
     -- vegetables
     (10, 'Vegetables', 'KILOGRAM', 'Onion',                'بصل',                   10),
     (11, 'Vegetables', 'KILOGRAM', 'Tomato',               'طماطم',                 10),
     (12, 'Vegetables', 'KILOGRAM', 'Potato',               'بطاطس',                 20),
     (13, 'Vegetables', 'KILOGRAM', 'Green pepper',         'فلفل أخضر',              5),
     (14, 'Vegetables', 'KILOGRAM', 'Bell pepper',          'فلفل ألوان',             4),
     (15, 'Vegetables', 'KILOGRAM', 'Parsley',              'بقدونس',                 3),
     (16, 'Vegetables', 'KILOGRAM', 'Garlic',               'ثوم',                    3),
     (17, 'Vegetables', 'KILOGRAM', 'Lemon',                'ليمون',                  4),
     (18, 'Vegetables', 'KILOGRAM', 'Cucumber',             'خيار',                   5),
     (19, 'Vegetables', 'KILOGRAM', 'Lettuce',              'خس',                     4),
     -- dairy
     (20, 'Dairy',      'KILOGRAM', 'Mozzarella cheese',    'جبنة موتزاريلا',         5),
     (21, 'Dairy',      'KILOGRAM', 'Butter',               'زبدة',                   3),
     (22, 'Dairy',      'KILOGRAM', 'Yoghurt',              'زبادي',                  3),
     (23, 'Dairy',      'LITRE',    'Milk',                 'لبن',                    5),
     -- bakery
     (24, 'Bakery',     'PIECE',    'Baladi bread',         'عيش بلدي',             100),
     (25, 'Bakery',     'PIECE',    'Burger bun',           'خبز برجر',              50),
     (26, 'Bakery',     'PIECE',    'Shami bread',          'عيش شامي',              50),
     (27, 'Bakery',     'KILOGRAM', 'Breadcrumbs',          'بقسماط',                 5),
     -- sauces
     (28, 'Sauces',     'KILOGRAM', 'Tahina',               'طحينة',                  5),
     (29, 'Sauces',     'KILOGRAM', 'Mayonnaise',           'مايونيز',                5),
     (30, 'Sauces',     'KILOGRAM', 'Ketchup',              'كاتشب',                  5),
     (31, 'Sauces',     'KILOGRAM', 'BBQ sauce',            'صوص باربكيو',            3),
     (32, 'Sauces',     'KILOGRAM', 'Hot sauce',            'صوص شطة',                2),
     -- spices
     (33, 'Spices',     'KILOGRAM', 'Salt',                 'ملح',                    2),
     (34, 'Spices',     'KILOGRAM', 'Black pepper',         'فلفل أسود',              1),
     (35, 'Spices',     'KILOGRAM', 'Cumin',                'كمون',                   1),
     (36, 'Spices',     'KILOGRAM', 'Paprika',              'بابريكا',                1),
     (37, 'Spices',     'KILOGRAM', 'Mixed spices',         'بهارات مشكلة',           2),
     (38, 'Spices',     'KILOGRAM', 'Grill marinade mix',   'خلطة تسوية مشويات',      2),
     -- dry goods (tenant category)
     (39, 'Dry Goods',  'KILOGRAM', 'Egyptian rice',        'أرز مصري',              20),
     (40, 'Dry Goods',  'KILOGRAM', 'Vermicelli',           'شعرية',                  3),
     (41, 'Dry Goods',  'KILOGRAM', 'Penne pasta',          'مكرونة بنه',             5),
     (42, 'Dry Goods',  'KILOGRAM', 'Flour',                'دقيق',                  10),
     (43, 'Dry Goods',  'LITRE',    'Vegetable oil',        'زيت نباتي',             10),
     (44, 'Dry Goods',  'LITRE',    'Vinegar',              'خل',                     3),
     (45, 'Dry Goods',  'KILOGRAM', 'Charcoal',             'فحم',                   20),
     -- drinks
     (46, 'Drinks',     'PIECE',    'Cola can 330ml',       'كولا كانز 330 مل',      48),
     (47, 'Drinks',     'PIECE',    'Mineral water 600ml',  'مياه معدنية 600 مل',    48),
     (48, 'Drinks',     'LITRE',    'Orange juice',         'عصير برتقال',           10),
     -- packaging
     (49, 'Packaging',  'PIECE',    'Foam box large',       'علبة فوم كبيرة',       100),
     (50, 'Packaging',  'PIECE',    'Foam box small',       'علبة فوم صغيرة',       100),
     (51, 'Packaging',  'PIECE',    'Sauce cup',            'كوب صوص صغير',         200),
     (52, 'Packaging',  'PIECE',    'Plastic bag',          'كيس بلاستيك',          200),
     (53, 'Packaging',  'PIECE',    'Aluminium foil sheet', 'ورق ألومنيوم',         100),
     (54, 'Packaging',  'PIECE',    'Cutlery set',          'طقم أدوات مائدة',      200),
     (55, 'Packaging',  'PIECE',    'Drink cup',            'كوب مشروبات',          100);

    -- expiry_tracked stays FALSE for every row: switching it on makes the
    -- expiry/batch-age path expect expiry dates on every inbound batch.
    INSERT INTO material
           (tenant_id, category_id, stock_uom_id, display_uom_id, code, name, name_ar,
            active, expiry_tracked, minimum_stock_level, created_at, created_by)
    SELECT c.tenant_id, mc.id, u.id, u.id,
           c.tenant_code || '-MAT-' || lpad((c.mat_last + src.ord)::text, 4, '0'),
           src.name, src.name_ar, TRUE, FALSE, src.min_level, c.ts, c.actor_id
    FROM seed_material_src src
    CROSS JOIN seed_ctx c
    JOIN material_category mc
      ON mc.name = src.cat_name
     AND (mc.tenant_id IS NULL OR mc.tenant_id = c.tenant_id)
    JOIN uom u ON u.code = src.uom_code AND u.tenant_id IS NULL;

    INSERT INTO invoice_sequence (tenant_id, year, last_seq, doc_type)
    SELECT c.tenant_id, 0, c.mat_last + (SELECT max(ord) FROM seed_material_src), 'MAT' FROM seed_ctx c
    ON CONFLICT (tenant_id, year, doc_type)
    DO UPDATE SET last_seq = GREATEST(invoice_sequence.last_seq, EXCLUDED.last_seq);

    DO $$
    DECLARE v_expected int; v_actual int;
    BEGIN
        SELECT count(*) INTO v_expected FROM seed_material_src;
        SELECT count(*) INTO v_actual FROM material m, seed_ctx c WHERE m.tenant_id = c.tenant_id;
        IF v_expected <> v_actual THEN
            RAISE EXCEPTION 'Material seed dropped rows: expected %, inserted % (unresolved category or UOM)',
                v_expected, v_actual;
        END IF;
    END $$;


    -- =====================================================================
    -- 3. Menu categories
    -- =====================================================================
    INSERT INTO menu_category (tenant_id, name, name_ar, sort_order, is_active, created_at, created_by)
    SELECT c.tenant_id, v.name, v.name_ar, v.sort_order, TRUE, c.ts, c.actor_id
    FROM seed_ctx c,
         (VALUES ('Grills',                'مشويات',          10),
                 ('Fried & Broasted',      'فرايد وبروست',    20),
                 ('Rice & Pasta',          'أرز ومكرونة',     30),
                 ('Salads & Appetizers',   'سلطات ومقبلات',   40),
                 ('Drinks',                'مشروبات',         50),
                 ('Add-ons',               'إضافات',          60)
         ) AS v(name, name_ar, sort_order);


    -- =====================================================================
    -- 4. Products
    --    4a. menu items: variant parents + standalones (is_menu = TRUE)
    --    4b. add-on products (is_menu = FALSE, still standalone)
    --    4c. variant children (is_menu = FALSE, parent_product_id set)
    --    A parent's selling_price is a placeholder equal to its cheapest
    --    variant; the cashier screen derives min/max from the children.
    -- =====================================================================
    CREATE TEMP TABLE seed_product_src (
        ord      int PRIMARY KEY,
        cat_name text NOT NULL,          -- menu_category.name
        name     text NOT NULL,
        price    numeric NOT NULL,
        is_menu  boolean NOT NULL,
        descr    text,
        descr_ar text
    ) ON COMMIT DROP;

    INSERT INTO seed_product_src VALUES
     -- Grills
     ( 1, 'Grills', 'فراخ مشوية',         55,  TRUE, 'Charcoal-grilled marinated chicken',      'فرخة متبلة مشوية على الفحم'),
     ( 2, 'Grills', 'كفتة مشوية',         85,  TRUE, 'Grilled minced-beef kofta fingers',       'أصابع كفتة لحمة مشوية على الفحم'),
     ( 3, 'Grills', 'شيش طاووق',          70,  TRUE, 'Yoghurt-marinated chicken skewers',       'مكعبات صدور فراخ متبلة بالزبادي'),
     ( 4, 'Grills', 'كباب لحمة',         130,  TRUE, 'Grilled beef kebab skewers',              'مكعبات لحمة كندوز مشوية'),
     ( 5, 'Grills', 'ريش ضاني مشوية',    320,  TRUE, 'Grilled lamb chops',                      'ريش ضاني مشوية على الفحم'),
     ( 6, 'Grills', 'مشكل مشويات',       260,  TRUE, 'Mixed grill platter',                     'طبق مشويات مشكل للشخصين'),
     ( 7, 'Grills', 'سجق مشوي',           90,  TRUE, 'Grilled baladi sausage',                  'سجق بلدي مشوي'),
     -- Fried & Broasted
     ( 8, 'Fried & Broasted', 'بروست فراخ',     75,  TRUE, 'Crispy broasted chicken',            'فراخ بروست مقرمشة'),
     ( 9, 'Fried & Broasted', 'استربس فراخ',    80,  TRUE, 'Breaded chicken strips',             'استربس صدور فراخ مقرمشة'),
     (10, 'Fried & Broasted', 'بطاطس محمرة',    20,  TRUE, 'French fries',                       'بطاطس مقطعة ومحمرة'),
     (11, 'Fried & Broasted', 'أجنحة بافلو',    85,  TRUE, 'Buffalo chicken wings',              'أجنحة فراخ بصوص البافلو'),
     -- Rice & Pasta
     (12, 'Rice & Pasta', 'أرز بالشعرية',    20,  TRUE, 'Egyptian rice with vermicelli',         'أرز مصري بالشعرية'),
     (13, 'Rice & Pasta', 'أرز بالخلطة',     45,  TRUE, 'Rice with mixed vegetables and butter', 'أرز بالخلطة والزبدة'),
     (14, 'Rice & Pasta', 'مكرونة بشاميل',   55,  TRUE, 'Pasta bechamel with minced beef',       'مكرونة بشاميل باللحمة المفرومة'),
     -- Salads & Appetizers
     (15, 'Salads & Appetizers', 'سلطة طحينة',        15, TRUE, 'Tahina dip',                    'سلطة طحينة'),
     (16, 'Salads & Appetizers', 'سلطة بلدي',         18, TRUE, 'Baladi green salad',            'سلطة خضراء بلدي'),
     (17, 'Salads & Appetizers', 'سلطة زبادي بالخيار',20, TRUE, 'Cucumber yoghurt salad',        'زبادي بالخيار والثوم'),
     (18, 'Salads & Appetizers', 'عيش بلدي',           3, TRUE, 'Baladi bread',                  'رغيفين عيش بلدي'),
     -- Drinks
     (19, 'Drinks', 'كولا كانز',          20, TRUE, 'Cola can 330ml',           'كولا كانز 330 مل'),
     (20, 'Drinks', 'مياه معدنية',        10, TRUE, 'Mineral water 600ml',      'مياه معدنية 600 مل'),
     (21, 'Drinks', 'عصير برتقال فريش',   25, TRUE, 'Fresh orange juice',       'عصير برتقال طازج'),
     -- Add-ons: sold as their own order line, hidden from the menu grid
     (22, 'Add-ons', 'إضافة جبنة موتزاريلا', 15, FALSE, 'Extra mozzarella',     'جبنة موتزاريلا إضافية'),
     (23, 'Add-ons', 'إضافة صوص باربكيو',     8, FALSE, 'Extra BBQ sauce',      'صوص باربكيو إضافي'),
     (24, 'Add-ons', 'إضافة صوص ثومية',       8, FALSE, 'Extra garlic sauce',   'صوص ثومية إضافي'),
     (25, 'Add-ons', 'إضافة شطة',             5, FALSE, 'Extra hot sauce',      'شطة إضافية'),
     (26, 'Add-ons', 'إضافة عيش زيادة',       5, FALSE, 'Extra bread',          'عيش بلدي إضافي');

    INSERT INTO product
           (tenant_id, menu_category_id, name, description, description_ar,
            selling_price, is_active, is_menu, created_at, created_by)
    SELECT c.tenant_id, mc.id, src.name, src.descr, src.descr_ar,
           src.price, TRUE, src.is_menu, c.ts, c.actor_id
    FROM seed_product_src src
    CROSS JOIN seed_ctx c
    JOIN menu_category mc ON mc.tenant_id = c.tenant_id AND mc.name = src.cat_name;

    -- Variant children. child_name is spelled out (not derived) because the
    -- recipe rows in section 6 join on it.
    CREATE TEMP TABLE seed_variant_src (
        ord         int PRIMARY KEY,
        parent_name text NOT NULL,
        child_name  text NOT NULL,
        label       text NOT NULL,
        label_ar    text NOT NULL,
        price       numeric NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO seed_variant_src VALUES
     ( 1, 'فراخ مشوية',       'فراخ مشوية - ربع',            'Quarter',    'ربع',          55),
     ( 2, 'فراخ مشوية',       'فراخ مشوية - نصف',            'Half',       'نصف',          95),
     ( 3, 'فراخ مشوية',       'فراخ مشوية - فرخة كاملة',     'Whole',      'فرخة كاملة',  180),
     ( 4, 'كفتة مشوية',       'كفتة مشوية - 4 أصابع',        '4 Fingers',  '4 أصابع',      85),
     ( 5, 'كفتة مشوية',       'كفتة مشوية - 8 أصابع',        '8 Fingers',  '8 أصابع',     160),
     ( 6, 'شيش طاووق',        'شيش طاووق - سيخ',             '1 Skewer',   'سيخ',          70),
     ( 7, 'شيش طاووق',        'شيش طاووق - سيخين',           '2 Skewers',  'سيخين',       130),
     ( 8, 'كباب لحمة',        'كباب لحمة - سيخ',             '1 Skewer',   'سيخ',         130),
     ( 9, 'كباب لحمة',        'كباب لحمة - سيخين',           '2 Skewers',  'سيخين',       240),
     (10, 'بروست فراخ',       'بروست فراخ - 3 قطع',          '3 Pieces',   '3 قطع',        75),
     (11, 'بروست فراخ',       'بروست فراخ - 6 قطع',          '6 Pieces',   '6 قطع',       140),
     (12, 'بروست فراخ',       'بروست فراخ - 9 قطع',          '9 Pieces',   '9 قطع',       200),
     (13, 'استربس فراخ',      'استربس فراخ - 4 قطع',         '4 Pieces',   '4 قطع',        80),
     (14, 'استربس فراخ',      'استربس فراخ - 6 قطع',         '6 Pieces',   '6 قطع',       115),
     (15, 'بطاطس محمرة',      'بطاطس محمرة - صغير',          'Small',      'صغير',         20),
     (16, 'بطاطس محمرة',      'بطاطس محمرة - وسط',           'Medium',     'وسط',          30),
     (17, 'بطاطس محمرة',      'بطاطس محمرة - كبير',          'Large',      'كبير',         40),
     (18, 'أرز بالشعرية',     'أرز بالشعرية - وسط',          'Medium',     'وسط',          20),
     (19, 'أرز بالشعرية',     'أرز بالشعرية - كبير',         'Large',      'كبير',         35),
     (20, 'عصير برتقال فريش', 'عصير برتقال فريش - صغير',     'Small',      'صغير',         25),
     (21, 'عصير برتقال فريش', 'عصير برتقال فريش - كبير',     'Large',      'كبير',         40);

    INSERT INTO product
           (tenant_id, menu_category_id, name, selling_price, is_active, is_menu,
            parent_product_id, variant_label, variant_label_ar, created_at, created_by)
    SELECT c.tenant_id, parent.menu_category_id, src.child_name, src.price, TRUE, FALSE,
           parent.id, src.label, src.label_ar, c.ts, c.actor_id
    FROM seed_variant_src src
    CROSS JOIN seed_ctx c
    JOIN product parent ON parent.tenant_id = c.tenant_id AND parent.name = src.parent_name;


    -- =====================================================================
    -- 5. One ACTIVE recipe per orderable product
    --    Orderable = not a variant parent. Parents are deliberately skipped.
    -- =====================================================================
    INSERT INTO recipe (tenant_id, product_id, is_active, created_at, created_by)
    SELECT c.tenant_id, p.id, TRUE, c.ts, c.actor_id
    FROM product p
    JOIN seed_ctx c ON c.tenant_id = p.tenant_id
    WHERE NOT EXISTS (SELECT 1 FROM product child WHERE child.parent_product_id = p.id);


    -- =====================================================================
    -- 6. Recipe lines
    --    Quantities are in the recipe's own UOM (g / ml / pcs); the consumption
    --    engine converts to each material's stock UOM through the shared root.
    --    Packaging and charcoal are recipe lines too — that is what makes the
    --    consumption report reflect real cost per dish.
    -- =====================================================================
    CREATE TEMP TABLE seed_recipe_src (
        product_name  text NOT NULL,
        material_name text NOT NULL,     -- material.name_ar
        qty           numeric NOT NULL,
        uom_code      text NOT NULL,
        PRIMARY KEY (product_name, material_name)
    ) ON COMMIT DROP;

    INSERT INTO seed_recipe_src VALUES
     -- فراخ مشوية
     ('فراخ مشوية - ربع', 'فرخة كاملة', 300, 'GRAM'),
     ('فراخ مشوية - ربع', 'خلطة تسوية مشويات', 8, 'GRAM'),
     ('فراخ مشوية - ربع', 'ملح', 3, 'GRAM'),
     ('فراخ مشوية - ربع', 'ليمون', 20, 'GRAM'),
     ('فراخ مشوية - ربع', 'ثوم', 5, 'GRAM'),
     ('فراخ مشوية - ربع', 'فحم', 120, 'GRAM'),
     ('فراخ مشوية - ربع', 'ورق ألومنيوم', 1, 'PIECE'),
     ('فراخ مشوية - ربع', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('فراخ مشوية - ربع', 'كيس بلاستيك', 1, 'PIECE'),
     ('فراخ مشوية - ربع', 'طقم أدوات مائدة', 1, 'PIECE'),
     ('فراخ مشوية - نصف', 'فرخة كاملة', 600, 'GRAM'),
     ('فراخ مشوية - نصف', 'خلطة تسوية مشويات', 15, 'GRAM'),
     ('فراخ مشوية - نصف', 'ملح', 5, 'GRAM'),
     ('فراخ مشوية - نصف', 'ليمون', 30, 'GRAM'),
     ('فراخ مشوية - نصف', 'ثوم', 8, 'GRAM'),
     ('فراخ مشوية - نصف', 'فحم', 200, 'GRAM'),
     ('فراخ مشوية - نصف', 'ورق ألومنيوم', 1, 'PIECE'),
     ('فراخ مشوية - نصف', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('فراخ مشوية - نصف', 'كيس بلاستيك', 1, 'PIECE'),
     ('فراخ مشوية - نصف', 'طقم أدوات مائدة', 1, 'PIECE'),
     ('فراخ مشوية - فرخة كاملة', 'فرخة كاملة', 1200, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'خلطة تسوية مشويات', 25, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'ملح', 8, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'ليمون', 50, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'ثوم', 15, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'فحم', 350, 'GRAM'),
     ('فراخ مشوية - فرخة كاملة', 'ورق ألومنيوم', 2, 'PIECE'),
     ('فراخ مشوية - فرخة كاملة', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('فراخ مشوية - فرخة كاملة', 'كيس بلاستيك', 1, 'PIECE'),
     ('فراخ مشوية - فرخة كاملة', 'طقم أدوات مائدة', 2, 'PIECE'),
     -- كفتة مشوية
     ('كفتة مشوية - 4 أصابع', 'لحمة مفرومة', 250, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'بصل', 40, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'بقدونس', 15, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'بهارات مشكلة', 6, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'ملح', 3, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'بقسماط', 20, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'فحم', 120, 'GRAM'),
     ('كفتة مشوية - 4 أصابع', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('كفتة مشوية - 4 أصابع', 'كيس بلاستيك', 1, 'PIECE'),
     ('كفتة مشوية - 4 أصابع', 'طقم أدوات مائدة', 1, 'PIECE'),
     ('كفتة مشوية - 8 أصابع', 'لحمة مفرومة', 500, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'بصل', 70, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'بقدونس', 25, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'بهارات مشكلة', 10, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'ملح', 5, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'بقسماط', 35, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'فحم', 200, 'GRAM'),
     ('كفتة مشوية - 8 أصابع', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('كفتة مشوية - 8 أصابع', 'كيس بلاستيك', 1, 'PIECE'),
     ('كفتة مشوية - 8 أصابع', 'طقم أدوات مائدة', 1, 'PIECE'),
     -- شيش طاووق
     ('شيش طاووق - سيخ', 'صدور فراخ', 220, 'GRAM'),
     ('شيش طاووق - سيخ', 'زبادي', 40, 'GRAM'),
     ('شيش طاووق - سيخ', 'ليمون', 15, 'GRAM'),
     ('شيش طاووق - سيخ', 'ثوم', 6, 'GRAM'),
     ('شيش طاووق - سيخ', 'بابريكا', 4, 'GRAM'),
     ('شيش طاووق - سيخ', 'ملح', 3, 'GRAM'),
     ('شيش طاووق - سيخ', 'فلفل ألوان', 40, 'GRAM'),
     ('شيش طاووق - سيخ', 'بصل', 30, 'GRAM'),
     ('شيش طاووق - سيخ', 'فحم', 120, 'GRAM'),
     ('شيش طاووق - سيخ', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('شيش طاووق - سيخ', 'كيس بلاستيك', 1, 'PIECE'),
     ('شيش طاووق - سيخ', 'طقم أدوات مائدة', 1, 'PIECE'),
     ('شيش طاووق - سيخين', 'صدور فراخ', 420, 'GRAM'),
     ('شيش طاووق - سيخين', 'زبادي', 70, 'GRAM'),
     ('شيش طاووق - سيخين', 'ليمون', 25, 'GRAM'),
     ('شيش طاووق - سيخين', 'ثوم', 10, 'GRAM'),
     ('شيش طاووق - سيخين', 'بابريكا', 7, 'GRAM'),
     ('شيش طاووق - سيخين', 'ملح', 5, 'GRAM'),
     ('شيش طاووق - سيخين', 'فلفل ألوان', 70, 'GRAM'),
     ('شيش طاووق - سيخين', 'بصل', 50, 'GRAM'),
     ('شيش طاووق - سيخين', 'فحم', 200, 'GRAM'),
     ('شيش طاووق - سيخين', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('شيش طاووق - سيخين', 'كيس بلاستيك', 1, 'PIECE'),
     ('شيش طاووق - سيخين', 'طقم أدوات مائدة', 1, 'PIECE'),
     -- كباب لحمة
     ('كباب لحمة - سيخ', 'لحمة كندوز مكعبات', 220, 'GRAM'),
     ('كباب لحمة - سيخ', 'بصل', 40, 'GRAM'),
     ('كباب لحمة - سيخ', 'فلفل أخضر', 30, 'GRAM'),
     ('كباب لحمة - سيخ', 'بهارات مشكلة', 6, 'GRAM'),
     ('كباب لحمة - سيخ', 'ملح', 3, 'GRAM'),
     ('كباب لحمة - سيخ', 'فحم', 130, 'GRAM'),
     ('كباب لحمة - سيخ', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('كباب لحمة - سيخ', 'كيس بلاستيك', 1, 'PIECE'),
     ('كباب لحمة - سيخ', 'طقم أدوات مائدة', 1, 'PIECE'),
     ('كباب لحمة - سيخين', 'لحمة كندوز مكعبات', 420, 'GRAM'),
     ('كباب لحمة - سيخين', 'بصل', 70, 'GRAM'),
     ('كباب لحمة - سيخين', 'فلفل أخضر', 50, 'GRAM'),
     ('كباب لحمة - سيخين', 'بهارات مشكلة', 10, 'GRAM'),
     ('كباب لحمة - سيخين', 'ملح', 5, 'GRAM'),
     ('كباب لحمة - سيخين', 'فحم', 220, 'GRAM'),
     ('كباب لحمة - سيخين', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('كباب لحمة - سيخين', 'كيس بلاستيك', 1, 'PIECE'),
     ('كباب لحمة - سيخين', 'طقم أدوات مائدة', 1, 'PIECE'),
     -- ريش ضاني مشوية
     ('ريش ضاني مشوية', 'ريش ضاني', 450, 'GRAM'),
     ('ريش ضاني مشوية', 'ملح', 5, 'GRAM'),
     ('ريش ضاني مشوية', 'فلفل أسود', 3, 'GRAM'),
     ('ريش ضاني مشوية', 'كمون', 3, 'GRAM'),
     ('ريش ضاني مشوية', 'ثوم', 8, 'GRAM'),
     ('ريش ضاني مشوية', 'فحم', 250, 'GRAM'),
     ('ريش ضاني مشوية', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('ريش ضاني مشوية', 'كيس بلاستيك', 1, 'PIECE'),
     ('ريش ضاني مشوية', 'طقم أدوات مائدة', 1, 'PIECE'),
     -- مشكل مشويات
     ('مشكل مشويات', 'صدور فراخ', 150, 'GRAM'),
     ('مشكل مشويات', 'لحمة مفرومة', 150, 'GRAM'),
     ('مشكل مشويات', 'لحمة كندوز مكعبات', 150, 'GRAM'),
     ('مشكل مشويات', 'سجق بلدي', 100, 'GRAM'),
     ('مشكل مشويات', 'بهارات مشكلة', 10, 'GRAM'),
     ('مشكل مشويات', 'ملح', 5, 'GRAM'),
     ('مشكل مشويات', 'بصل', 50, 'GRAM'),
     ('مشكل مشويات', 'فلفل ألوان', 40, 'GRAM'),
     ('مشكل مشويات', 'فحم', 300, 'GRAM'),
     ('مشكل مشويات', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('مشكل مشويات', 'كيس بلاستيك', 1, 'PIECE'),
     ('مشكل مشويات', 'طقم أدوات مائدة', 2, 'PIECE'),
     -- سجق مشوي
     ('سجق مشوي', 'سجق بلدي', 300, 'GRAM'),
     ('سجق مشوي', 'ملح', 2, 'GRAM'),
     ('سجق مشوي', 'بهارات مشكلة', 4, 'GRAM'),
     ('سجق مشوي', 'فحم', 120, 'GRAM'),
     ('سجق مشوي', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('سجق مشوي', 'كيس بلاستيك', 1, 'PIECE'),
     ('سجق مشوي', 'طقم أدوات مائدة', 1, 'PIECE'),
     -- بروست فراخ
     ('بروست فراخ - 3 قطع', 'دبابيس فراخ', 330, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'دقيق', 80, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'لبن', 60, 'MILLILITRE'),
     ('بروست فراخ - 3 قطع', 'بقسماط', 40, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'زيت نباتي', 90, 'MILLILITRE'),
     ('بروست فراخ - 3 قطع', 'ملح', 4, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'بابريكا', 5, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'فلفل أسود', 2, 'GRAM'),
     ('بروست فراخ - 3 قطع', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('بروست فراخ - 3 قطع', 'كيس بلاستيك', 1, 'PIECE'),
     ('بروست فراخ - 3 قطع', 'كوب صوص صغير', 1, 'PIECE'),
     ('بروست فراخ - 6 قطع', 'دبابيس فراخ', 660, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'دقيق', 150, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'لبن', 110, 'MILLILITRE'),
     ('بروست فراخ - 6 قطع', 'بقسماط', 70, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'زيت نباتي', 160, 'MILLILITRE'),
     ('بروست فراخ - 6 قطع', 'ملح', 7, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'بابريكا', 9, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'فلفل أسود', 4, 'GRAM'),
     ('بروست فراخ - 6 قطع', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('بروست فراخ - 6 قطع', 'كيس بلاستيك', 1, 'PIECE'),
     ('بروست فراخ - 6 قطع', 'كوب صوص صغير', 2, 'PIECE'),
     ('بروست فراخ - 9 قطع', 'دبابيس فراخ', 990, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'دقيق', 220, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'لبن', 160, 'MILLILITRE'),
     ('بروست فراخ - 9 قطع', 'بقسماط', 100, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'زيت نباتي', 230, 'MILLILITRE'),
     ('بروست فراخ - 9 قطع', 'ملح', 10, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'بابريكا', 13, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'فلفل أسود', 6, 'GRAM'),
     ('بروست فراخ - 9 قطع', 'علبة فوم كبيرة', 1, 'PIECE'),
     ('بروست فراخ - 9 قطع', 'كيس بلاستيك', 1, 'PIECE'),
     ('بروست فراخ - 9 قطع', 'كوب صوص صغير', 3, 'PIECE'),
     -- استربس فراخ
     ('استربس فراخ - 4 قطع', 'صدور فراخ', 200, 'GRAM'),
     ('استربس فراخ - 4 قطع', 'دقيق', 60, 'GRAM'),
     ('استربس فراخ - 4 قطع', 'لبن', 50, 'MILLILITRE'),
     ('استربس فراخ - 4 قطع', 'بقسماط', 40, 'GRAM'),
     ('استربس فراخ - 4 قطع', 'زيت نباتي', 80, 'MILLILITRE'),
     ('استربس فراخ - 4 قطع', 'ملح', 3, 'GRAM'),
     ('استربس فراخ - 4 قطع', 'بابريكا', 3, 'GRAM'),
     ('استربس فراخ - 4 قطع', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('استربس فراخ - 4 قطع', 'كيس بلاستيك', 1, 'PIECE'),
     ('استربس فراخ - 4 قطع', 'كوب صوص صغير', 1, 'PIECE'),
     ('استربس فراخ - 6 قطع', 'صدور فراخ', 300, 'GRAM'),
     ('استربس فراخ - 6 قطع', 'دقيق', 80, 'GRAM'),
     ('استربس فراخ - 6 قطع', 'لبن', 70, 'MILLILITRE'),
     ('استربس فراخ - 6 قطع', 'بقسماط', 55, 'GRAM'),
     ('استربس فراخ - 6 قطع', 'زيت نباتي', 110, 'MILLILITRE'),
     ('استربس فراخ - 6 قطع', 'ملح', 4, 'GRAM'),
     ('استربس فراخ - 6 قطع', 'بابريكا', 4, 'GRAM'),
     ('استربس فراخ - 6 قطع', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('استربس فراخ - 6 قطع', 'كيس بلاستيك', 1, 'PIECE'),
     ('استربس فراخ - 6 قطع', 'كوب صوص صغير', 1, 'PIECE'),
     -- بطاطس محمرة
     ('بطاطس محمرة - صغير', 'بطاطس', 200, 'GRAM'),
     ('بطاطس محمرة - صغير', 'زيت نباتي', 40, 'MILLILITRE'),
     ('بطاطس محمرة - صغير', 'ملح', 2, 'GRAM'),
     ('بطاطس محمرة - صغير', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('بطاطس محمرة - وسط', 'بطاطس', 300, 'GRAM'),
     ('بطاطس محمرة - وسط', 'زيت نباتي', 55, 'MILLILITRE'),
     ('بطاطس محمرة - وسط', 'ملح', 3, 'GRAM'),
     ('بطاطس محمرة - وسط', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('بطاطس محمرة - كبير', 'بطاطس', 450, 'GRAM'),
     ('بطاطس محمرة - كبير', 'زيت نباتي', 80, 'MILLILITRE'),
     ('بطاطس محمرة - كبير', 'ملح', 4, 'GRAM'),
     ('بطاطس محمرة - كبير', 'علبة فوم كبيرة', 1, 'PIECE'),
     -- أجنحة بافلو
     ('أجنحة بافلو', 'أجنحة فراخ', 350, 'GRAM'),
     ('أجنحة بافلو', 'صوص شطة', 50, 'GRAM'),
     ('أجنحة بافلو', 'زبدة', 20, 'GRAM'),
     ('أجنحة بافلو', 'دقيق', 40, 'GRAM'),
     ('أجنحة بافلو', 'زيت نباتي', 90, 'MILLILITRE'),
     ('أجنحة بافلو', 'ملح', 3, 'GRAM'),
     ('أجنحة بافلو', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('أجنحة بافلو', 'كوب صوص صغير', 1, 'PIECE'),
     -- أرز
     ('أرز بالشعرية - وسط', 'أرز مصري', 120, 'GRAM'),
     ('أرز بالشعرية - وسط', 'شعرية', 15, 'GRAM'),
     ('أرز بالشعرية - وسط', 'زيت نباتي', 15, 'MILLILITRE'),
     ('أرز بالشعرية - وسط', 'ملح', 2, 'GRAM'),
     ('أرز بالشعرية - وسط', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('أرز بالشعرية - كبير', 'أرز مصري', 200, 'GRAM'),
     ('أرز بالشعرية - كبير', 'شعرية', 25, 'GRAM'),
     ('أرز بالشعرية - كبير', 'زيت نباتي', 25, 'MILLILITRE'),
     ('أرز بالشعرية - كبير', 'ملح', 3, 'GRAM'),
     ('أرز بالشعرية - كبير', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('أرز بالخلطة', 'أرز مصري', 180, 'GRAM'),
     ('أرز بالخلطة', 'شعرية', 20, 'GRAM'),
     ('أرز بالخلطة', 'زبدة', 15, 'GRAM'),
     ('أرز بالخلطة', 'بهارات مشكلة', 3, 'GRAM'),
     ('أرز بالخلطة', 'ملح', 2, 'GRAM'),
     ('أرز بالخلطة', 'فلفل ألوان', 30, 'GRAM'),
     ('أرز بالخلطة', 'بصل', 20, 'GRAM'),
     ('أرز بالخلطة', 'علبة فوم كبيرة', 1, 'PIECE'),
     -- مكرونة بشاميل
     ('مكرونة بشاميل', 'مكرونة بنه', 200, 'GRAM'),
     ('مكرونة بشاميل', 'لحمة مفرومة', 120, 'GRAM'),
     ('مكرونة بشاميل', 'لبن', 200, 'MILLILITRE'),
     ('مكرونة بشاميل', 'دقيق', 30, 'GRAM'),
     ('مكرونة بشاميل', 'زبدة', 25, 'GRAM'),
     ('مكرونة بشاميل', 'جبنة موتزاريلا', 60, 'GRAM'),
     ('مكرونة بشاميل', 'بصل', 30, 'GRAM'),
     ('مكرونة بشاميل', 'ملح', 3, 'GRAM'),
     ('مكرونة بشاميل', 'فلفل أسود', 2, 'GRAM'),
     ('مكرونة بشاميل', 'علبة فوم كبيرة', 1, 'PIECE'),
     -- سلطات ومقبلات
     ('سلطة طحينة', 'طحينة', 60, 'GRAM'),
     ('سلطة طحينة', 'ليمون', 15, 'GRAM'),
     ('سلطة طحينة', 'ثوم', 4, 'GRAM'),
     ('سلطة طحينة', 'ملح', 1, 'GRAM'),
     ('سلطة طحينة', 'خل', 5, 'MILLILITRE'),
     ('سلطة طحينة', 'كوب صوص صغير', 1, 'PIECE'),
     ('سلطة بلدي', 'طماطم', 100, 'GRAM'),
     ('سلطة بلدي', 'خيار', 80, 'GRAM'),
     ('سلطة بلدي', 'خس', 50, 'GRAM'),
     ('سلطة بلدي', 'بصل', 20, 'GRAM'),
     ('سلطة بلدي', 'ليمون', 10, 'GRAM'),
     ('سلطة بلدي', 'ملح', 1, 'GRAM'),
     ('سلطة بلدي', 'زيت نباتي', 5, 'MILLILITRE'),
     ('سلطة بلدي', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('سلطة زبادي بالخيار', 'زبادي', 120, 'GRAM'),
     ('سلطة زبادي بالخيار', 'خيار', 60, 'GRAM'),
     ('سلطة زبادي بالخيار', 'ثوم', 3, 'GRAM'),
     ('سلطة زبادي بالخيار', 'ملح', 1, 'GRAM'),
     ('سلطة زبادي بالخيار', 'علبة فوم صغيرة', 1, 'PIECE'),
     ('عيش بلدي', 'عيش بلدي', 2, 'PIECE'),
     -- مشروبات
     ('كولا كانز', 'كولا كانز 330 مل', 1, 'PIECE'),
     ('مياه معدنية', 'مياه معدنية 600 مل', 1, 'PIECE'),
     ('عصير برتقال فريش - صغير', 'عصير برتقال', 250, 'MILLILITRE'),
     ('عصير برتقال فريش - صغير', 'كوب مشروبات', 1, 'PIECE'),
     ('عصير برتقال فريش - كبير', 'عصير برتقال', 400, 'MILLILITRE'),
     ('عصير برتقال فريش - كبير', 'كوب مشروبات', 1, 'PIECE'),
     -- إضافات
     ('إضافة جبنة موتزاريلا', 'جبنة موتزاريلا', 60, 'GRAM'),
     ('إضافة صوص باربكيو', 'صوص باربكيو', 40, 'GRAM'),
     ('إضافة صوص باربكيو', 'كوب صوص صغير', 1, 'PIECE'),
     ('إضافة صوص ثومية', 'مايونيز', 40, 'GRAM'),
     ('إضافة صوص ثومية', 'ثوم', 5, 'GRAM'),
     ('إضافة صوص ثومية', 'ليمون', 5, 'GRAM'),
     ('إضافة صوص ثومية', 'كوب صوص صغير', 1, 'PIECE'),
     ('إضافة شطة', 'صوص شطة', 30, 'GRAM'),
     ('إضافة شطة', 'كوب صوص صغير', 1, 'PIECE'),
     ('إضافة عيش زيادة', 'عيش بلدي', 2, 'PIECE');

    INSERT INTO recipe_item
           (tenant_id, recipe_id, material_id, quantity, uom_id, created_at, created_by)
    SELECT c.tenant_id, r.id, m.id, src.qty, u.id, c.ts, c.actor_id
    FROM seed_recipe_src src
    CROSS JOIN seed_ctx c
    JOIN product p  ON p.tenant_id = c.tenant_id AND p.name = src.product_name
    JOIN recipe  r  ON r.tenant_id = c.tenant_id AND r.product_id = p.id AND r.is_active
    JOIN material m ON m.tenant_id = c.tenant_id AND m.name_ar = src.material_name
    JOIN uom u      ON u.code = src.uom_code AND u.tenant_id IS NULL;


    -- =====================================================================
    -- 7. Add-on suggestion links (host must be a top-level product)
    -- =====================================================================
    INSERT INTO product_add_on (tenant_id, product_id, add_on_product_id, created_at, created_by)
    SELECT c.tenant_id, host.id, addon.id, c.ts, c.actor_id
    FROM seed_ctx c
    CROSS JOIN (VALUES ('فراخ مشوية',        'إضافة جبنة موتزاريلا'),
                 ('فراخ مشوية',        'إضافة صوص باربكيو'),
                 ('فراخ مشوية',        'إضافة صوص ثومية'),
                 ('فراخ مشوية',        'إضافة عيش زيادة'),
                 ('كفتة مشوية',        'إضافة صوص ثومية'),
                 ('كفتة مشوية',        'إضافة شطة'),
                 ('كفتة مشوية',        'إضافة عيش زيادة'),
                 ('شيش طاووق',         'إضافة جبنة موتزاريلا'),
                 ('شيش طاووق',         'إضافة صوص ثومية'),
                 ('شيش طاووق',         'إضافة صوص باربكيو'),
                 ('كباب لحمة',         'إضافة شطة'),
                 ('كباب لحمة',         'إضافة عيش زيادة'),
                 ('ريش ضاني مشوية',    'إضافة صوص ثومية'),
                 ('مشكل مشويات',       'إضافة صوص ثومية'),
                 ('مشكل مشويات',       'إضافة عيش زيادة'),
                 ('سجق مشوي',          'إضافة شطة'),
                 ('سجق مشوي',          'إضافة عيش زيادة'),
                 ('بروست فراخ',        'إضافة صوص باربكيو'),
                 ('بروست فراخ',        'إضافة صوص ثومية'),
                 ('بروست فراخ',        'إضافة شطة'),
                 ('استربس فراخ',       'إضافة صوص باربكيو'),
                 ('استربس فراخ',       'إضافة صوص ثومية'),
                 ('بطاطس محمرة',       'إضافة جبنة موتزاريلا'),
                 ('بطاطس محمرة',       'إضافة شطة'),
                 ('أجنحة بافلو',       'إضافة صوص ثومية'),
                 ('أجنحة بافلو',       'إضافة شطة')
         ) AS v(host_name, addon_name)
    JOIN product host  ON host.tenant_id  = c.tenant_id AND host.name  = v.host_name
    JOIN product addon ON addon.tenant_id = c.tenant_id AND addon.name = v.addon_name;


    -- =====================================================================
    -- 8. Verification — every one of these would otherwise fail silently
    --    (a mistyped name just drops the row from an inner join).
    -- =====================================================================
    DO $$
    DECLARE
        v_tenant   bigint;
        v_expected int;
        v_actual   int;
        v_bad      text;
    BEGIN
        SELECT tenant_id INTO v_tenant FROM seed_ctx;

        -- every recipe line landed
        SELECT count(*) INTO v_expected FROM seed_recipe_src;
        SELECT count(*) INTO v_actual   FROM recipe_item WHERE tenant_id = v_tenant;
        IF v_expected <> v_actual THEN
            RAISE EXCEPTION 'Recipe lines dropped: expected %, inserted % — a product or material name did not match',
                v_expected, v_actual;
        END IF;

        -- every product landed
        SELECT (SELECT count(*) FROM seed_product_src) + (SELECT count(*) FROM seed_variant_src)
          INTO v_expected;
        SELECT count(*) INTO v_actual FROM product WHERE tenant_id = v_tenant;
        IF v_expected <> v_actual THEN
            RAISE EXCEPTION 'Products dropped: expected %, inserted %', v_expected, v_actual;
        END IF;

        -- no variant parent carries a recipe (RecipeService forbids it)
        SELECT string_agg(p.name, ', ') INTO v_bad
        FROM product p
        WHERE p.tenant_id = v_tenant
          AND EXISTS (SELECT 1 FROM product ch WHERE ch.parent_product_id = p.id)
          AND EXISTS (SELECT 1 FROM recipe r WHERE r.product_id = p.id);
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'Variant parents must not carry a recipe: %', v_bad;
        END IF;

        -- every orderable product has an active recipe with at least one line
        SELECT string_agg(p.name, ', ') INTO v_bad
        FROM product p
        WHERE p.tenant_id = v_tenant
          AND NOT EXISTS (SELECT 1 FROM product ch WHERE ch.parent_product_id = p.id)
          AND NOT EXISTS (
                SELECT 1 FROM recipe r
                JOIN recipe_item ri ON ri.recipe_id = r.id
                WHERE r.product_id = p.id AND r.is_active);
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'Orderable products without an active non-empty recipe: %', v_bad;
        END IF;

        -- exactly one active recipe per product
        IF EXISTS (
            SELECT 1 FROM recipe WHERE tenant_id = v_tenant AND is_active
            GROUP BY product_id HAVING count(*) > 1) THEN
            RAISE EXCEPTION 'More than one active recipe for a product';
        END IF;

        -- every recipe UOM is convertible to its material's stock UOM
        SELECT string_agg(DISTINCT m.name_ar, ', ') INTO v_bad
        FROM recipe_item ri
        JOIN material m ON m.id = ri.material_id
        JOIN uom ru ON ru.id = ri.uom_id
        JOIN uom su ON su.id = m.stock_uom_id
        WHERE ri.tenant_id = v_tenant
          AND COALESCE(ru.base_uom_id, ru.id) <> COALESCE(su.base_uom_id, su.id);
        IF v_bad IS NOT NULL THEN
            RAISE EXCEPTION 'Recipe UOM not convertible to stock UOM for: %', v_bad;
        END IF;

        -- every add-on host is top-level, and every add-on is hidden from the grid
        IF EXISTS (SELECT 1 FROM product_add_on ao JOIN product h ON h.id = ao.product_id
                    WHERE ao.tenant_id = v_tenant AND h.parent_product_id IS NOT NULL) THEN
            RAISE EXCEPTION 'An add-on host is a variant child';
        END IF;

        -- master data only: nothing may have touched stock
        IF EXISTS (SELECT 1 FROM stock_balance WHERE tenant_id = v_tenant)
           OR EXISTS (SELECT 1 FROM stock_batch WHERE tenant_id = v_tenant)
           OR EXISTS (SELECT 1 FROM inventory_transaction WHERE tenant_id = v_tenant) THEN
            RAISE EXCEPTION 'This script must not create stock rows, yet tenant % has some', v_tenant;
        END IF;

        RAISE NOTICE 'Seed OK — tenant %: % materials, % menu categories, % products (% menu items, % variants, % add-ons), % recipes, % recipe lines, % add-on links. No stock written.',
            v_tenant,
            (SELECT count(*) FROM material WHERE tenant_id = v_tenant),
            (SELECT count(*) FROM menu_category WHERE tenant_id = v_tenant),
            (SELECT count(*) FROM product WHERE tenant_id = v_tenant),
            (SELECT count(*) FROM product WHERE tenant_id = v_tenant AND is_menu),
            (SELECT count(*) FROM product WHERE tenant_id = v_tenant AND parent_product_id IS NOT NULL),
            (SELECT count(*) FROM product WHERE tenant_id = v_tenant AND NOT is_menu AND parent_product_id IS NULL),
            (SELECT count(*) FROM recipe WHERE tenant_id = v_tenant),
            (SELECT count(*) FROM recipe_item WHERE tenant_id = v_tenant),
            (SELECT count(*) FROM product_add_on WHERE tenant_id = v_tenant);
    END $$;

    COMMIT;
