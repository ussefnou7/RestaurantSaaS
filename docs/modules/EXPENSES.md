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
  optional description/payee, source identity, status, void trace, and audit timestamps.
- `ExpenseCategory`: global seeded defaults plus tenant-owned rows. Global rows are read-only;
  tenant rows may be created, renamed, activated, and deactivated. Inactive rows remain readable.
- Expense states are only `ACTIVE` and `VOIDED`; there is no draft/post lifecycle, update, delete,
  or document number.
- Amount is positive `NUMERIC(18,6)`. Business dates are tenant/branch-local; audit timestamps are
  stamped by `TenantTimestampListener`.

## API and permissions

- `/api/expenses`: paginated list, get, create, and `POST /{id}/void`.
- `/api/expense-categories`: list, create, update, activate, and deactivate.
- Reads use `EXPENSES_VIEW`; writes split across `EXPENSES_CREATE`, `EXPENSES_VOID`, and
  `EXPENSES_CATEGORY_MANAGE`.

The exact wire contract and runtime examples are in
[`claude/CONTRACT_EXPENSES_API.md`](../../claude/CONTRACT_EXPENSES_API.md).

O16 remains open for P&L, COGS timing, and Fixed Assets exclusion. O48-O51 remain deferred: asset
maintenance posting, attachments, payroll posting, and shift linkage are not anticipated in this
schema.
