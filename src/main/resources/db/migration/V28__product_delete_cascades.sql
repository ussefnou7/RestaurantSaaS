-- Product delete behavior:
-- - deleting a product deletes its recipes and recipe items
-- - deleting a product removes add-on link rows where it appears on either side
-- - deleting a parent product with variant children remains restricted

ALTER TABLE public.recipe
  DROP CONSTRAINT IF EXISTS fk_recipe_product,
  ADD CONSTRAINT fk_recipe_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE public.recipe_item
  DROP CONSTRAINT IF EXISTS fk_recipe_item_recipe,
  ADD CONSTRAINT fk_recipe_item_recipe
    FOREIGN KEY (recipe_id) REFERENCES public.recipe(id) ON DELETE CASCADE;

ALTER TABLE public.product_add_on
  DROP CONSTRAINT IF EXISTS fk_product_add_on_product,
  ADD CONSTRAINT fk_product_add_on_product
    FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE public.product_add_on
  DROP CONSTRAINT IF EXISTS fk_product_add_on_add_on_product,
  ADD CONSTRAINT fk_product_add_on_add_on_product
    FOREIGN KEY (add_on_product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE public.product
  DROP CONSTRAINT IF EXISTS fk_product_parent,
  ADD CONSTRAINT fk_product_parent
    FOREIGN KEY (parent_product_id) REFERENCES public.product(id) ON DELETE RESTRICT;
