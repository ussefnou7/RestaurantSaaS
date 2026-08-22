-- Supports bilingual UOM display by carrying an optional Arabic symbol beside symbol.
ALTER TABLE uom
    ADD COLUMN IF NOT EXISTS symbol_ar VARCHAR(50);

UPDATE uom
SET symbol_ar = CASE code
    WHEN 'GRAM' THEN 'جم'
    WHEN 'MILLILITRE' THEN 'مل'
    WHEN 'PIECE' THEN 'حبة'
    WHEN 'KILOGRAM' THEN 'كجم'
    WHEN 'TON' THEN 'طن'
    WHEN 'LITRE' THEN 'لتر'
    ELSE symbol_ar
END
WHERE tenant_id IS NULL
  AND symbol_ar IS NULL
  AND code IN ('GRAM', 'MILLILITRE', 'PIECE', 'KILOGRAM', 'TON', 'LITRE');
