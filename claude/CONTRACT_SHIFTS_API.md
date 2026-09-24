# Shifts API contract

Generated from the backend implementation and updated against the resumed working tree on
2026-09-06. This is the source of truth for **Part B (`restaurant-pos`)** and
**Part C (`restaurant-saas-web`)**, subject to the explicitly named release blockers below.

Implements the main D119–D126 structures. It does **not** yet certify blind detail reads,
concurrent-close immutability, or coordinated expense/close ordering; those remain in
`docs/SHIFT_REVIEW_FOLLOWUP.md`. This contract supersedes every earlier description of
`/api/shifts`: the previous module was keyed on the cashier, returned the expected figure and the
variance, and gated all three endpoints on `SHIFTS_OPEN`.

---

## The protection currently established

**Four fields are not members of the POS-facing open/current/close DTOs** (D123):

`expectedCash` · `variance` · `handoverVariance` · `expensesAtClose`

— and those operational responses carry no order or payment-method totals.

This is deliberately a property of the **API**, not of the UI. If the server returned the variance
and the POS merely declined to render it, the number would sit in the network tab and the blind
count would be one commit from gone. `ShiftResponse` has no such record components, so no future
mapper edit can begin populating them, and no client can read them from a trace.

The figures **are** computed and stored on the row. The explicitly named expected/variance
fields surface through `GET /api/shifts` and `GET /api/shifts/{id}` only for a caller holding
`SHIFTS_VIEW_VARIANCE`.

**This does not yet establish the complete blind-count guarantee.** The manager detail endpoint
still returns individual order amounts/payment methods and drawer expenses to every caller with
`SHIFTS_VIEW`, while its shift header returns opening and closing counts. A caller without
`SHIFTS_VIEW_VARIANCE` can therefore reconstruct expected cash, and can derive handover variance
from consecutive shifts. Defining and enforcing separate restricted/investigation read surfaces
is an open release blocker; see `docs/SHIFT_REVIEW_FOLLOWUP.md`, finding 1.

---

## Common transport rules

- All endpoints require authentication. Tenant comes from the resolved principal.
- **No shift endpoint reads `X-User-Id`, and none accepts a device in the body, a header, or by
  branch inference.** Both actors come from the signed token. `X-User-Id` remains whitelisted in
  CORS and is still read by ~20 other controllers; removing it there is out of scope.
- The device is `requireCurrentDeviceId()` — the signed `deviceId` claim, revalidated on every
  request against tenant ownership and active state in the authentication filter's single account
  lookup (D127).
- **A token with no `deviceId` claim is a web session.** It has no drawer: `open`, `current` and
  `close` all reject it with `DEVICE_IDENTITY_REQUIRED` (401). A manager therefore cannot open,
  close or force-close from the admin web. That is intended, not a gap — those actions require
  standing at the drawer. The two **read** endpoints are unaffected and work for web sessions.
- Dates are ISO `YYYY-MM-DD`. Timestamps are tenant-local ISO date-times **without an offset**,
  in the branch's zone falling back to the tenant's (D101).
- `BigDecimal` serializes as an **unquoted JSON number at scale 6**. The runtime response was
  `"openingCount":200.000000` and `"variance":-20.000000`. `JSON.parse` yields `200` and `-20`.
- **Null handling differs by DTO and it matters:**
  - `ShiftResponse` / `CurrentShiftResponse` **emit** nulls (`"closedAt":null`).
  - `ShiftListItemResponse` is `@JsonInclude(NON_NULL)` — null fields are **absent**. A client must
    treat an absent key as null. This structurally omits the named variance fields, but does not
    prevent the reconstruction described above.
- A system administrator passes every permission gate via the standard sysadmin bypass.

---

## Endpoint matrix

| Method | Full path | Permission | Success |
|---|---|---|---|
| `GET` | `/api/shifts` | `SHIFTS_VIEW` | `200 OK` |
| `GET` | `/api/shifts/{id}` | `SHIFTS_VIEW` | `200 OK` |
| `GET` | `/api/shifts/current` | `SHIFTS_VIEW` | `200 OK` |
| `POST` | `/api/shifts/open` | `SHIFTS_OPEN` | `201 Created` / **`200 OK` on resume** |
| `POST` | `/api/shifts/{id}/close` | `SHIFTS_CLOSE` **or** `SHIFTS_FORCE_CLOSE` | `200 OK` |
| `GET` | `/api/expenses/selectable-shifts` | `EXPENSES_CREATE` | `200 OK` |

