-- =====================================================================
-- D58: dual-trigger order-consumption batching.
-- Adds the ShedLock table backing the single-instance batching scheduler.
-- No changes to the order_consumption / order_consumption_line shape are needed —
-- the trigger reads the existing PENDING doc's line count + age; no new columns.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
