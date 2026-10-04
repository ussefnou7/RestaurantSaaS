# Guard-reversion audit — ledger L006

**Run:** 2026-10-03, branch `codex/mvp-e2e-fixes`, database `restaurant_saas_test`.
**Baseline:** 950 tests in 130 classes, green, 46 s for the full suite.

## The question this answers

A passing suite proves the code does what the tests say. It does not prove a test would notice if a
protection were deleted. Every other QA pass on this project has measured the first thing. This one
measures the second, guard by guard: disable one protection, run the whole suite, record which test
failed — or that none did.

The project's own history is the argument for doing it. `MVP_HARDENING_PLAN.md` records a
commented-out guard that survived 22 days and 33 commits behind a suite that looked green. 129
backend test files are currently cited as evidence of safety in live decisions.

## Method

`scripts/audit/guard_reversion.py` drives it. For each guard: apply a one-line source mutation that
disables it, run `./mvnw -o test`, parse every surefire report for testcase-level failures, revert
with `git checkout --`, record. The harness refuses to start if the target file is already dirty,
and reverts in a `finally` block, so a crashed run cannot leave a guard disabled.

**One guard at a time, never batched** — two disabled at once gives an ambiguous result. Case files
live in `scripts/audit/cases/`. Anchors are either an exact source substring that must occur exactly
once, or a line number plus a substring that line must contain, so a case file fails loudly rather
than silently mutating the wrong line after an edit above it.

Verdicts:

- **COVERED** — at least one test failed, and it asserts the guard's own behaviour.
- **COVERED (unrelated)** — a test failed for a reason that is not the protection. Recorded as a
  finding; see S01.
- **UNCOVERED** — the entire suite passed with the protection deleted.

## Priority 1 — money and stock invariants

15 guards. **5 UNCOVERED, 1 covered only for an unrelated reason.**

| # | Guard | file:line | Test that failed | Verdict |
|---|---|---|---|---|
| M01 | `BATCH_SHORTFALL` — a purchase return may not take more than its source batch still holds | `StockBatchService.java:312` | *none* | **UNCOVERED** |
| M02 | `BATCH_SHORTFALL` — reversing an inbound movement may not reverse consumed stock | `StockBatchService.java:246` | *none* | **UNCOVERED** |
| M03 | `BATCH_SHORTFALL` — a return unpost may not restore past the original batch quantity | `StockBatchService.java:349` | *none* | **UNCOVERED** |
| M04 | FIFO batch ordering — oldest movement date first, id as tiebreaker | `StockBatchService.java:183` | `StockBatchOrderingIntegrationTest.consumesOlderMovementDateBeforeLowerId` (+2) | COVERED |
| M05 | FIFO shortfall — unmatched remainder valued at the pre-movement average | `StockBatchService.java:211` | `StockBatchServiceTest.consumeFifoValuesShortfallRemainderAtBalanceAverageCost` | COVERED |
| M06 | Average cost derived from OPEN batches' **remaining** quantity only | `StockBatchRepository.java:83-91` | *none* | **UNCOVERED** |
| M07 | `UNPOST_BLOCKED_BATCH_CONSUMED` — cannot unpost an invoice whose batches were consumed | `PurchaseInvoiceService.java:513` | `PurchaseInvoiceServiceTest.unpostRejectsWhenAnyBatchWasConsumedBeforeReversing` | COVERED |
| M08 | `requireSourceBatch` fails loudly when a return line has no traceable source batch | `StockBatchService.java:277` | *none* | **UNCOVERED** |
| S01 | `closeShift` takes the shift row lock before deciding already-closed | `ShiftService.java:163` | `ShiftServiceTest.closeShift_alreadyClosed_isRejected` (+5) | **COVERED (unrelated)** |
| S02 | `closeShift` rejects an already-CLOSED shift | `ShiftService.java:174` | `ShiftIntegrationTest.aClosedShiftCannotBeClosedAgain` | COVERED |
| S03 | `closeShift` stores the frozen expense sum as `expensesAtClose` | `ShiftService.java:190` | `ShiftServiceTest.closeShift_storesExpectedCashAndVarianceButReturnsNeither` | COVERED |
| S04 | `expectedCash = opening + cashSales − expenses` | `ShiftService.java:192` | `ShiftServiceTest.closeShift_storesExpectedCashAndVarianceButReturnsNeither` | COVERED |
| S05 | A drawer may only close its own shift | `ShiftService.java:168` | `ShiftServiceTest.closeShift_shiftOnAnotherDevice_isNotFound` | COVERED |
| V01 | `SHIFTS_VIEW_VARIANCE` gates the variance fields on the shift **list** | `ShiftQueryService.java:65` | `ShiftIntegrationTest.withoutViewVariancePermissionTheListOmitsTheFiguresEntirely` | COVERED |
| V02 | `SHIFTS_VIEW_VARIANCE` gates variance fields and order/expense rows on shift **detail** | `ShiftQueryService.java:75` | `ShiftQueryServiceTest.findById_withoutVariancePermission_returnsNoOrdersNoExpensesAndNoCounts` (+2) | COVERED |