`GET /api/shifts/current` is matched before `/{id}` — literal paths win over templates.

### Permissions, exactly

| Action | Permission | Notes |
|---|---|---|
| Open / resume | `SHIFTS_OPEN` | |
| Close **your own** shift | `SHIFTS_CLOSE` | |
| Close **someone else's** | `SHIFTS_FORCE_CLOSE` | Recorded as `forcedClose: true` |
| Read list / detail / current | `SHIFTS_VIEW` | |
| See the money columns | `SHIFTS_VIEW_VARIANCE` | Omitted server-side without it |

The close endpoint's `@PreAuthorize` admits a caller holding **either** close permission, because
which one applies is not knowable until the shift is loaded and its opener known. The service then
enforces the exact one — so holding only `SHIFTS_FORCE_CLOSE` passes the gate and is still rejected
when closing your own shift, and vice versa.

**Consequence Part B must handle — two different error codes for the same refusal:**

| Caller holds | Closing a colleague's shift returns |
|---|---|
| `SHIFTS_CLOSE` only | `403` `SHIFT_FORCE_CLOSE_NOT_PERMITTED` (specific, from the service) |
| neither close permission | `403` `ACCESS_DENIED` (generic, from Spring's gate) |

Both are 403 and both mean "you may not close this". Translate both.

### Seeded role grants after `V58`

| Role | `SHIFTS_OPEN` | `SHIFTS_CLOSE` | `SHIFTS_VIEW` | `SHIFTS_FORCE_CLOSE` | `SHIFTS_VIEW_VARIANCE` |
|---|---|---|---|---|---|
| `OWNER` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `BRANCH_MANAGER` | ❌ | ❌ | ✅ | ❌ | ✅ |
| `CASHIER` | ✅ | ✅ | ✅ | ❌ | ❌ |

`CASHIER` receives neither new permission, and `V58` ends with a `DO $$` block that raises if it
ever does — so a later "grant the cashier everything shifts-related" edit has to argue with it.

Roles are a gate, not a permission source: permissions are direct user grants, so a user's actual
capability is their `user_permissions` rows, not their role.

---

## Enums

| Enum | Exact JSON values |
|---|---|
| `ShiftStatus` | `OPEN`, `CLOSED` |
| `OrderStatus` (detail) | `COMPLETE`, `CANCELLED` |
| `ExpenseStatus` (detail) | `ACTIVE`, `VOIDED` |
| `paymentMethod` (detail, string) | `CASH`, `CARD`, `WALLET` |

`status` and `forcedClose` are response-only. **`forcedClose` cannot be set by a client**: it is
derived from `closedBy != openedBy`, and `chk_shift_forced_close` rejects any row contradicting
that, so a client-supplied value cannot take even through a future code path.

---

## Request DTOs

### `OpenShiftRequest` — `POST /api/shifts/open`

| Field | JSON type | Presence | Constraints |
|---|---|---|---|
| `openingCount` | number | **required** | `>= 0`. Scaled to 6 dp. |

That is the whole body. No `deviceId`, no `branchId`, no cashier.

### `CloseShiftRequest` — `POST /api/shifts/{id}/close`

| Field | JSON type | Presence | Constraints |
|---|---|---|---|
| `closingCount` | number | **required** | `>= 0`. Scaled to 6 dp. |

**There is deliberately no `pendingCount` and no `forcedClose`.** See "The sync-queue precondition"
below.

---

## Response DTOs

### `ShiftResponse` — returned by `open`, `close`, and nested in `current`

| Field | JSON type | Nullability |
|---|---|---|
| `id` | integer | never null |
| `deviceId` / `deviceName` | integer / string | never null |
| `branchId` / `branchName` | integer / string | never null — reached **through the device** |
| `businessDate` | date | never null |
| `status` | `ShiftStatus` | never null |
| `openedByUserId` | integer | never null |
| `openedByUserName` | string | null if the user row is missing |
| `openedAt` | date-time | never null |
| `closedByUserId` | integer | null while `OPEN` |
| `closedAt` | date-time | null while `OPEN` |
| `forcedClose` | boolean | never null; always `false` while `OPEN` |
| `openingCount` | number | **null unless the caller is the shift's opener** |
| `closingCount` | number | null while `OPEN` |

**`openingCount` is withheld from a non-opener** — the cashier about to force-close a colleague's
drawer counts it blind (D122), and the opening float is the largest single component of the figure
they would otherwise aim at. It is also null in the `close` response of a **forced** close.

`closingCount` is not withheld: on an open shift it is null anyway, and on a close it is the
caller's own input echoed back.

### `CurrentShiftResponse` — `GET /api/shifts/current`

| Field | JSON type | Nullability |
|---|---|---|
| `shift` | `ShiftResponse` | **null when the device has no open shift** |

**No open shift is an ordinary result, not an error.** The endpoint returns `200` with
`{"shift":null}`. The previous implementation threw `NO_OPEN_SHIFT_FOR_CASHIER` here.

**Part B's login branch reads this body and never an error code:**

| Body | Meaning | Next screen |
|---|---|---|
| `shift == null` | first login of the day on this drawer | opening count |
| `shift.openedByUserId === me` | your own shift | resume, no count |
| otherwise | a colleague left it open | force-close screen |

Branching on a thrown code would make the normal path an exception, and any future error would be
misread as "no shift" — sending the cashier into an opening count on top of a live shift.

### `ShiftListItemResponse` — `GET /api/shifts` rows, and `detail.shift`

`@JsonInclude(NON_NULL)`: **absent keys mean null.**

| Field | JSON type | Notes |
|---|---|---|
| `id`, `businessDate`, `deviceId`, `deviceName`, `branchId`, `branchName` | | |
| `cashierUserId` / `cashierName` | integer / string | resolved from `openedByUserId` on read, never stored |
| `closedByUserId` / `closedByUserName` | integer / string | absent while `OPEN` |
| `openedAt` / `closedAt` | date-time | `closedAt` absent while `OPEN` |
| `durationMinutes` | integer | derived; absent while `OPEN` |
| `status`, `forcedClose` | | |
| `openingCount`, `closingCount` | number | `closingCount` absent while `OPEN` |
| `expectedCash` | number | **absent without `SHIFTS_VIEW_VARIANCE`** |
| `variance` | number | **absent without `SHIFTS_VIEW_VARIANCE`** |
| `handoverVariance` | number | **absent without `SHIFTS_VIEW_VARIANCE`** |

**`handoverVariance` is also absent on a device's first ever shift** — there is no prior count.
Absent here means *"no baseline exists"*, which is **not** the same as zero: zero would mean "the
drawer was counted and nothing had moved". Rendering a missing handover as `0` fabricates the
single strongest finding the module produces (D121). Part C must render it as blank or `—`.

### `ShiftDetailResponse` — `GET /api/shifts/{id}`

| Field | JSON type | Notes |
|---|---|---|
| `shift` | `ShiftListItemResponse` | |
| `salesByPaymentMethod` | object `{method: number}` | `{}` without `SHIFTS_VIEW_VARIANCE` |
| `cashSales` | number | null without the permission |
| `expensesAtClose` | number | frozen at close; null without the permission |
| `lateExpenses` | number | recorded **after** close; null without the permission, null while `OPEN` |
| `explainedVariance` | number | `variance + lateExpenses`; null without the permission |
| `orders` | array of `ShiftOrderLine` | always present |
| `expenses` | array of `ShiftExpenseLine` | always present |

The two arrays are currently present even without `SHIFTS_VIEW_VARIANCE`. Because their monetary
details combine with the ungated opening/closing counts, this current contract is the disclosure
under review, not proof that D123 is fully enforced.

`ShiftOrderLine`: `id`, `orderNo`, `orderDate`, `status`, `paymentMethod`, `totalAmount`,
`createdByUserId`, `createdByName`. Cancelled orders are included.

`ShiftExpenseLine`: `id`, `amount`, `expenseDate`, `description`, `payeeName`, `categoryId`,
`categoryName`, `status`, `recordedByUserId`, `recordedByName`, `createdAt`,
`recordedAfterClose` (boolean).

**`lateExpenses` is never added into `variance`, and the two are separate fields so a client cannot
accidentally sum them.** Merging them would let any shortfall be erased after the fact by recording
an expense for the matching amount — the easiest exploit in the system, and it would turn the
expenses screen into an eraser for variances. `explainedVariance` is supplied pre-computed
precisely so the client never does the addition itself and never gets the sign wrong. Render all
three (D124):

```
Variance at close      −300
Late expenses           300   ⚠ recorded after close
Explained variance        0
```

Voided expenses appear in `expenses` with `status: VOIDED`. They did **not** enter `expectedCash`,
but an expense attached to a drawer and then voided is exactly the sequence an investigation needs
to see, so it is not filtered out. Render the status.

### `SelectableShiftResponse` — `GET /api/expenses/selectable-shifts`

`id`, `businessDate`, `deviceId`, `deviceName`, `cashierUserId`, `cashierName`, `openedAt`,
`closedAt`, `status`, `closed` (boolean), `branchDeviceCount`.

`branchDeviceCount` lets the admin web preselect only when the branch has one device and exactly
one selectable shift matches the expense's fixed `businessDate`; it is not a money field.

**Carries no money figures of any kind** — this list is reachable by anyone who can record an
expense, a wider audience than may see variances.

---

## Query parameters

### `GET /api/shifts`

| Param | Type | Default |
|---|---|---|
| `branchId`, `deviceId`, `cashierUserId` | integer | unset = all |
| `dateFrom`, `dateTo` | `YYYY-MM-DD` | unset = unbounded; filters on **`businessDate`**, not `openedAt` |
| `status` | `OPEN` \| `CLOSED` | unset = both |
| `forcedClose` | boolean | unset = both |
| `page`, `size` | integer | `0`, `20` |

**Ordering is fixed and is not a `Pageable` default:**

```sql
ORDER BY ABS(variance) DESC NULLS LAST, business_date DESC, id DESC
```

By **magnitude**, not signed value. A drawer that persistently runs *over* is at least as strong a
signal as one that runs short — it means money is coming in that the system was not told about — so
sorting by signed variance would file every surplus at the far end and remove half the detection
(D119). Open shifts have no variance and sort last. A `sort=` query parameter is **not** honoured;
the screen exists to bring the anomalous to the top.

> The paginated envelope is Spring Data's `PageImpl` (`content`, `totalElements`, `totalPages`,
> `number`, `size`, `first`, `last`, `empty`, `pageable`, `sort`). Spring logs a warning that this
> shape is not guaranteed stable across versions. That is pre-existing across this codebase
> (expenses included) and was not changed here.

