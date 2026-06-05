DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'material_catalog'
          AND column_name = 'default_uom_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'material_catalog'
          AND column_name = 'default_stock_uom_id'
    ) THEN
        ALTER TABLE material_catalog
            RENAME COLUMN default_uom_id TO default_stock_uom_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'material'
          AND column_name = 'default_uom_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'material'
          AND column_name = 'stock_uom_id'
    ) THEN
        ALTER TABLE material
            RENAME COLUMN default_uom_id TO stock_uom_id;
    END IF;
END $$;

ALTER TABLE material_catalog
    ADD COLUMN IF NOT EXISTS default_display_uom_id BIGINT;

ALTER TABLE material
    ADD COLUMN IF NOT EXISTS display_uom_id BIGINT;

UPDATE material_catalog catalog
SET default_display_uom_id = COALESCE(
        CASE stock_uom.code
            WHEN 'GRAM' THEN kg.id
            WHEN 'ML' THEN liter.id
            WHEN 'PCS' THEN pcs.id
            ELSE NULL
        END,
        catalog.default_stock_uom_id
    )
FROM uom stock_uom
LEFT JOIN uom kg ON kg.code = 'KG'
LEFT JOIN uom liter ON liter.code = 'LITER'
LEFT JOIN uom pcs ON pcs.code = 'PCS'
WHERE catalog.default_stock_uom_id = stock_uom.id
  AND catalog.default_display_uom_id IS NULL;

UPDATE material material
SET display_uom_id = COALESCE(catalog.default_display_uom_id, material.stock_uom_id)
FROM material_catalog catalog
WHERE material.catalog_id = catalog.id
  AND material.display_uom_id IS NULL;

UPDATE material material
SET display_uom_id = COALESCE(
        CASE stock_uom.code
            WHEN 'GRAM' THEN kg.id
            WHEN 'ML' THEN liter.id
            WHEN 'PCS' THEN pcs.id
            ELSE NULL
        END,
        material.stock_uom_id
    )
FROM uom stock_uom
LEFT JOIN uom kg ON kg.code = 'KG'
LEFT JOIN uom liter ON liter.code = 'LITER'
LEFT JOIN uom pcs ON pcs.code = 'PCS'
WHERE material.stock_uom_id = stock_uom.id
  AND material.display_uom_id IS NULL;

ALTER TABLE material_catalog
    ALTER COLUMN default_stock_uom_id SET NOT NULL,
    ALTER COLUMN default_display_uom_id SET NOT NULL;

ALTER TABLE material
    ALTER COLUMN stock_uom_id SET NOT NULL,
    ALTER COLUMN display_uom_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_material_catalog_default_display_uom'
    ) THEN
        ALTER TABLE material_catalog
            ADD CONSTRAINT fk_material_catalog_default_display_uom
            FOREIGN KEY (default_display_uom_id) REFERENCES uom(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_material_display_uom'
    ) THEN
        ALTER TABLE material
            ADD CONSTRAINT fk_material_display_uom
            FOREIGN KEY (display_uom_id) REFERENCES uom(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_material_catalog_default_stock_uom
    ON material_catalog (default_stock_uom_id);

CREATE INDEX IF NOT EXISTS idx_material_catalog_default_display_uom
    ON material_catalog (default_display_uom_id);

CREATE INDEX IF NOT EXISTS idx_material_tenant_stock_uom
    ON material (tenant_id, stock_uom_id);

CREATE INDEX IF NOT EXISTS idx_material_tenant_display_uom
    ON material (tenant_id, display_uom_id);