### The shape of the gap

Three of the three `BATCH_SHORTFALL` throws were uncovered — every guard standing between a purchase
return and stock leaving a batch that no longer holds it. The pattern is not random: all three live
in `StockBatchService`, whose existing test file covers the *happy* paths of the same methods
(`restoreSourceBatchIsCommutativeForMultipleReturnsOnSameBatch` exercises `restoreSourceBatch`
thoroughly and never once asks it to refuse). Coverage followed the code paths that succeed.

M06 was invisible for a structural reason worth noting: the formula lives in JPQL, and
`StockBalanceServiceTest` mocks the repository — so the query no assertion ever executed could be
rewritten freely. Average cost is the valuation basis for FIFO shortfall remainders, physical-count
surplus batches, and the `unitCostAtFreeze` a count stamps, so a wrong basis misprices all three.
The two formulas also agree exactly until stock is consumed, which is why a test has to consume
before asserting.

### S01 — covered, but for the wrong reason

This is the finding the audit exists to surface, and it would not show up in any coverage report.

Swapping `findByIdAndTenantIdForUpdate` for the unlocked `findByIdAndTenantId` turns six
`ShiftServiceTest` tests red. None of them asserts anything about locking. `ShiftServiceTest` is a
pure Mockito test that *stubs* `findByIdAndTenantIdForUpdate`; once the production code calls a
different method the stub stops matching, the mock returns `Optional.empty()`, and every close test
fails with "shift not found". The tests that go red assert "an already-closed shift is rejected" and
"forced close is recorded" — statements about state, not about serialisation.

So the row lock's actual property, that a second close cannot overwrite the first cashier's counted
figure, had **zero** coverage. That is exactly the premise of ledger L007, independently confirmed.
`ShiftCloseConcurrencyIntegrationTest` now covers it.

**Generalisation:** any guard whose reversion is detected only by mock-based tests is suspect. A
mock asserts the call was made; it cannot assert the call did its job.

### Prose-only invariants — no guard to disable

Three invariants in scope turned out not to be guards at all. They are sentences in javadoc, with
nothing executable behind them, so there is nothing to revert and no test could ever fail:

- `InventoryLedgerService` as **sole writer** to `inventory_transaction` — asserted in its class
  javadoc ("The sole writer to the inventory_transaction table"), enforced by nothing.
- `StockBatchService` as sole writer to `stock_batch` — same.
- `@TenantUnscoped` — a `RetentionPolicy.SOURCE` annotation documenting every repository method that
  deliberately omits a tenant filter. No test reads it, so a new unscoped method that simply omits
  the annotation is caught by nothing.

This is the weakest class of protection in the codebase, and it sits on the ledger every stock figure
derives from. `LedgerSoleWriterArchitectureTest` now enforces the first two by reading the source
tree — the invariant is about which code may write, which no runtime test can establish totally.

**A correction fell out of writing it.** `StockBalanceService` is *not* the sole writer to
`stock_balance`: `PurchaseInvoiceService`, `PurchaseReturnService`, `PhysicalCountService` and
`StockBalanceAverageCostBackfill` all call `save`/`saveAll` on it. What they write is denormalised
metadata (`lastPurchasePrice`, `lastPurchaseDate`, `lastCountDate`, `lastCountQuantity`) and never
`quantity` or `averageCost`. The enforceable invariant is therefore about those two **fields**, not
about the table, and the documented claim overstates what is true.

## L007 — competing shift closes: CLOSED, with evidence

`ShiftCloseConcurrencyIntegrationTest` (new). The lock holds.