### `GET /api/expenses/selectable-shifts`

| Param | Type | Default |
|---|---|---|
| `branchId` | integer | **required** |
| `days` | integer | `7`; extendable per call |

---

## Structured errors

Standard envelope: `errorCode`, `message` (English, logs-only — never render it), `params`,
`status`, `timestamp`, `path`, `fieldErrors`. **Clients branch on `errorCode` and build the user
message from `params` via `translateApiError`.**

| `errorCode` | Status | `params` keys | When |
|---|---|---|---|
| `SHIFT_OPEN_BY_ANOTHER_USER` | 409 | `shiftId`, `deviceId`, `openedByUserId`, `openedByUserName`, `openedAt` | Open, drawer held by a colleague |
| `SHIFT_ALREADY_CLOSED` | 409 | `shiftId`, `closedAt` | Closing a closed shift |
| `SHIFT_NOT_FOUND` | 404 | `entityType`, `entityId` | Unknown shift, **or a shift on another device** |
| `SHIFT_FORCE_CLOSE_NOT_PERMITTED` | 403 | `shiftId`, `openedByUserId`, `requiredPermission` | Colleague's shift, holds `SHIFTS_CLOSE` but not force |
| `SHIFT_CLOSE_NOT_PERMITTED` | 403 | `shiftId`, `requiredPermission` | Own shift, lacks `SHIFTS_CLOSE` |
| `ACCESS_DENIED` | 403 | `{}` | Caller holds neither close permission |
| `DEVICE_IDENTITY_REQUIRED` | 401 | `claim` | Token has no `deviceId` — a web session |
| `DEVICE_INACTIVE` / `DEVICE_NOT_FOUND` | 401 | | Device deactivated or removed mid-session |
| `USER_INACTIVE` / `ROLE_INACTIVE` | 401 | | Account or role deactivated |
| `TOKEN_EXPIRED` / `TOKEN_INVALID` | 401 | | Retry `TOKEN_EXPIRED` via refresh; `TOKEN_INVALID` is terminal |
| `NO_OPEN_SHIFT_FOR_DEVICE` | 409 | `deviceId` | **`POST /api/orders`** with no open shift on the device |
| `EXPENSE_SHIFT_NOT_FOUND` | 404 | `paidFromShiftId` | Expense links an unknown shift |
| `EXPENSE_SHIFT_BRANCH_MISMATCH` | 409 | `paidFromShiftId`, `shiftBranchId`, `expenseBranchId` | Expense links another branch's drawer |

