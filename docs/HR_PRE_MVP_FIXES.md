# HR — issues to close before MVP

> Opened 2026-10-03. Scope decision that day: HR was to be **hidden** from the first release
> (M2 already excluded it). The product owner then asked for it to be tested anyway, and on seeing
> the results decided to **fix it before the MVP** instead of hiding it.
>
> Findings live in [MVP_LEAK_LEDGER.md](MVP_LEAK_LEDGER.md) as **L050, L051, L052** — this file is
> the working sheet for closing them, not a second source of truth. Evidence:
> [`restaurant-saas-web/docs/evidence/mvp-e2e/S-hr/`](../../restaurant-saas-web/docs/evidence/mvp-e2e/S-hr/README.md).
>
> **Severity describes impact if shipped as-is, not fix difficulty.** All three look small to fix.
> L050 is a `P0`-class because the module's core operation does not work at all — not because the
> change is large.

## Environment the findings were observed in

`restaurant_saas_mvp` @ Flyway v69 · tenant `tagreba` (id 2) · job 1 `Chef` · employee 1 `Ahmed`,
branch 1, hired `2026-01-01`, starting salary 5,000 · leave type `ANNUAL` 21 days.

---

## 1. L050 — An employee's salary can never be changed after the first one

**Impact if shipped:** a raise, a pay cut, or a correction to a typo can never be recorded. The
salary freezes on whatever number was entered first.

### Cause

```
ux_employee_salaries_active   UNIQUE (tenant_id, employee_id) WHERE active = true
```

`SalaryService.createSalary()` (`:52-70`) does two things in one transaction:

1. loads the current active salary and mutates it through `closeCurrentSalary()` —
   `effective_to = newEffectiveFrom.minusDays(1)`, `active = false`. A JPA dirty update, flushed
   at commit.
2. `salaryRepository.save(newSalary)` with `active = true` — an **INSERT**.

Hibernate's default flush ordering runs **INSERTs before UPDATEs**, so the new `active = true` row
meets the partial unique index while the old row is still `active = true` in the database.

> *This explanation is inference from the evidence* — first insert succeeds, every later one fails
> on the only applicable constraint regardless of date — not read from a Hibernate SQL log. Worth
> confirming with `show-sql` before changing anything.

The first salary succeeds only because the starting 5,000 lives on the **employee record**, not in
`employee_salaries`, so there is no active row to collide with.

### Repro — seven dates, all `409 DATA_INTEGRITY_VIOLATION`

```
POST /api/hr/employees/1/salaries {"salaryAmount":6000,"effectiveFrom":"2026-03-01"} → 201
```

then any of:

| `effectiveFrom` | |
|---|---|
| `2026-06-01` · `2026-09-01` · `2026-10-02` | valid, in the past |
| `2026-10-04` · `2026-11-01` · `2027-01-01` · `2030-01-01` | future |

all → `409 DATA_INTEGRITY_VIOLATION`, and `employee_salaries` keeps exactly one row.

### Not part of this defect — leave it alone

The service's own guards are correct and reject cleanly **before** reaching the database:

| Case | Result |
|---|---|
| effective date earlier than the current active salary | `400 VALIDATION_FAILED` (`closeCurrentSalary():74-78`) |
| the same effective date | `400 VALIDATION_FAILED` |
| before the hire date | `400 VALIDATION_FAILED` |
| amount `0` or `-500` | `400 VALIDATION_FAILED` |

The validation layer is sound. Only the write path is broken.

### Done when

A second salary posts successfully, the previous row shows `active = false` with
`effective_to = newEffectiveFrom - 1 day`, `GET /salary/current` returns the new amount, and the
four guards above still reject. A regression test covering "change a salary twice" is the obvious
companion — `SalaryService` currently has **no test references at all** (PROJECT.md:74), which is
why this reached a running stack undetected.

---

## 2. L052 — Overlapping leave requests are approved, and the balance is charged for each

**Impact if shipped:** an employee is recorded on leave several times over the same days, and
entitlement drains for days never taken — eventually blocking leave they are actually owed.

### Cause

