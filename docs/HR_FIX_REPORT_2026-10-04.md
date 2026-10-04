# HR Fix Report - 2026-10-04

All three findings from `HR_PRE_MVP_FIXES.md` are implemented and marked `FIXED` in
`MVP_LEAK_LEDGER.md`. Independent live-scenario replay is still required for `VERIFIED`.

## Changes

| Finding | Cause confirmed | Result |
| --- | --- | --- |
| L050 | Replacement salary INSERT collided with the still-active previous row's unique index. | Flush the old salary's closure before inserting its replacement, within the same transaction. Repeated changes work and retain correct history. |
| L051 | Adjustment creation had no hire-date, future-date, or salary-limit validation. | Reject future/pre-hire adjustments and deductions above the salary effective on that date, with named English/Arabic errors. |
| L052 | Leave approval had no overlap check. | Reject inclusive date overlaps with any approved leave for the employee, across all types, before charging balance. Lock the employee to serialize simultaneous approvals. |

The all-types rule follows L052's written acceptance criterion ("any existing APPROVED request").
The working sheet also listed this as a product question; no separate user answer was received.
Cancelled leave no longer blocks dates, adjacent leave remains valid, and other employees can
take the same dates. Cancellation restores entitlement and repeated cancellation remains harmless.

The original document said pre-hire salary dates were already rejected. A database test proved
the first salary was not guarded; L050 now rejects it. Existing equal/earlier-date and
non-positive-amount validation remains covered.

Deduction limits use historical salary intervals, not just the latest active row. Before the
first salary change, the employee's starting salary applies. Future-dated raises do not raise
today's deduction limit. The cap is per deduction; this does not introduce payroll calculation.
"Today" uses the employee branch's timezone, falling back to the tenant zone as configured.

## Verification

PostgreSQL 16, dedicated `restaurant_saas_test` database, Flyway v71; no new migration required.

| Test class | Passing cases |
| --- | ---: |
| SalaryServiceIntegrationTest | 4 |
| SalaryAdjustmentServiceIntegrationTest | 11 |
| LeaveRequestServiceIntegrationTest | 17 |
| HrServiceTest | 12 |
| PermissionManifestTest | 2 |
| BranchScopeIntegrationTest | 6 |
| Total distinct targeted backend tests | 52 |

Before fixing: L050 reproduced the exact unique-index collision and missing pre-hire rejection;
L051 produced seven missing-validation failures; L052 accepted all five tested overlap shapes.
After fixing: all targeted cases pass, including concurrent same-type/cross-type approvals,
unchanged balances on rejection, and tenant/branch/inactive-employee guards on locked lookup.

Admin-web: `hrAdjustmentErrors.test.ts` and `hrLeaveErrors.test.ts` pass. English/Arabic messages
resolve through `translateApiError`, with required parameters and no backend debug text.
`npm run build` passes ESLint, TypeScript, and Vite; the existing large-chunk warning remains.
Diff whitespace and the applicable `docs/REVIEW.md` invariants were checked.

## Commits

| Finding | Backend | Admin-web |
| --- | --- | --- |
| L050 | `11a9a66` | No frontend change |
| L051 | `c219862` | `a810d73` |
| L052 | `5f2db5c` | `097d10f` |

## Limits

The full repository test suite and original running-stack/browser scenarios were not rerun.
No deployment was performed. Existing invalid adjustments or overlapping leave records were
not rewritten; the fixes govern new requests. The three ledger entries await independent
verification rather than being self-certified as `VERIFIED`.