**A shift on another device returns `SHIFT_NOT_FOUND`, not 403.** Closing happens at the drawer
being counted; answering "exists, but not yours" would confirm shifts on devices the caller is not
standing at.

---

## The sync-queue precondition (D126) — a limit, stated plainly

**Close requires an empty sync queue. The server does not and cannot verify this.**

The queue lives in the device's local SQLite/OPFS and exposes no watermark, sequence number or
flush acknowledgement, so the server cannot distinguish an empty queue from orders a device has not
yet sent. A `pendingCount` in the close request would only be the caller asserting its own
compliance — a field the server cannot check reads as a verification and is not one. **It is
therefore deliberately absent from `CloseShiftRequest`.**

The rule is implemented in the POS (Part B). A modified client could close with orders outstanding
and the resulting variance would be wrong. That guards against the realistic threat — a cashier
working around the POS — and not against a fabricated client, which is an accepted trade.

**Anyone reading a `variance` from this API must not assume the order set was complete when it was
computed.** D121's arithmetic is valid only because of this precondition.

---

## Worked examples — transcribed from a real run

### `GET /api/shifts/current` — no open shift

```json
{"shift":null}
```

### `POST /api/shifts/open` → `201 Created`

```json
{"id":55,"deviceId":976003,"deviceName":"Till 1","branchId":976001,"branchName":"Main Branch",
 "businessDate":"2026-09-06","status":"OPEN","openedByUserId":976005,
 "openedByUserName":"Sara Ahmed","openedAt":"2026-09-06T17:09:38.182319659",
 "closedByUserId":null,"closedAt":null,"forcedClose":false,
 "openingCount":200.000000,"closingCount":null}
```

