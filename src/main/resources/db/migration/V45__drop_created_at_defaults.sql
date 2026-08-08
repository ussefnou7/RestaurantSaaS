-- =====================================================================
-- D101 / O33 — remove DEFAULT CURRENT_TIMESTAMP from every created_at column.
--
-- The reason is testability, not purity. created_at is stamped by
-- TenantTimestampListener in the owning tenant's zone. With a DB default in
-- place, "the listener ran correctly" and "the listener never ran, so the
-- server's clock filled it in" produce rows that look equally plausible and
-- cannot be told apart after the fact. Without the default, a listener that
-- does not run is an immediate NOT NULL violation — loud, at the point of the
-- bug, instead of a quietly wrong timestamp discovered months later in a report.
--
-- NOT NULL is deliberately KEPT. Dropping the default without it would just
-- trade a wrong value for a null one.
--
-- Safe with respect to the seed data. Ten seed INSERTs across V1, V2, V12,
-- V16, V17, V18, V21, V30 and V34 insert permissions/roles/recipe rows without
-- naming created_at and so rely on this default — but every one of them runs at
-- its own version, long before this migration. Flyway applies V1..V44 first,
-- so those rows already exist and are already stamped. The constraint this adds
-- is forward-looking: a NEW migration that inserts without created_at will fail,
-- which is the intended behaviour.
--
-- Driven off information_schema rather than a hand-written list of ~50 tables:
-- the enumeration cannot drift, and dropping a default that is not there is a
-- no-op, which makes the whole block idempotent.
--
-- V11__waste.sql already declared created_at with no default. This generalises
-- that precedent rather than inventing a new rule.
-- =====================================================================

DO $$
DECLARE
    target record;
BEGIN
    FOR target IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'created_at'
          AND c.column_default IS NOT NULL
          AND t.table_type = 'BASE TABLE'
          -- Flyway owns its own history table; never touch it.
          AND c.table_name <> 'flyway_schema_history'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.%I ALTER COLUMN created_at DROP DEFAULT',
            target.table_name);
    END LOOP;
END $$;
