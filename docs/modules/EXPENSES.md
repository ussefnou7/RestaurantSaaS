# Expenses module

The Expenses module is built under `com.smart.restaurant_saas.expense` from D115-D118. It records
flat, immediately real payments, with append-only correction through a reasoned void.

> **Anything that enters a warehouse has a purchase document, not an expense.**
> An expense is money that left with no stock behind it.

This boundary prevents material purchases from being counted once as inventory/COGS and again as
an expense. The module does not write the inventory ledger and has no dependency on inventory
core.

## Model

- `Expense`: tenant-owned; optional branch; one category, amount, expense date, payment source,
  optional description/payee, optional explicitly selected `paidFromShiftId`, source identity,
  status, void trace, and audit timestamps.
- `ExpenseCategory`: global seeded defaults plus tenant-owned rows. Global rows are read-only;
  tenant rows may be created, renamed, activated, and deactivated. Inactive rows remain readable.
- Expense states are only `ACTIVE` and `VOIDED`; there is no draft/post lifecycle, update, delete,
  or document number.
- Amount is positive `NUMERIC(18,6)`. Business dates are tenant/branch-local; audit timestamps are
  stamped by `TenantTimestampListener`.

## API and permissions

- `/api/expenses`: paginated list, get, create, and `POST /{id}/void`.
- `/api/expenses/selectable-shifts`: recent OPEN and CLOSED shifts for the selected branch/date
  window, used by both admin-web expense creation surfaces. Closed shifts remain selectable and
  are labelled as links that will not rewrite the recorded close variance.
- `/api/expense-categories`: list, create, update, activate, and deactivate.
- Reads use `EXPENSES_VIEW`; writes split across `EXPENSES_CREATE`, `EXPENSES_VOID`, and
  `EXPENSES_CATEGORY_MANAGE`.

The exact wire contract and runtime examples are in
[`claude/CONTRACT_EXPENSES_API.md`](../../claude/CONTRACT_EXPENSES_API.md).

O16 remains open for P&L, COGS timing, and Fixed Assets exclusion. O48-O50 remain deferred: asset
maintenance posting, attachments, and payroll posting are not part of this schema. O51 is
partially implemented: the manager-selected shift link, picker, frozen close expense total, and
late-expense read model exist. Coordinated expense-create/shift-close ordering is still an open
release finding; a frozen column alone does not prove that race is closed.