### `POST /api/shifts/open` again, same cashier → **`200 OK`**, body identical

Sent `{"openingCount":999.00}`; the response still reads `"openingCount":200.000000`. **The resume
writes nothing and the submitted count is discarded** — which is why Part B must not ask for a
count before calling `/current`.

`200` rather than `201` because nothing was created. A client can rely on the status to tell a
resume from a create.

### `GET /api/shifts/current` — a colleague's shift

```json
{"shift":{"id":55,...,"openedByUserId":976005,"openedByUserName":"Sara Ahmed",
 "forcedClose":false,"openingCount":null,"closingCount":null}}
```

`openingCount` is `null` — the caller is not the opener.

### `POST /api/shifts/open` on a colleague's drawer → `409`

```json
{"errorCode":"SHIFT_OPEN_BY_ANOTHER_USER",
 "message":"Device 976003 has an open shift belonging to user 976005",
 "params":{"shiftId":55,"deviceId":976003,"openedByUserId":976005,
           "openedByUserName":"Sara Ahmed","openedAt":"2026-09-06T17:09:38.182319659"},
 "status":409,"path":"/api/shifts/open","fieldErrors":null}
```

### `POST /api/shifts/open` with a web token → `401`

```json
{"errorCode":"DEVICE_IDENTITY_REQUIRED",
 "message":"Authenticated token is not bound to a POS device",
 "params":{"claim":"deviceId"},"status":401,"path":"/api/shifts/open"}
```

### `POST /api/shifts/{id}/close` → `200`

Opening 200, no orders, no expenses, counted 180.

```json
{"id":55,"deviceId":976003,"deviceName":"Till 1","branchId":976001,"branchName":"Main Branch",
 "businessDate":"2026-09-06","status":"CLOSED","openedByUserId":976005,
 "openedByUserName":"Sara Ahmed","openedAt":"2026-09-06T17:09:38.182319659",
 "closedByUserId":976005,"closedAt":"2026-09-06T17:09:38.343875634","forcedClose":false,
 "openingCount":200.000000,"closingCount":180.000000}
```

**No `expectedCash`. No `variance`.** The row stores `expected_cash = 200.000000` and
`variance = -20.000000`; neither appears above, and neither is a field of this DTO.

### `POST /api/shifts/{id}/close` again → `409`