The previous E2E attempt fired two closes as parallel `curl` requests and got the right answer, but
nothing showed the two ever overlapped inside the window between reading the shift and writing
`closedAt`. If they ran back to back the lock was never exercised. The new test constructs the
overlap instead of hoping for it: the first close is impersonated by a transaction that writes the
shift row and then stops on a `CountDownLatch`, holding its row lock. The second close provably runs
while the first is in flight and uncommitted.

The interleaving proof is that the second request is **still blocked after 300 ms** — it reached the
critical window and waited. The load-bearing assertion is the stored `closing_count`: with the lock
removed, the second close reads the stale OPEN row, passes the already-closed check, and overwrites
the first cashier's figure, so `777` becomes `999` and the test fails even in the variant where the
second request still blocks later at flush time.

Result: second close gets `409 SHIFT_ALREADY_CLOSED`, `closing_count` stays `777`. Deterministic, no
`@Transactional`, assertions on `JdbcTemplate` against committed rows.

### A detail about the lock that matters

The lock `@Lock(PESSIMISTIC_WRITE)` emits is **not** plain `SELECT ... FOR UPDATE`. Evidence, from
two runs of the same test with only the lock-holder changed:

- a hand-written `FOR UPDATE` on the shift row **does** block an expense insert, because PostgreSQL
  takes `FOR KEY SHARE` on the parent row to check `fk_expense_paid_from_shift`, and that conflicts
  with `FOR UPDATE`;
- the lock `findByIdAndTenantIdForUpdate` actually takes does **not** block it, while still
  serialising a competing close.

That pair is only consistent with PostgreSQL's `FOR NO KEY UPDATE` (Hibernate's rendering of
`PESSIMISTIC_WRITE`), which conflicts with `FOR UPDATE`/`FOR NO KEY UPDATE` but not with
`FOR KEY SHARE`. Consequence for L008: **the close's lock does not incidentally serialise FK
children.** A concurrency test that holds a hand-written `FOR UPDATE` would therefore "prove" a
contention the shipped code never creates — the trap this audit nearly fell into, and the reason the
L008 test holds the lock through the repository method instead.

## L008 — the expense/close race: CONFIRMED and pinned, not fixed

`ExpenseShiftCloseRaceIntegrationTest` (new). Both halves missed, exactly as filed.

`closeShift` freezes the expense sum at `ShiftService.java:189-190` under the shift row lock;
`ExpenseService.validatePaidFromShift` reads the shift with the unlocked `findByIdAndTenantId`
(`:236`), so recording an expense never contends for that lock. An expense committing after
`sumActiveByShift` runs but before `closedAt` is written is excluded from the frozen sum, and because
`recordedAfterShiftClose` derives lateness at read time from `createdAt > closedAt`, its earlier
`createdAt` also makes it read as recorded *before* the close.

Made deterministic without instrumenting the code: the expense is inserted inside a transaction held
open on a latch. An uncommitted row is invisible to `sumActiveByShift` whenever it runs, while
`created_at` was already fixed at insert time — the race's exact end state, reached by construction.

Measured: both expenses committed (10 + 50 = 60), `expenses_at_close` = **10**, `expected_cash`
overstated by exactly the lost 50, and the lost expense's `recordedAfterShiftClose` = **false**. The
second test isolates the mechanism: an expense POST returns `201` while the close's own lock is held.

**These tests assert today's defective behaviour on purpose.** L006 is a find-don't-fix pass, so
asserting the correct behaviour would leave a permanently red suite and destroy the signal this audit
exists to create. Every assertion names the wrong value and the right one beside it, and the javadoc
marks each line that must be inverted when L008 is fixed — they double as the fix's acceptance
criteria. L008 stays **OPEN**; it now has a deterministic repro instead of an argument.

## Priority 2 — authorization: started, running at session end

239 `@PreAuthorize` annotations across 16 modules (99 in `inventory/`, 26 in `hr/`, 20 in `menu/`).
Case file generated by `scripts/audit/gen_preauthorize_cases.py` — each case names the HTTP verb,
path and method it guards — ordered by MVP priority into
`scripts/audit/cases/authz_prioritised.json`, all 239 anchors dry-run validated. At ~65 s per guard
the full sweep is ~4.3 h, so it was launched detached and was still running when the session ended.

**First 12 results: 5 UNCOVERED.** Enough to establish that the gap is not confined to inventory.

