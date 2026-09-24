# Expenses API contract

Generated from the backend implementation and verified through
`ExpenseApiContractIntegrationTest` against Flyway-migrated PostgreSQL on 2026-09-05.
This is the frontend source of truth for the Expenses pass.

> **Updated 2026-09-06 by the shifts pass (O51/D124):** `paidFromShiftId` on create,
> `paidFromShiftId` and `recordedAfterShiftClose` on the response, and
> `GET /api/expenses/selectable-shifts`. Everything else is unchanged.

## Common transport rules

- All endpoints require authentication and the `X-Tenant-Id` header.
- Expense endpoints do not read `X-User-Id`; create and void derive their actor from the signed
  JWT principal. A supplied header is ignored.
- `X-User-Id` remains optional on category mutations and is persisted in their audit fields when
  supplied.
- A system administrator passes every permission gate through the standard sysadmin bypass.
- Dates are ISO `YYYY-MM-DD`. Expense-resource audit timestamps are tenant-local ISO date-times
  without an offset. When an expense is embedded in shift detail, its stored tenant-local
  `createdAt` is converted to branch-local wall time before it is compared with `closedAt` and
  returned beside other shift timestamps.
- `BigDecimal` values in this module serialize as JSON **numbers**, not strings. The runtime
  response was `"amount":375.250000`.
- JSON `null` fields are emitted rather than omitted in normal DTO responses.

## Endpoint matrix

| Method | Full path | Permission | Success |
|---|---|---|---|
| `GET` | `/api/expenses` | `EXPENSES_VIEW` | `200 OK` |
| `GET` | `/api/expenses/{id}` | `EXPENSES_VIEW` | `200 OK` |
| `POST` | `/api/expenses` | `EXPENSES_CREATE` | `201 Created` |
| `POST` | `/api/expenses/{id}/void` | `EXPENSES_VOID` | `200 OK` |
| `GET` | `/api/expenses/selectable-shifts` | `EXPENSES_CREATE` | `200 OK` |
| `GET` | `/api/expense-categories` | `EXPENSES_VIEW` | `200 OK` |
| `POST` | `/api/expense-categories` | `EXPENSES_CATEGORY_MANAGE` | `201 Created` |
| `PUT` | `/api/expense-categories/{id}` | `EXPENSES_CATEGORY_MANAGE` | `200 OK` |
| `PATCH` | `/api/expense-categories/{id}/activate` | `EXPENSES_CATEGORY_MANAGE` | `200 OK` |
| `PATCH` | `/api/expense-categories/{id}/deactivate` | `EXPENSES_CATEGORY_MANAGE` | `200 OK` |

## Enums

| Enum | Exact JSON values |
|---|---|
| `ExpensePaymentSource` | `CASH_DRAWER`, `CASH_ON_HAND`, `BANK` |
| `ExpenseSourceType` | `MANUAL` |
| `ExpenseStatus` | `ACTIVE`, `VOIDED` |

`sourceType` and `status` are response-only. Clients cannot set them on create.

## Request DTOs

### `CreateExpenseRequest`

| Field | JSON type | Presence/nullability | Constraints |
|---|---|---|---|
| `branchId` | integer (`int64`) | optional, nullable | When non-null, must identify a branch owned by the tenant. `null` creates a company-level expense. |
| `categoryId` | integer (`int64`) | required, non-null | Must resolve to an active global or tenant-owned category. |
| `amount` | number | required, non-null | `BigDecimal`; at most 12 integer and 6 fractional digits; must be strictly greater than zero. Serializes as a JSON number. |
| `expenseDate` | string (`date`) | required, non-null | Must not be after today in `branch.timezone`, falling back to `tenant.timezone`. No backdating limit. |
| `description` | string | optional, nullable | Maximum 500 characters. Blank input is stored as `null`. |
| `payeeName` | string | optional, nullable | Maximum 255 characters. Blank input is stored as `null`; no Supplier FK exists. |
| `paymentSource` | string enum | required, non-null | One of `CASH_DRAWER`, `CASH_ON_HAND`, `BANK`. |
| `paidFromShiftId` | integer (`int64`) | optional, nullable | **Added by the shifts pass (O51/D124).** The shift whose drawer paid this, chosen explicitly by the manager. Must be a shift this tenant owns, and its device's branch must equal the expense's `branchId`, else `EXPENSE_SHIFT_BRANCH_MISMATCH`. A **closed** shift is a legal choice. |

