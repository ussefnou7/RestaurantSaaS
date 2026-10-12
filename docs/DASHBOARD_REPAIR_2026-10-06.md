# Dashboard repair and simplification — 2026-10-06

Scope: the user's explicit request to repair and simplify the existing uncommitted dashboard.
Existing features, thresholds, permission gates and API success shapes are retained.

## Changes

- Replaced 17 Spring rule components plus `AlertRule` with five concrete domain services:
  `StockAlerts`, `ConsumptionAlerts`, `ShiftAlerts`, `OrderAlerts`, and `DocumentAlerts`.
  `AlertService` calls their methods explicitly and gates reads by the existing permissions.
- Stock-out/below-minimum share one balance aggregate instead of two. All three expiry
  conditions share one batch aggregate instead of three. No cross-request cache or new framework.
- Corrected cover calculation: convert the balance's quantity from its own UOM to stock UOM
  before comparing it to the aggregated consumption rate (D87). Future-dated consumption is
  excluded. Conversion failures propagate; they never produce a guessed figure.
- Corrected unposted cost: required quantity and balance average cost are both display-UOM
  values. Removed the erroneous conversion, material lookup and their service dependencies.
- Alert exceptions now fail the request. Continuing after a PostgreSQL statement failure in
  the same transaction cannot produce a reliable partial result. The frontend shows a translated
  failure message and clears previous alerts, rather than presenting an all-clear response.
- Dashboard effects ignore responses after cleanup, including obsolete period and refresh
  requests. Failed loads hide old figures. Both locale dictionaries include loading/failure copy.
- Withheld checks suppress the frontend all-clear message.
- Removed speculative infrastructure commentary and the unused tenant-wide occurrence factory.

Production Java files inside `dashboard/`: **45 → 32**. Lines, including comments:
**3,204 → 2,071**. This is a structural reduction, not the removal of the 17 alert conditions.

## Verification

- Backend: **41 passed**, including real PostgreSQL integration on `restaurant_saas_test`,
  all alert queries, branch scope, mixed gram/kilogram units, future movements, request failures,
  missing permissions, shared-query counts, and the existing summary reconciliation tests.
- Frontend: **44 passed**, including four new asynchronous request regression tests executing
  the page's actual handlers/effects with a small hook harness; no new test dependency.
- Frontend build: lint, TypeScript and Vite passed. Existing `TableLayoutPage` hook warning and
  bundle-size warning remain outside this change.
- Regression evidence: the original cost calculation produced **20,000 instead of 20**;
  the old swallowed-exception path failed the request-failure test; the original frontend let
  an old response replace **200 with 10** and retained previous-period figures on load failure.
- Reviewed against D13, D87/D88, D101, D135 and `docs/REVIEW.md`. No schema change, stock mutation,
  permission removal, new dependency, or product threshold change was introduced in this repair.

Backend command:

```sh
./mvnw -q -Dtest=DashboardReconciliationIntegrationTest,DashboardDateRangeTest,DashboardControllerSecurityTest,TrendBucketTest,AlertServiceTest,ConsumptionAlertsTest,StockAlertsTest test
```

Frontend commands (in `restaurant-saas-web`): `npm test`, `npm run build`.

Suggested commit message: `fix(dashboard): simplify alert services and correct calculations and loading`

The pre-existing implementation and these repairs are left uncommitted for review.