| # | Endpoint | Permission dropped | Verdict |
|---|---|---|---|
| A207 | `GET /api/shifts` → `list()` | `SHIFTS_VIEW` | **UNCOVERED** |
| A208 | `GET /api/shifts/{id}` → `getById()` | `SHIFTS_VIEW` | **UNCOVERED** |
| A209 | `POST /api/shifts/open` → `openShift()` | `SHIFTS_OPEN` | **UNCOVERED** |
| A210 | `GET /api/shifts/current` → `getCurrentShift()` | `SHIFTS_VIEW` | **UNCOVERED** |
| A028 | `GET /api/expenses/selectable-shifts` → `selectableShifts()` | `EXPENSES_CREATE` | **UNCOVERED** |

Four of the five `ShiftController` gates can be deleted with the suite staying green — on the module
whose authorization was verified present by hand this week. Note what this does *not* say: the gates
are present and they work. `ShiftIntegrationTest` has real permission tests, including
`SHIFTS_VIEW_VARIANCE` (V01 above is covered by one of them). What is missing is a 403 assertion per
endpoint, so a future edit that drops one of these annotations ships silently.

**To resume:**

```
python3 scripts/audit/guard_reversion.py scripts/audit/cases/authz_prioritised.json --out authz.jsonl
python3 scripts/audit/guard_reversion.py scripts/audit/cases/authz_fixups.json      --out authz.jsonl
```

The second covers `ShiftController:127-129`, the one `@PreAuthorize` whose expression wraps across
lines. Commenting only its first line leaves a dangling string concatenation, so the generated case
reported INCONCLUSIVE rather than a verdict about the guard; the harness now takes a `line_end` span
and the fixup case uses it. A verdict that reads "did not compile" is never counted as coverage —
the harness requires a named failing test before it will call a guard covered.

`SecurityService.isSysAdmin()` appears inside most of those 239 expressions and is exercised by them;
it needs no separate case.

## Scope not reached

Stopping where the budget ran out rather than thinning the coverage, as instructed.

- **Priority 3, tenant isolation** — every repository method filtering on `tenant_id`. Start from the
  `@TenantUnscoped` finding above: the convention marking deliberate exceptions is unenforced, so
  generating this case file should begin by treating an *unannotated* unscoped method as a finding in
  its own right. `tenant/CrossTenantIsolationIntegrationTest` and `tenant/support/CrossTenantFixture`
  are the existing harness.
- **Priority 4, document state machines** — `INVALID_STATE_TRANSITION` on purchase invoices, returns,
  waste and physical counts.

## Tests added

| Test | Guards it covers | Was |
|---|---|---|
| `StockBatchServiceTest` — 5 new cases | M01, M02, M03, M08, plus the exact-remainder boundary | uncovered |
| `StockBalanceAverageCostIntegrationTest` — 3 cases | M06, over the real JPQL | uncovered |
| `LedgerSoleWriterArchitectureTest` — 3 cases | sole-writer invariants on `inventory_transaction`, `stock_batch`, and the balance money fields | prose only |
| `ShiftCloseConcurrencyIntegrationTest` — 2 cases | S01's real property; closes L007 | covered only via mock stubs |
| `ExpenseShiftCloseRaceIntegrationTest` — 2 cases | L008, both halves | no coverage, no repro |

Suite: 950 → **965 tests**, green.

Every one was re-run through the harness against its own mutation to confirm it fails when the guard
is removed — a new test that does not detect the reversion it was written for is no better than the
gap it replaced. All five re-verifications named exactly the intended test, and the three
architecture reversions each failed exactly the matching assertion.

**Flakiness check on the two latch-based tests.** Concurrency tests that pass once prove little, so:
across the 12 full-suite runs the authorization sweep had completed by session end — 12 × 965 tests,
each with an unrelated guard disabled — neither concurrency test failed spuriously even once, and the
test total was identical in every run. That is the evidence they are deterministic rather than
merely lucky, which is the same standard this audit applied to the `curl` attempt at L007.

## Constraints observed

- The 34 `@Transactional` integration tests serialise onto one connection and cannot observe a race.
  Both new concurrency tests omit it and tear down by hand.
- L018 is **partly stale**: `src/test/resources/application.yml` already points at
  `restaurant_saas_test` by default, with a comment explaining that the default must never be the
  development database. Still no Testcontainers, so the database is shared between runs and test
  classes partition it by id base — the two new tests take 975_xxx/976_xxx and 986_xxx.
