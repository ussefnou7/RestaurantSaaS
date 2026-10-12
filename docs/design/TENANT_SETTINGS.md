# Tenant settings — MVP API and deferred design

Product scope: 2026-10-07, D138. The backend API, SysAdmin Panel editor and the POS's
tenant-scoped settings lifecycle are now implemented. This document distinguishes those
completed integration slices from the remaining order-contract work and subscription design.

## Implemented

`V74__tenant_settings.sql` creates one settings row per tenant (identity `id`, unique
non-null `tenant_id`, FK to `tenants`). It backfills existing tenants without overwriting
their values on a repeated execution. New tenants get a row in the tenant-creation transaction.
Audit timestamps use the existing tenant-local D101 behavior. Timezone stays on `Tenant`.

| JSON field | Default | Meaning |
|---|---|---|
| `taxEnabled` | `true` | Whether tax is enabled |
| `taxRate` | `14` | Percentage from 0 to 100, up to four decimal places |
| `taxOnServiceCharge` | `false` | Include the service charge in the taxable base when true |
| `serviceChargeEnabled` | `false` | Whether the configured service charge is enabled |
| `serviceChargeRate` | `0` | Percentage from 0 to 100, up to four decimal places |
| `dineInEnabled` | `true` | Dine-in operating option |
| `takeawayEnabled` | `true` | Takeaway operating option |
| `deliveryEnabled` | `true` | Delivery operating option |

At least one order type must be enabled. Disabling a charge retains its rate. This allows
turning off tax while keeping 14% ready for re-enablement. The enabled flag is authoritative.
All values are tenant-wide; no branch override table is introduced.

### API contract

`GET /api/tenant-settings` returns the eight settings plus `tenantId`, `createdAt`, `updatedAt`.
Requires `TENANT_SETTINGS_VIEW` or `TENANT_SETTINGS_MANAGE` (or SYS_ADMIN).

`PUT /api/tenant-settings` replaces all eight settings and returns the same response shape.
Requires `TENANT_SETTINGS_MANAGE` (or SYS_ADMIN). Every field is required; no partial update,
omission defaults or client-supplied tenant/actor/row id. The actor is recorded in `updated_by`.

```json
{
  "taxEnabled": true,
  "taxRate": 14,
  "taxOnServiceCharge": false,
  "serviceChargeEnabled": false,
  "serviceChargeRate": 0,
  "dineInEnabled": true,
  "takeawayEnabled": true,
  "deliveryEnabled": true
}
```

For tenant users the tenant comes from the authenticated principal; a forged `X-Tenant-Id`
cannot redirect either operation. SYS_ADMIN uses `X-Tenant-Id` to select a tenant, following
the existing `CurrentTenantProvider` contract. No settings DELETE endpoint is exposed.

OWNER receives VIEW + MANAGE by default; BRANCH_MANAGER and CASHIER receive VIEW.
The migration grants both role defaults and existing users' direct permission snapshots.
Other users can receive these permissions through the existing permission administration.

Errors: `TENANT_ORDER_TYPE_REQUIRED` (400), `TENANT_SETTINGS_NOT_FOUND` (404), and standard
field-validation errors (400). Missing rows fail explicitly; a read never silently invents
or persists defaults. Arabic and English messages are present in admin-web.

### Tax-base rule agreed with the product owner

Before the POS's D129 rounding rule:

```text
S = item subtotal
C = service enabled and applicable ? S × serviceChargeRate / 100 : 0
taxBase = taxOnServiceCharge ? S + C : S
T = taxEnabled ? taxBase × taxRate / 100 : 0
total = S + C + T
```

For S=100, service=10%, tax=14%: flag false → C=10, T=14, total=124;
flag true → C=10, T=15.40, total=125.40. Tax disabled → total=110 in either mode.
Service disabled → total=114 in either mode. These are design examples, not calculations
performed by the settings endpoint.

## Frontend integration — implemented in this pass

The Panel renders an Operating Settings section from the tenant details screen. It fetches the
selected tenant before enabling edits, sends `X-Tenant-Id` per request without mutating Axios
defaults, ignores stale tenant responses, validates rates/order-type selection, sends all eight
PUT fields, and renders retry/saved/unsaved/error states in English and Arabic dictionaries.