Not accepted: `sourceType`, `sourceId`, `status`, any void field, audit timestamps, document code,
lines, attachment, tax, or supplier ID.

**`paidFromShiftId` is never inferred.** Attribution by `expenseDate` was considered and rejected:
that column is a `DATE` with no time, so on a day with three shifts it cannot identify one — and if
the date did drive attribution, a manager could erase any shortfall by dating an expense into the
shift that has it. The manager picks from `GET /api/expenses/selectable-shifts`.

**Linking to a closed shift stores the link and does not move that shift's recorded variance.** The
client must say so at the point of selection — "Closed — this will be linked, but its recorded
variance will not change" — because a manager recording expense after expense in the belief that
they are correcting the figures is the failure this column invites.

### `VoidExpenseRequest`

| Field | JSON type | Presence/nullability | Constraints |
|---|---|---|---|
| `reason` | string | required for success, non-null | Must contain non-whitespace text and be at most 500 characters. A missing body, missing field, `null`, empty, or blank value yields `EXPENSE_VOID_REASON_REQUIRED`. |

### `ExpenseCategoryRequest`

Used by both category create and update.

| Field | JSON type | Presence/nullability | Constraints |
|---|---|---|---|
| `name` | string | required, non-null | Non-blank, maximum 255 characters; trimmed before storage; case-insensitively unique within the tenant. |
| `nameAr` | string | optional, nullable | Maximum 255 characters; blank input is stored as `null`. |

Category `active`, `tenantId`, and `global` are response-only. New categories always start active.

## Response DTOs

### `ExpenseResponse`

Used by expense create, get, list content, and void.

| Field | JSON type | Nullable | Meaning |
|---|---|---|---|
| `id` | integer (`int64`) | no | Numeric expense identity. |
| `branchId` | integer (`int64`) | **yes** | `null` means company-level. |
| `branchName` | string | **yes** | Denormalized display name; `null` with an unbranched expense. |
| `categoryId` | integer (`int64`) | no | Referenced category ID. |
| `categoryName` | string | no | Denormalized English/default category name. |
| `categoryNameAr` | string | **yes** | Denormalized Arabic category name. |
| `amount` | number | no | Six-decimal `BigDecimal`, serialized as a JSON number. |
| `expenseDate` | string (`date`) | no | User-supplied date when money left. |
| `description` | string | **yes** | Optional description. |
| `payeeName` | string | **yes** | Optional free-text payee. |
| `paymentSource` | string enum | no | `CASH_DRAWER`, `CASH_ON_HAND`, or `BANK`. |
| `sourceType` | string enum | no | `MANUAL` in this pass. |
| `sourceId` | integer (`int64`) | **yes** | Always `null` for `MANUAL`; reserved for a future decided source integration. |
| `status` | string enum | no | `ACTIVE` or `VOIDED`. |
| `voidedAt` | string (`date-time`) | **yes** | Non-null exactly when status is `VOIDED`. |
| `voidedBy` | integer (`int64`) | **yes** | Non-null exactly when status is `VOIDED`. |
| `voidReason` | string | **yes** | Non-null exactly when status is `VOIDED`. |
| `createdBy` | integer (`int64`) | **yes** | Authenticated JWT user for new manual expenses. The database column remains nullable, so historical rows created before token binding may return `null`. |
| `createdAt` | string (`date-time`) | no | Tenant-local write timestamp stamped by `TenantTimestampListener`. |
| `paidFromShiftId` | integer (`int64`) | **yes** | **Added by the shifts pass.** `null` for most expenses — the drawer link is the exception, not the rule. |
| `recordedAfterShiftClose` | boolean | **yes** | **Added by the shifts pass.** `null` when there is no linked shift, so a client can tell "not a drawer expense" from "a drawer expense recorded in time". `false` means it entered its shift's `expectedCash`; `true` means it did not and never will. Derived from `createdAt` vs the shift's `closedAt`; not stored. |

### `ExpenseCategoryResponse`

| Field | JSON type | Nullable | Meaning |
|---|---|---|---|
| `id` | integer (`int64`) | no | Category identity. |
| `tenantId` | integer (`int64`) | **yes** | `null` for a global seeded category. |
| `name` | string | no | English/default display name. |
| `nameAr` | string | **yes** | Arabic display name. |
| `active` | boolean | no | Inactive rows remain in list responses. |
| `global` | boolean | no | `true` exactly when `tenantId` is `null`; global rows are read-only. |
| `createdBy` | integer (`int64`) | **yes** | `null` on migration-seeded globals or when no audit header was supplied. |
| `createdAt` | string (`date-time`) | no | Creation timestamp. |

