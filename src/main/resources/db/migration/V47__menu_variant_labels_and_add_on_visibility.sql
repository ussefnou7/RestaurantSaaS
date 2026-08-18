-- Supports D103/D105: product-level menu visibility and bilingual labels for legacy variants.

UPDATE public.product
SET is_menu = FALSE
WHERE id = 24
  AND name = 'اضافة جبن';

UPDATE public.product
SET variant_label = CASE id
        WHEN 22 THEN 'Small'
        WHEN 16 THEN 'Medium'
        WHEN 23 THEN 'Large'
    END,
    variant_label_ar = CASE id
        WHEN 22 THEN 'صغير'
        WHEN 16 THEN 'وسط'
        WHEN 23 THEN 'كبير'
    END
WHERE id IN (16, 22, 23)
  AND parent_product_id = 21;