The POS fetches settings through its authenticated client, caches them under a tenant-scoped
SQLite reference-cache key, refreshes on reconnect, rejects cross-tenant responses, and keeps
session/ticket/outbox recovery intact when the fetch fails. New order modes are gated by the
saved configuration, and a single configured tax calculation now feeds the cart, payment,
board, receipt and payload paths. New tickets and local outbox payloads carry a financial
settings snapshot; the API client strips that local-only field before POSTing the current
backend order contract. Legacy local tickets without a snapshot remain readable and are not
silently rewritten.

## Remaining POS/backend work

- Service-charge collection is intentionally guarded. The current order contract has no
  `serviceChargeAmount`, financial-settings snapshot, or service-charge report field. The POS
  therefore does not hide service in `taxAmount`, change item subtotal, or submit an ignored
  field; when service is enabled, new collection is blocked with a recoverable explanation.
- Confirm whether service applies to dine-in only or also takeaway/delivery.
- Decide how any separately represented service amount receives a whole-unit rounding remainder.
- After those decisions, the backend follow-up must extend `OrderRequest`, `Order`, line/header
  persistence and response/history DTOs, order reconciliation, sales/payment reports, receipt
  lookup, and compatibility tests. The POS can then remove its guard and include the agreed
  service amount in the same D129 calculation source.
- D133 table rounds must share one bill snapshot; a later implementation must reject or resolve
  mixed snapshots explicitly rather than repricing rounds independently from current settings.

## Deferred design — no tables or runtime gates yet

### Module toggles

Proposed `tenant_feature(tenant_id, feature_code, enabled)` with a fixed feature-code enum
and unique tenant/feature pair. Module visibility and module access need coordinated frontend
and backend enforcement, plus explicit dependencies (for example, order consumption uses
inventory even if an inventory navigation item is hidden). Exact codes and dependencies are open.

### Plans and subscriptions

Keep commercial subscription history separate from operating settings. Proposed tables:

- `subscription_plan`: code, display name, billing period, active status.
- `subscription_plan_feature`: the features included in each plan.
- `tenant_subscription`: tenant, plan, start/end dates, status and renewal history.

Proposed effective access: plan entitlement AND tenant enablement AND user permission.
An operational setting must not grant a paid feature the plan does not include.
Prices, invoices, payments, grace periods, expiry behavior, trial policy, plan changes,
overrides and subscription date boundary semantics remain open product decisions.
`Tenant.status` continues to represent administrative account status, not subscription status.

### Additional settings

Possible later additions: currency, tax-inclusive pricing, receipt identity, payment methods,
discount policy and delivery fees. Branch-specific channels or charges can be designed when
required. No placeholder columns, JSON bags, currency switches, settings versions or branch
override entities are added ahead of those consumers.

## Verification

`TenantSettingsIntegrationTest` covers defaults, onboarding, migration backfill/repeatability,
direct permission grants, real JWT isolation, read/write authorization, required fields,
rate boundaries/precision, all seven nonempty channel combinations, audit attribution,
tax-base flag persistence and database constraints. Run it on an isolated Flyway-migrated
database together with `TenantTimeZoneIntegrationTest` and `CurrentTenantProviderTest`.

Verified 2026-10-07 on the isolated PostgreSQL database `codex_tenant_settings_20261007`:
41 settings integration cases + 6 timezone cases + 10 tenant-resolution cases passed
(57 tests, no failures/errors). Flyway migrated from empty through V74 and Hibernate
validated the mappings. The development database was not migrated by this task.
The two permission-manifest tests also passed (59 tests total). Generated backend/frontend
permission contracts were synchronized, and the touched frontend contracts and English/Arabic
error dictionaries passed ESLint; the two new error keys have matching locale coverage.
The isolated test database was removed after verification; it can be recreated from Flyway.

Suggested backend commit: `feat(tenant): add configurable operating settings`
Suggested integration handoff: extend the order contract before enabling service collection.