## Paginated expense-list envelope

`GET /api/expenses` returns Spring Data's current `PageImpl` JSON shape verbatim:

```json
{
  "content": ["ExpenseResponse"],
  "empty": false,
  "first": true,
  "last": true,
  "number": 0,
  "numberOfElements": 1,
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "unpaged": false
  },
  "size": 20,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "totalElements": 1,
  "totalPages": 1
}
```

`content` is an array of `ExpenseResponse`; the string above is a schema marker only. The worked
list example below contains the real object returned by the run.

## Expense-list query parameters

All are optional.

| Parameter | Type | Default | Semantics |
|---|---|---|---|
| `branchId` | integer (`int64`) | no filter | Exact branch match. A missing parameter does not mean unbranched. |
| `unbranchedOnly` | boolean | `false` | When `true`, requires `branch_id IS NULL`. This is the only null-branch filter. |
| `categoryId` | integer (`int64`) | no filter | Exact category match. |
| `dateFrom` | string (`date`) | no lower bound | Inclusive expense-date lower bound. |
| `dateTo` | string (`date`) | no upper bound | Inclusive expense-date upper bound. |
| `paymentSource` | string enum | no filter | Exact `ExpensePaymentSource` match. |
| `status` | string enum | **no filter** | Both `ACTIVE` and `VOIDED` are returned by default. |
| `search` | string | no filter | Trimmed; case-insensitive substring match over `description` or `payeeName`. Blank means no filter. |
| `page` | integer | `0` | Zero-based page number. |
| `size` | integer | `20` | Requested page size. |
| `sort` | string, repeatable | `expenseDate,DESC` then `id,DESC` | Spring Data sort syntax. Supplying `sort` replaces the defaults. |

If `branchId` and `unbranchedOnly=true` are both supplied, both predicates apply; the result is
normally empty. The API does not guess which filter the caller intended.

## `GET /api/expenses/selectable-shifts`

The list a manager picks from when attributing an expense to a drawer (D124). Added by the shifts
pass; full detail in [CONTRACT_SHIFTS_API.md](CONTRACT_SHIFTS_API.md).

| Param | Type | Presence | Default |
|---|---|---|---|
| `branchId` | integer | **required** | — |
| `days` | integer | optional | `7`, extendable per call |

Returns an array, newest first, of `{id, businessDate, deviceId, deviceName, cashierUserId,
cashierName, openedAt, closedAt, status, closed}`.

Filtered to the expense's branch — so an expense cannot be charged to another branch's drawer — and
to a recent window, because an unbounded list becomes unreadable within months and an explicit
choice nobody can read is not an explicit choice. Closed shifts are included and selectable.

**Carries no money figures of any kind.** This endpoint is reachable by anyone who can record an
expense, which is a wider audience than the one permitted to see variances (D123). `cashierName` is
resolved from the shift's `openedByUserId` on read, never stored on the shift.

## Structured errors

Every module error is returned through the shared envelope:

```json
{
  "errorCode": "EXPENSE_NOT_FOUND",
  "message": "Expense not found: 99",
  "params": {"expenseId": 99},
  "status": 404,
  "timestamp": "2026-09-05T15:17:40.267954",
  "path": "/api/expenses/99",
  "fieldErrors": null
}
```

The frontend must translate `errorCode` plus `params`; `message` is logs/debug only.

| `ExpenseErrorCode` | HTTP | Thrown when | Exact `ErrorParams` keys |
|---|---:|---|---|
| `EXPENSE_NOT_FOUND` | 404 | An expense ID is missing or belongs to another tenant on get/void. | `expenseId` |
| `EXPENSE_CATEGORY_NOT_FOUND` | 404 | A create category is neither global nor owned by the tenant, or a category mutation targets a missing/other-tenant row. | `categoryId` |
| `BRANCH_NOT_FOUND` | 404 | A non-null create `branchId` is missing or owned by another tenant. | `branchId` |
| `EXPENSE_CATEGORY_INACTIVE` | 409 | The resolved create category is inactive. | `categoryId`, `categoryName` |
| `EXPENSE_INVALID_AMOUNT` | 400 | The normalized six-decimal amount is null, zero, or negative. | `amount` |
| `EXPENSE_DATE_IN_FUTURE` | 400 | `expenseDate` is after the tenant/branch-local today. | `expenseDate`, `today` |
| `EXPENSE_ALREADY_VOIDED` | 409 | A void is attempted on a `VOIDED` expense. | `expenseId`, `voidedAt` |
| `EXPENSE_NOT_MANUAL` | 409 | Direct void is attempted on a non-manual source row. Unreachable while `MANUAL` is the only live source. | `expenseId`, `sourceType` |
| `EXPENSE_VOID_REASON_REQUIRED` | 400 | The void reason/body is absent, null, empty, or blank. | `expenseId` |
| `EXPENSE_CATEGORY_IS_GLOBAL` | 409 | Update/activate/deactivate targets a global category. | `categoryId` |
| `EXPENSE_CATEGORY_NAME_EXISTS` | 409 | A tenant category create/update duplicates another tenant-owned name case-insensitively. | `name` |

