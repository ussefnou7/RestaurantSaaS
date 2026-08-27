-- Supports bilingual menu category names without changing D16 tenant-level category ownership.

ALTER TABLE public.menu_category
    ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255);
