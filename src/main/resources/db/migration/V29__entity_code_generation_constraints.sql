-- Supports D75: backend-generated tenant entity codes remain unique per tenant.

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.material
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=material tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.material_category
     WHERE tenant_id IS NOT NULL
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=material_category tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.supplier
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=supplier tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.warehouse
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=warehouse tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.hr_employees
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=hr_employees tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

DO $$
DECLARE
    duplicate_row record;
BEGIN
    SELECT tenant_id, code, COUNT(*) AS duplicate_count
      INTO duplicate_row
      FROM public.jobs
     GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Duplicate tenant code blocks V29: table=jobs tenant_id=% code=% duplicate_count=%. Resolve duplicates before adding the unique index.',
            duplicate_row.tenant_id, duplicate_row.code, duplicate_row.duplicate_count;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_material_tenant_code
    ON public.material USING btree (tenant_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_material_category_tenant_code
    ON public.material_category USING btree (tenant_id, code)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_supplier_tenant_code
    ON public.supplier USING btree (tenant_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_warehouse_tenant_code
    ON public.warehouse USING btree (tenant_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_hr_employees_tenant_code
    ON public.hr_employees USING btree (tenant_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_jobs_tenant_code
    ON public.jobs USING btree (tenant_id, code);