Bean-validation failures use the shared `VALIDATION_FAILED` field-error shape rather than an
`ExpenseErrorCode`. Each field error carries exact keys `rejectedValue` and `constraint`.

## Worked endpoint examples from the integration run

IDs and timestamps below are copied from the successful PostgreSQL-backed test run. They are not
stable identifiers for another database.

### `GET /api/expense-categories`

Request:

```http
GET /api/expense-categories
X-Tenant-Id: 987001
```

Response `200`:

```json
[
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":12,"name":"Bank & payment fees","nameAr":"رسوم بنكية ومدفوعات","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":7,"name":"Cleaning & consumables","nameAr":"نظافة ومستهلكات","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":2,"name":"Electricity","nameAr":"كهرباء","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":4,"name":"Gas","nameAr":"غاز","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":10,"name":"Internet & phone","nameAr":"إنترنت وتليفون","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":9,"name":"Licences & government fees","nameAr":"رخص ورسوم حكومية","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":6,"name":"Maintenance & repairs","nameAr":"صيانة وإصلاحات","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":8,"name":"Marketing & advertising","nameAr":"تسويق ودعاية","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":13,"name":"Other","nameAr":"متنوع","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":1,"name":"Rent","nameAr":"إيجار","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":5,"name":"Salaries & wages","nameAr":"مرتبات وأجور","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":11,"name":"Transport & delivery","nameAr":"مواصلات وتوصيل","tenantId":null},
  {"active":true,"createdAt":"2026-09-05T15:13:30.712109","createdBy":null,"global":true,"id":3,"name":"Water","nameAr":"مياه","tenantId":null}
]
```

### `POST /api/expense-categories`

Request:

```http
POST /api/expense-categories
X-Tenant-Id: 987001
X-User-Id: 987101
Content-Type: application/json

{"name":"Office supplies","nameAr":"أدوات مكتبية"}
```

Response `201`:

```json
{"active":true,"createdAt":"2026-09-05T15:17:39.998943114","createdBy":987101,"global":false,"id":15,"name":"Office supplies","nameAr":"أدوات مكتبية","tenantId":987001}
```

### `PUT /api/expense-categories/{id}`

Request:

```http
PUT /api/expense-categories/15
X-Tenant-Id: 987001
X-User-Id: 987101
Content-Type: application/json

{"name":"Office and stationery","nameAr":"مكتب وقرطاسية"}
```

Response `200`:

```json
{"active":true,"createdAt":"2026-09-05T15:17:39.998943114","createdBy":987101,"global":false,"id":15,"name":"Office and stationery","nameAr":"مكتب وقرطاسية","tenantId":987001}
```

### `PATCH /api/expense-categories/{id}/deactivate`

Request:

```http
PATCH /api/expense-categories/15/deactivate
X-Tenant-Id: 987001
X-User-Id: 987101
```

Response `200`:

```json
{"active":false,"createdAt":"2026-09-05T15:17:39.998943114","createdBy":987101,"global":false,"id":15,"name":"Office and stationery","nameAr":"مكتب وقرطاسية","tenantId":987001}
```

### `PATCH /api/expense-categories/{id}/activate`

Request:

```http
PATCH /api/expense-categories/15/activate
X-Tenant-Id: 987001
X-User-Id: 987101
```

Response `200`:

```json
{"active":true,"createdAt":"2026-09-05T15:17:39.998943114","createdBy":987101,"global":false,"id":15,"name":"Office and stationery","nameAr":"مكتب وقرطاسية","tenantId":987001}
```

### `POST /api/expenses`

Request:

