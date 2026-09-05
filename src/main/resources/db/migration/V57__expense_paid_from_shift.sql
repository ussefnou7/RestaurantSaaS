-- =====================================================================
-- O51 / D124: the manager links an expense to the shift whose drawer paid
-- for it. Purely additive -- an existing expense keeps a null link and
-- behaves exactly as before.
--
-- Attribution is the manager's explicit choice, never derived from
-- expense_date: that column is a DATE with no time (D118), so on a day
-- with three shifts it cannot identify one -- and if the date did drive
-- attribution, a manager could erase any shortfall by dating an expense
-- into the shift that has it.
--
-- Whether the linked shift's stored figures move is a separate question
-- from which shift is linked, and is decided by created_at vs the shift's
-- closed_at, not by this column.
-- =====================================================================

ALTER TABLE public.expense
    ADD COLUMN IF NOT EXISTS paid_from_shift_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expense_paid_from_shift') THEN
        ALTER TABLE public.expense
            ADD CONSTRAINT fk_expense_paid_from_shift
            FOREIGN KEY (paid_from_shift_id) REFERENCES public.shift(id);
    END IF;
END $$;

-- Reads go both ways: every expense on a shift (detail screen, and the
-- expectedCash term at close), and whether a given expense is linked.
CREATE INDEX IF NOT EXISTS idx_expense_paid_from_shift
    ON public.expense (paid_from_shift_id)
    WHERE paid_from_shift_id IS NOT NULL;

COMMENT ON COLUMN public.expense.paid_from_shift_id IS
    'Manager-selected shift whose drawer paid this expense (D124). Never inferred from expense_date.';