No conflict check on the requested date range, against either the same leave type or any other.

### Repro

Request 2 (`2026-12-01 → 12-03`, 3 days) already `APPROVED`:

| Request | Dates | Result |
|---|---|---|
| id 3 | **identical dates** | `201 APPROVED` |
| id 4 | `2026-12-02 → 12-04`, overlapping | `201 APPROVED` |

`usedDays` went **3 → 9** for **4 distinct calendar days** (Dec 1–4).

### Open question for the fix

Should the conflict check span **all** leave types or only the same type? An employee cannot be on
annual leave and sick leave simultaneously, which argues for all types — but that is a product
call, not a code detail.

### Done when

A request overlapping any existing `APPROVED` request for the same employee is rejected with a
named error code, the balance is untouched, and a non-overlapping request still succeeds.

---

## 3. L051 — Salary adjustments accept impossible amounts and dates

**Impact if shipped:** nothing computes today — there is no payroll engine (PROJECT.md: no pay run,
no payslip) — so the cost is bad stored data that will surface as negative net pay the moment
payroll is built. Cheap now, expensive later.

### Repro — employee 1's active salary is 6,000

| Request | Result |
|---|---|
| `DEDUCTION` **999,999** | `201` — no cap of any kind |
| `ADDITION` dated **`2030-01-01`** | `201` |
| `ADDITION` dated **`2025-01-01`** (hire date is `2026-01-01`) | `201` |
| `DEDUCTION` `0` / `-100` | `400 VALIDATION_FAILED` — correct |

Three independent holes: no relationship to the salary, no upper date bound, no lower date bound.

### Note the pattern before fixing

The two date holes are the **same family** as `L044` (assets) and `L047` (purchase invoices). The
codebase already validates business dates correctly in waste documents, physical counts and
expenses — those are the implementations to copy rather than invent a third style.

### Done when

A deduction cannot exceed the employee's active salary on the adjustment date, adjustments cannot
be dated in the future or before the hire date, and each rejection carries a named, translated
error code.

---

## What already works — do not regress it

Recorded so a fix is not mistaken for a rewrite. Verified 2026-10-03 against the running stack:

| Area | Result |
|---|---|
| leave balance generation | `assignedDays 21`, `usedDays 0`, `remainingDays 21` |
| 5-day request | `201 APPROVED`, balance `0 → 5` used, `21 → 16` remaining |
| request exceeding the balance | `400 INSUFFICIENT_LEAVE_BALANCE`, balance untouched |
| `toDate` before `fromDate` | `400 VALIDATION_FAILED` |
| `daysCount: 99` on a 3-day range | `400 VALIDATION_FAILED` |
| **cancelling an approved request** | `200 CANCELLED`, balance restored `5 → 0` used, `16 → 21` remaining |
| `APPROVED → REJECTED` | `400 UNSUPPORTED_OPERATION`, balance unchanged |
| all 26 HR endpoints | permission-gated; none relies on a role-only check |

Eight of nine leave checks passed, including the reverse path that usually breaks first.

### Side observations — recorded, not filed

- Leave dated `2025-01-01` or `2030-01-01` returns `404 RESOURCE_NOT_FOUND`. It is rejected, but
  **accidentally** — no balance row exists for that year — and the message points at a missing
  resource rather than an out-of-range date.
- `LeaveRequestStatus` declares `PENDING, APPROVED, REJECTED, CANCELLED`, but requests default to
  `APPROVED` and there is no employee self-service path (PROJECT.md), so `PENDING` and `REJECTED`
  appear unreachable in practice.

---

## Why this module failed where others held

| Part | Tests | Outcome |
|---|---|---|
| leave balances and requests | 5 in `HrServiceTest` | **8 of 9 checks passed** |
| salaries and adjustments | **none** | **broken on the first operation** |

PROJECT.md:74 recorded that `SalaryService` and `SalaryAdjustmentService` have no test references.
The parts with tests held; the part without them did not. The same pattern held in assets — 53
tests, and its core status-derivation logic was correct across every transition.

Whatever fixes land here, the regression tests matter more than the fixes.