```http
POST /api/expenses
X-Tenant-Id: 987001
Content-Type: application/json

{
  "branchId": null,
  "categoryId": 15,
  "amount": 375.250000,
  "expenseDate": "2026-09-04",
  "description": "Printer paper and pens",
  "payeeName": "Downtown Stationery",
  "paymentSource": "CASH_ON_HAND"
}
```

Response `201`:

```json
{"amount":375.250000,"branchId":null,"branchName":null,"categoryId":15,"categoryName":"Office and stationery","categoryNameAr":"مكتب وقرطاسية","createdAt":"2026-09-05T15:17:40.125032","createdBy":987101,"description":"Printer paper and pens","expenseDate":"2026-09-04","id":2,"payeeName":"Downtown Stationery","paymentSource":"CASH_ON_HAND","sourceId":null,"sourceType":"MANUAL","status":"ACTIVE","voidReason":null,"voidedAt":null,"voidedBy":null}
```

### `GET /api/expenses`

Request:

```http
GET /api/expenses?unbranchedOnly=true&categoryId=15&dateFrom=2026-09-01&dateTo=2026-09-05&paymentSource=CASH_ON_HAND&status=ACTIVE&search=paper
X-Tenant-Id: 987001
```

Response `200`:

```json
{"content":[{"amount":375.250000,"branchId":null,"branchName":null,"categoryId":15,"categoryName":"Office and stationery","categoryNameAr":"مكتب وقرطاسية","createdAt":"2026-09-05T15:17:40.125032","createdBy":987101,"description":"Printer paper and pens","expenseDate":"2026-09-04","id":2,"payeeName":"Downtown Stationery","paymentSource":"CASH_ON_HAND","sourceId":null,"sourceType":"MANUAL","status":"ACTIVE","voidReason":null,"voidedAt":null,"voidedBy":null}],"empty":false,"first":true,"last":true,"number":0,"numberOfElements":1,"pageable":{"offset":0,"pageNumber":0,"pageSize":20,"paged":true,"sort":{"empty":false,"sorted":true,"unsorted":false},"unpaged":false},"size":20,"sort":{"empty":false,"sorted":true,"unsorted":false},"totalElements":1,"totalPages":1}
```

### `GET /api/expenses/{id}`

Request:

```http
GET /api/expenses/2
X-Tenant-Id: 987001
```

Response `200`:

```json
{"amount":375.250000,"branchId":null,"branchName":null,"categoryId":15,"categoryName":"Office and stationery","categoryNameAr":"مكتب وقرطاسية","createdAt":"2026-09-05T15:17:40.125032","createdBy":987101,"description":"Printer paper and pens","expenseDate":"2026-09-04","id":2,"payeeName":"Downtown Stationery","paymentSource":"CASH_ON_HAND","sourceId":null,"sourceType":"MANUAL","status":"ACTIVE","voidReason":null,"voidedAt":null,"voidedBy":null}
```

### `POST /api/expenses/{id}/void`

Request:

```http
POST /api/expenses/2/void
X-Tenant-Id: 987001
Content-Type: application/json

{"reason":"Duplicate entry"}
```

Response `200`:

```json
{"amount":375.250000,"branchId":null,"branchName":null,"categoryId":15,"categoryName":"Office and stationery","categoryNameAr":"مكتب وقرطاسية","createdAt":"2026-09-05T15:17:40.125032","createdBy":987101,"description":"Printer paper and pens","expenseDate":"2026-09-04","id":2,"payeeName":"Downtown Stationery","paymentSource":"CASH_ON_HAND","sourceId":null,"sourceType":"MANUAL","status":"VOIDED","voidReason":"Duplicate entry","voidedAt":"2026-09-05T15:17:40.267954","voidedBy":987101}
```

## What does not exist

- No `PUT` or `PATCH /api/expenses/{id}` update endpoint.
- No `DELETE /api/expenses/{id}` endpoint.
- No expense totals, aggregate, accounting, P&L, journal-entry, or allocation endpoint.
- No `DELETE /api/expense-categories/{id}` endpoint; deactivate is the retirement mechanism.
- No approval or draft/post lifecycle and no document code.
- No expense lines.
- ~~No `paidFromShiftId` field or shift-link endpoint.~~ **Both now exist** — see
  `CreateExpenseRequest.paidFromShiftId` above and `GET /api/expenses/selectable-shifts` below.
  This implements the attribution surface, but O51 remains partially open until expense creation
  and shift close share a coordinated ordering boundary.
- No attachment/receipt upload.
- No `systemKey` category field.
- No `ASSET_MAINTENANCE` or `PAYROLL` source value/producer.
