-- =====================================================================
-- Advisory stock-shortfall warnings stored as a JSON column on
-- waste_document. Computed once at COMPLETE time; travels with the
-- document row so GET issues zero extra queries — empty list or
-- populated, cost is identical.
--
-- NOTE — first (and intentionally only) JSON column in this schema.
-- Used here because stock_warnings is point-in-time descriptive data
-- that has no independent query need and is never filtered/joined on.
-- Future child data with independent query needs should still use a
-- proper table per the established project convention.
-- =====================================================================

ALTER TABLE public.waste_document
    ADD COLUMN IF NOT EXISTS stock_warnings jsonb;
