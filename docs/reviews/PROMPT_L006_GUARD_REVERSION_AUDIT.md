# PROMPT — L006: guard-reversion audit

> For **Opus**, in a **dedicated session**. Repo: `restaurant-saas` (backend), with
> `restaurant-pos` second. Ledger row **L006**, open since the ledger was created and never
> attempted. Self-contained — you do not need any prior session's context.

## The question this answers

A passing test suite proves the code does what the tests say. It does **not** prove the tests would
notice if a protection were removed. This audit establishes, guard by guard, that **each protection
has at least one test that fails when the protection is deleted.**

Everything else in this project's QA has measured the first thing. This measures the second.

## Why it is not optional here

The project's own history is the argument. From `MVP_HARDENING_PLAN.md`: a commented-out guard
survived **22 days and 33 commits** behind a suite that looked green. ROADMAP records the request
for this audit; it has never been run. Meanwhile 129 backend test files are cited as evidence of
safety in decisions being made right now.

## Method

For each guard in scope:

1. Disable it — comment the throw, invert the condition, drop the `@PreAuthorize`, remove the
   constraint. One guard at a time.
2. Run the suite.
3. Record: **which test failed, or none did.**
4. Restore the guard. Verify the suite is green again before moving on.

A guard with no failing test is a finding. A guard whose only failing test asserts something
unrelated is also a finding — note the distinction.

> Script step 1 if you can, but do not batch: two guards disabled at once gives an ambiguous result.

## Scope, in priority order

Work down this list and stop when the session's budget runs out rather than thinning the coverage.

1. **Money and stock invariants** — the engine that was verified behaviourally this week and whose
   guards have never been reversion-tested:
   - `InventoryLedgerService` as sole writer to `inventory_transaction`;
     `StockBalanceService` to `stock_balance`; `StockBatchService` to `stock_batch`
   - FIFO batch ordering and `BATCH_SHORTFALL`
   - `UNPOST_BLOCKED_BATCH_CONSUMED`
   - average cost derived from **OPEN batches only**
   - `ShiftService.closeShift` — the row lock at `:163` and the already-closed check at `:174`
   - the frozen `expensesAtClose` at `:189-190`
2. **Authorization** — every `@PreAuthorize` on an MVP controller. There are ~26 in `hr/` alone,
   all verified present this week but none reversion-tested. Include
   `SecurityService.isSysAdmin()` and the `SHIFTS_VIEW_VARIANCE` gating in
   `ShiftQueryService:65,75`, which hides seven response fields.
3. **Tenant isolation** — every repository method that filters on `tenant_id`. Removing the filter
   from any one of them should fail a test. `tenant/CrossTenantIsolationIntegrationTest` and
   `tenant/support/CrossTenantFixture` are the existing harness.
4. **Document state machines** — `INVALID_STATE_TRANSITION` on purchase invoices, returns, waste
   and physical counts.

## Two findings to resolve on the way, since they live in the same place

Both are ledger rows blocked on exactly the kind of test this audit builds:

- **L007** — competing shift closes. The row lock exists and held under two concurrent `curl`
  requests, but parallel curl cannot be shown to interleave inside the critical window, so that is
  weak evidence. A deterministic latch-based test is needed. **Copy the existing pattern:**
  `auth/refresh/RefreshTokenConcurrencyIntegrationTest` and
  `inventory/core/DocumentSequencePropagationIntegrationTest` both do this correctly — two
  `CountDownLatch`es, `@SpringBootTest` **without** `@Transactional`, assertions on `JdbcTemplate`
  state rather than on the thrown exception.
- **L008** — the expense/close race. `ShiftService.closeShift` freezes the sum at `:189-190` under
  the shift row lock, while `ExpenseService.validatePaidFromShift:236` reads the shift with the
  **unlocked** `findByIdAndTenantId`. An expense committing between `sumActiveByShift` and the write
  of `closedAt` is excluded from the frozen sum **and** classified as recorded before close. Both
  halves missed.

## Two constraints that will bite

- **34 integration tests use `@Transactional`**, which serialises everything and makes a race
  impossible to observe. Any concurrency test you write must not.
- **`@SpringBootTest` runs against the configured development database** — ledger row L018, no
  Testcontainers. Use a throwaway database; `restaurant_saas_mvp` was built from empty by Flyway on
  2026-10-03 and is disposable.

## You are the finder here, not the fixer

Record what has no coverage. Write the **missing tests** — that is the deliverable. Do not fix the
guards themselves unless a reversion reveals one that is already broken, and if that happens, file
it as its own ledger row rather than folding it in.

## Deliverable

1. A table in `claude/GUARD_REVERSION_AUDIT.md`: guard · file:line · test that failed · verdict.
2. New tests for every guard that had none, prioritised by the order above.
3. L007 and L008 closed with real concurrency evidence, or a written explanation of why not.
4. One ledger row per uncovered guard class — not one row for the whole audit.
