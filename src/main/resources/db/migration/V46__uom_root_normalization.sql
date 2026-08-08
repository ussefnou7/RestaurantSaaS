-- =====================================================================
-- UOM root normalization.
--
-- Uom is a self-referencing tree (base_uom_id + factor_to_base) with no
-- conversion table (D13). UomConversionService.baseUomId() reads exactly one
-- level, which encodes an unwritten invariant: the tree is two levels deep.
-- V6 honours it (GRAM root, KILOGRAM -> GRAM x1000, TON -> GRAM x1000000);
-- nothing on the write path ever enforced it.
--
-- A tenant UOM created with base_uom_id = KILOGRAM and factor_to_base = 25
-- therefore resolves to base KILOGRAM while KILOGRAM itself resolves to GRAM,
-- so sameBaseUom() fails and the unit converts to nothing -- not even its own
-- parent. Independently, factor_to_base means "how many of the root": 25 was
-- typed meaning kilograms and would have been read as 25 grams, a silent
-- 1000x error written to the ledger rather than an exception.
--
-- The fix is to flatten on write, not to make the reader recursive.
-- physicalConvert never needed the root as a step -- it multiplies by the
-- source factor and divides by the target factor, and neither operand has to
-- be a root (KILOGRAM -> TON works today; neither is a root). What the shared
-- root guarantees is a shared calibration point: dividing factor by factor is
-- only meaningful when both were measured from the same zero.
--
-- entered_factor / entered_against_uom_id preserve what the user actually
-- typed, so the edit form can render "25 per KILOGRAM" while the engine keeps
-- using the root-calibrated 25000.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. The entered pair.
-- ---------------------------------------------------------------------
ALTER TABLE public.uom ADD COLUMN entered_factor numeric(18,6);
ALTER TABLE public.uom ADD COLUMN entered_against_uom_id bigint REFERENCES public.uom(id);

-- Backfilled BEFORE flattening: the entered pair must capture the values as
-- they were typed, which is exactly the pre-flatten state. Rows that are
-- already flat get an identity copy; roots keep a NULL parent and factor 1.
UPDATE public.uom
SET entered_factor         = factor_to_base,
    entered_against_uom_id = base_uom_id;

-- ---------------------------------------------------------------------
-- 2. Flatten every chain onto its root.
--
-- One pass collapses one level, so repeat until a pass changes nothing.
-- The cap is a cycle detector, not a tuning knob: §0's recursive check found
-- no cycles, and ck_uom_no_self_base below blocks the trivial one, but a cycle
-- reaching this loop would spin forever. Raising beats hanging, and beats
-- silently stopping with half-flattened factors.
--
-- Ids are never reassigned and no row is inserted or deleted, so all 16
-- foreign keys pointing at uom stay valid throughout.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    affected  bigint;
    iteration integer := 0;
BEGIN
    LOOP
        UPDATE public.uom AS child
        SET factor_to_base = child.factor_to_base * parent.factor_to_base,
            base_uom_id    = parent.base_uom_id
        FROM public.uom AS parent
        WHERE child.base_uom_id = parent.id
          AND parent.base_uom_id IS NOT NULL;

        GET DIAGNOSTICS affected = ROW_COUNT;
        EXIT WHEN affected = 0;

        iteration := iteration + 1;
        IF iteration >= 10 THEN
            RAISE EXCEPTION
                'uom flattening did not converge after % iterations - a cycle survived the pre-migration check',
                iteration;
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 3. Constraints that keep the tree flat.
-- ---------------------------------------------------------------------
ALTER TABLE public.uom ALTER COLUMN entered_factor SET NOT NULL;

-- A root is the calibration point for its type, so its factor is 1 by
-- definition. This also rejects the row shape the sysadmin panel produces
-- (O29): that form posts baseCode as a String where the DTO declares baseUom
-- as a Long id, Jackson drops it, and the row lands as a claimed root carrying
-- a real factor. Failing loudly at the database is the correct outcome on a
-- sysadmin-only screen.
ALTER TABLE public.uom ADD CONSTRAINT ck_uom_root_factor CHECK (
    (base_uom_id IS NULL AND factor_to_base = 1)
    OR
    (base_uom_id IS NOT NULL AND factor_to_base > 0)
);

ALTER TABLE public.uom ADD CONSTRAINT ck_uom_no_self_base CHECK (
    base_uom_id IS NULL OR base_uom_id <> id
);

-- entered_against_uom_id stays nullable: roots have no parent to have been
-- entered against.
