-- Supersedes D116 with D137: expense categories are tenant-owned and never seeded globally.

-- Preserve history in environments that already used a global category. Reuse an existing
-- same-name tenant category when possible; otherwise create one before moving the expenses.
INSERT INTO public.expense_category
    (tenant_id, name, name_ar, active, created_at)
SELECT DISTINCT
    e.tenant_id,
    global_category.name,
    global_category.name_ar,
    global_category.active,
    CURRENT_TIMESTAMP
FROM public.expense e
JOIN public.expense_category global_category
  ON global_category.id = e.category_id
 AND global_category.tenant_id IS NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM public.expense_category tenant_category
    WHERE tenant_category.tenant_id = e.tenant_id
      AND LOWER(tenant_category.name) = LOWER(global_category.name)
);

WITH category_mapping AS (
    SELECT
        e.tenant_id,
        global_category.id AS global_category_id,
        MIN(tenant_category.id) AS tenant_category_id
    FROM public.expense e
    JOIN public.expense_category global_category
      ON global_category.id = e.category_id
     AND global_category.tenant_id IS NULL
    JOIN public.expense_category tenant_category
      ON tenant_category.tenant_id = e.tenant_id
     AND LOWER(tenant_category.name) = LOWER(global_category.name)
    GROUP BY e.tenant_id, global_category.id
)
UPDATE public.expense expense
SET category_id = category_mapping.tenant_category_id
FROM category_mapping
WHERE expense.tenant_id = category_mapping.tenant_id
  AND expense.category_id = category_mapping.global_category_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.expense expense
        JOIN public.expense_category category ON category.id = expense.category_id
        WHERE category.tenant_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot remove global expense categories while expenses still reference them';
    END IF;
END $$;

DELETE FROM public.expense_category WHERE tenant_id IS NULL;

DROP INDEX IF EXISTS public.uk_expense_category_global_name;
DROP INDEX IF EXISTS public.uk_expense_category_tenant_name;

ALTER TABLE public.expense_category
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_expense_category_tenant_name
    ON public.expense_category (tenant_id, LOWER(name));