```json
{"errorCode":"SHIFT_ALREADY_CLOSED","message":"Shift is already closed: 55",
 "params":{"shiftId":55,"closedAt":"2026-09-06T17:09:38.343875634"},"status":409}
```

### `GET /api/shifts` — **with** `SHIFTS_VIEW_VARIANCE`

```json
{"content":[{"id":55,"businessDate":"2026-09-06","deviceId":976003,"deviceName":"Till 1",
  "branchId":976001,"branchName":"Main Branch","cashierUserId":976005,"cashierName":"Sara Ahmed",
  "closedByUserId":976005,"closedByUserName":"Sara Ahmed",
  "openedAt":"2026-09-06T17:09:38.18232","closedAt":"2026-09-06T17:09:38.343876",
  "durationMinutes":0,"status":"CLOSED","forcedClose":false,
  "openingCount":200.000000,"closingCount":180.000000,
  "expectedCash":200.000000,"variance":-20.000000}],
 "totalElements":1,"totalPages":1,"number":0,"size":20,"first":true,"last":true,"empty":false}
```

Note `handoverVariance` is **absent**: this is the device's first shift, so there is no prior count.

### `GET /api/shifts` — **without** `SHIFTS_VIEW_VARIANCE`

Identical row, ending:

```json
  "openingCount":200.000000,"closingCount":180.000000}
```

`expectedCash`, `variance` and `handoverVariance` are **gone from the payload entirely** — not
present-and-null. Part C must handle their absence without breaking the table.

### `GET /api/shifts/{id}` — with the permission

```json
{"shift":{...as the list row...,"expectedCash":200.000000,"variance":-20.000000},
 "salesByPaymentMethod":{},"cashSales":0.000000,"expensesAtClose":0.000000,
 "lateExpenses":0.000000,"explainedVariance":-20.000000,"orders":[],"expenses":[]}
```

---

## Timestamp precision, noted because it surprises

The **write** responses carry nanosecond precision straight from `LocalDateTime.now(zone)`
(`17:09:38.182319659`). The **read** endpoints return the same instant at PostgreSQL's microsecond
precision (`17:09:38.18232`). Same moment, different tails. Do not compare the two as strings.

---

## What does not exist

- **No `CashDrawer` entity, table, or drawer-discovery endpoint.** The device is the drawer (D119).
  The POS sends no drawer identifier, so it cannot misreport one.
- **No drawer balance, anywhere.** Never stored, never returned. A stored balance would be a second
  copy of a truth that already exists, and two copies drift.
- **No drawer ledger, no float top-up, no safe drop, no drawer transaction type.** Nothing writes
  to the drawer between the two counts.
- **No separate count table.** Counting happens at exactly two moments and each already has a row.
- **No `pendingCount` on close** — see the sync-queue section.
- **No `forcedClose` input.** Derived and constrained.
- **No X report / Z report endpoint.** The old `GET /current` doubled as a live X report returning
  order counts, totals, averages and per-payment-method sums; all of it is gone from the POS
  surface. The equivalent figures live on `GET /api/shifts/{id}` behind `SHIFTS_VIEW_VARIANCE`.
- **No refunds.** D121's formula subtracts cash refunds, but **refunds do not exist anywhere in
  this system** — `ORDERS_REFUND` is a seeded permission with no entity, column, endpoint or
  service behind it. The term is structurally zero, not omitted by choice, and `expectedCash` is
  complete only while that stays true.
- **No split payment.** `Order.paymentMethod` is a single enum (O63), so an order paid part cash
  part card is recorded entirely under one method — a known limit on every drawer figure's
  accuracy.
- **No `PosShiftEvent`** (O54). The backend sees only orders arriving `COMPLETE` or `CANCELLED`;
  everything voided inside the POS before payment is invisible. This is why the cashier performance
  report is **not** in Part C.
- **No spot count** (O56), **no `varianceTolerance`** (O57), **no card settlement** (O55), **no
  end-of-day handover** (O60), **no notifications** (O61), **no branch scoping** (O62), **no drawer
  transfer between devices** (O64), **no offline shift open** (O65).
- **No server-verifiable sync barrier.** D126 records why.

## Legacy

`shift_legacy` and `shift_legacy_order` hold the pre-D119 rows and their order links. Read-only
history, no API. Nothing reads them; they exist so the rewrite destroyed nothing.
