# Manual test plan — D112 to D128

> Written 2026-09-19 alongside the DECISIONS re-verification pass. Covers **what is built**, in the
> order the state has to be built up. Section 11 lists what is **not** built, with how you would
> notice each absence.
>
> Every case names the decision it proves. A case that fails is a code bug, not a reason to change
> the decision (CLAUDE.md).

## 0. Setup

**Stack**

| Piece | Command | URL |
|---|---|---|
| Postgres | must be running; `restaurant-saas` database | `localhost:5432` |
| Backend | `./mvnw spring-boot:run` | `localhost:2020`, Swagger at `/swagger-ui.html` |
| Admin web | `npm run dev` in `restaurant-saas-web` | Vite default |
| POS | `npm run dev` in `restaurant-pos` | Vite default |

Flyway must reach **V58**. Check before anything else:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 8;
```

**Fixtures to create once, through the admin web**

1. One branch with a timezone set (D101), one warehouse under it.
2. **Two devices** on that branch — `DEV-A`, `DEV-B`. Two are required: one open shift per device is
   the invariant, and `handoverVariance` chains per device.
3. **Users:** `owner` (OWNER), `mgr` (BRANCH_MANAGER), `cash1` and `cash2` (CASHIER).
   Do **not** grant `SHIFTS_VIEW_VARIANCE` or `SHIFTS_FORCE_CLOSE` to `cash1` — several cases
   depend on the cashier not holding them. Grant `SHIFTS_FORCE_CLOSE` to `cash2` only when §7
   asks for it.
4. A couple of menu products with prices, for cash orders.
5. Materials and a supplier, for §9 and §10.

Every API call carries `X-Tenant-Id` and `Authorization: Bearer <token>`.

---

## 1. D127 — tokens and device binding

Everything below depends on this, so it goes first.

| # | Do | Expect | Proves |
|---|---|---|---|
| 1.1 | `POST /api/auth/login` as `cash1` **with** `deviceId` = `DEV-A` | 200, access token + refresh token. Decode the access token on jwt.io — it carries the `deviceId` claim | device identity is signed, not header-supplied |
| 1.2 | Login as `cash1` with a `deviceId` belonging to another tenant, or a deactivated device | rejected | login validates tenant + active state before signing |
| 1.3 | Login as `owner` **without** `deviceId` | 200, no `deviceId` claim | optional for web sessions |
| 1.4 | With the 1.3 token, `POST /api/shifts/open` | rejected — no device in token | a web manager cannot operate a drawer |
| 1.5 | While holding a valid `cash1` token, deactivate the device in admin web, then call any shift endpoint | rejected on the next request, not at token expiry | filter rechecks device live |
| 1.6 | Same, but deactivate the **user** | next request rejected, and the refresh token is revoked | live revocation |
| 1.7 | `POST /api/auth/refresh` with a refresh token, then call refresh **again with the same token** | first succeeds and returns a *new* refresh token; second fails | rotation under a row lock |
| 1.8 | `POST /api/auth/logout`, then refresh with that token | rejected | revoke on logout |
| 1.9 | `SELECT token_hash FROM refresh_token LIMIT 1;` | a 64-char hex hash, never the token you hold | only the SHA-256 is stored |

## 2. D119 + D120 — the shift is the device's

| # | Do | Expect | Proves |
|---|---|---|---|
| 2.1 | `GET /api/shifts/current` as `cash1` on `DEV-A` before opening anything | 200 with `{"shift": null}` — **not** an error | an empty drawer is an ordinary result |
| 2.2 | `POST /api/shifts/open` with `openingCount: 500` | **201**, shift returned, `deviceId` = `DEV-A`, `businessDate` = today in the **branch** zone | D119 device anchor, D120 businessDate column |
| 2.3 | Call open again as `cash1` on `DEV-A` with `openingCount: 999` | **200** (not 201). `SELECT opening_count FROM shift` still shows **500** | resume never overwrites the count |
| 2.4 | Login `cash2` on **the same** `DEV-A`, call open | `SHIFT_OPEN_BY_ANOTHER_USER` | one open shift per device |
| 2.5 | Try to force a second open row directly: `INSERT INTO shift (...) VALUES (... same device_id, 'OPEN' ...)` | Postgres rejects on `uk_shift_open_per_device` | enforced by the database, not the service |
| 2.6 | `cash2` opens on `DEV-B` | 201 — a separate drawer, both open at once | the constraint is per device |
| 2.7 | Open a shift near midnight (or set the branch zone so it is), leave it open past the local date change, then `GET /api/shifts/{id}` | `businessDate` unchanged | fixed at open, never re-derived |
| 2.8 | In the POS, look for a sign-out button | there is none; only close | D120 |

## 3. D126 — the offline boundary

Use the POS with Chrome DevTools → Network → **Offline**.

| # | Do | Expect | Proves |
|---|---|---|---|
| 3.1 | Go offline, take two cash orders to payment | both complete locally, receipts print-preview, queue count rises | selling is fully local |
| 3.2 | Still offline, press Close | refused, and the message shows **the number** of pending orders — not a generic retry | close waits for the queue |
| 3.3 | Go online, let the queue drain, then Close | allowed | |
| 3.4 | Force an order into `SYNC_ERROR` (stop the backend mid-sync), then Close | **also refused**, and the number shown equals the number the block checked | both queue states block |
| 3.5 | Go offline and press Open on a device with no shift | refused — open needs connectivity | O65, known and accepted |
| 3.6 | Leave an **unpaid** ticket open and close the shift | close is allowed; the ticket is still there for the next session | in-progress tickets are not money |
| 3.7 | Close successfully, then check `refresh_token` for that user | revoked | close signs out on the server |
| 3.8 | Close while the backend logout endpoint is unreachable | session ends locally, revocation is retained and retried later | durable logout-pending |
| 3.9 | With paid-but-unsent orders in the queue, run device reset | asks for explicit confirmation naming the count and permanent deletion | only force reset may discard them |

## 4. D115 + D116 + D117 — expenses on their own

| # | Do | Expect | Proves |
|---|---|---|---|
| 4.1 | Open the expenses create form | the boundary text is visible: anything entering a warehouse is a purchase document, not an expense | D115 |
| 4.2 | Create an expense: amount, category, `expenseDate`, no branch | accepted — `branchId IS NULL` is a company-level expense | D115 |
| 4.3 | Create one with amount `0` or `-50` | rejected | amount strictly positive |
| 4.4 | `GET /api/expense-categories` as a fresh tenant | 13 global rows (Rent … Other), each with `nameAr` | D116 seeds |
| 4.5 | Try to rename or deactivate a **global** category as a tenant | refused | global rows are read-only to tenants |
| 4.6 | Create a tenant category, use it on an expense, then deactivate it | the expense still renders the name; the category disappears from the picker | retired rows keep rendering |
| 4.7 | `PUT /api/expenses/{id}` and `DELETE /api/expenses/{id}` | **404 / 405 — the routes do not exist** | D117 append-only |
| 4.8 | `POST /api/expenses/{id}/void` with no reason | rejected | reason is required |
| 4.9 | Void with a reason | row shows struck through **in the list**, reason visible, `voidedBy`/`voidedAt` stamped; totals drop it | the trace is the point |
| 4.10 | As a user with `EXPENSES_CREATE` but not `EXPENSES_VOID`, try to void | 403 | the split is deliberate |
| 4.11 | As a user with only `EXPENSES_VIEW`, try to create | 403 | |

## 5. D118 + D124 — linking an expense to a drawer

Needs an open shift from §2 and a closed one.

| # | Do | Expect | Proves |
|---|---|---|---|
| 5.1 | Create an expense with `paymentSource: CASH_DRAWER` from the **page** form | a shift picker appears, filtered to the expense's branch and a 7-day window, showing cashier name, business date, open/close times, device, status | D124 |
| 5.2 | Same from the **modal** form | the picker is there too | both creation surfaces |
| 5.3 | Branch has exactly one device and one shift covering the date | it is preselected, and still changeable | |
| 5.4 | Look at how the cashier name is rendered vs. `SELECT * FROM shift` | the name is **not** a column on the shift — it is resolved from `openedByUserId` | no denormalised copy |
| 5.5 | Open the picker on a **closed** shift | it is listed and selectable, labelled with the consequence ("will be linked, but the recorded variance will not change") | the manager is told before, not after |
| 5.6 | Record an expense of 100 against an **open** shift, then close it | it lands inside `expensesAtClose` and moves `expectedCash` | recorded before close counts |
| 5.7 | Record an expense of 300 against a shift **already closed** | stored and linked; `SELECT variance, expenses_at_close FROM shift` is **unchanged** | freeze at close |
| 5.8 | Open the shift detail for that shift | `Variance at close`, `Late expenses`, `Explained variance` shown as **three separate figures**, never merged | the anti-eraser rule |
| 5.9 | Look at each expense row in shift detail | shows who recorded it and when, not just a total | investigation needs the actor |
| 5.10 | Record an expense with `expenseDate` three days before `createdAt` | both are shown, the gap is visible | D118 |
| 5.11 | Check the times on shift detail against the branch timezone | expense times and late classification use **branch** time | 2026-09-06 review decision |

## 6. D121 — the two variances

| # | Do | Expect | Proves |
|---|---|---|---|
| 6.1 | On a **brand-new device**, open and close the first shift | `handover_variance IS NULL` — **not 0** | a zero here is a fabricated finding |
| 6.2 | Close shift 1 on `DEV-A` counting 800. Open shift 2 on `DEV-A` counting **780** | `handover_variance = -20` on shift 2, in its own column, not folded into `variance` | the drawer sat closed; this has no innocent explanation |
| 6.3 | Open at 500, take 200 cash sales, record a 50 drawer expense, count **650** | `expected_cash = 650`, `variance = 0` | the formula |
| 6.4 | Same but count **700** | `variance = +50` and it is reported just as loudly as a shortfall | surplus is not the lesser finding |
| 6.5 | Take a **card** order, then close | it does not move `expectedCash` | card never enters the drawer |
| 6.6 | Check `SELECT * FROM shift` for any stored running balance column | there is none — balance is derived | D119 |

## 7. D122 — force close

| # | Do | Expect | Proves |
|---|---|---|---|
| 7.1 | `cash1` opens on `DEV-A` and walks away. `cash2` logs in on `DEV-A` | the POS lands on a **force-close screen**, not an open screen | |
| 7.2 | `cash2` has `SHIFTS_CLOSE` but **not** `SHIFTS_FORCE_CLOSE`, tries to close | rejected — and note the endpoint gate passes, the service rejects | the exact permission is enforced after load |
| 7.3 | Grant `SHIFTS_FORCE_CLOSE` to `cash2`, retry | closes. `forced_close = true`, `closed_by_user_id` = cash2, variance recorded **against cash1's shift** | D122 |
| 7.4 | With `SHIFTS_FORCE_CLOSE` only (no `SHIFTS_CLOSE`), try to close your **own** shift | rejected | the split works both ways |
| 7.5 | `UPDATE shift SET forced_close = true WHERE closed_by_user_id = opened_by_user_id;` | Postgres rejects on `chk_shift_forced_close` | derived, never client-supplied |
| 7.6 | During 7.1, check what `cash2` can see of cash1's shift | **no `openingCount`** — it is nulled for anyone but the opener | D122 §2: the closer counts blind |
| 7.7 | Check whether `CASHIER` was granted either new permission | `V58` raises an exception at migration time if it was | the seed asserts it |

## 8. D123 — the blind count, **including the known violation**

| # | Do | Expect | Proves |
|---|---|---|---|
| 8.1 | POS: `GET /api/shifts/current` on an open shift, read the **raw network response** | no `expectedCash`, no `variance`, no `handoverVariance`, no `expensesAtClose`, no order totals | fields absent by construction |
| 8.2 | POS: close a shift and read the raw response | same — **no variance after closing either** | D123 |
| 8.3 | `SELECT * FROM shift WHERE status='OPEN'` | `expected_cash`, `variance`, `expenses_at_close` are all NULL on an open row | `chk_shift_close_fields` |
| 8.4 | `GET /api/shifts` as `cash1` (has `SHIFTS_VIEW`, not `SHIFTS_VIEW_VARIANCE`) | variance columns omitted | gated once, in `ShiftQueryService` |
| 8.5 | **`GET /api/shifts/{id}` as `cash1`** | ⚠️ **currently returns `salesByPaymentMethod`, `cashSales`, the order list and the expense list** — add them up and you have the expected figure | **this is the open violation.** Finding 1 in [SHIFT_REVIEW_FOLLOWUP.md](SHIFT_REVIEW_FOLLOWUP.md). Record what you see; it is the input to fixing it |
| 8.6 | Same call as `mgr` (has `SHIFTS_VIEW_VARIANCE`) | full figures — correct for this caller | |

## 9. D112 — document numbers

| # | Do | Expect | Proves |
|---|---|---|---|
| 9.1 | Create a purchase invoice, a purchase return, a waste doc and a physical count | codes read `PI/26/000001`, `PR/26/…`, `WS/26/…`, `PC/26/…` (`YY` = the tenant's current year) — **no branch, warehouse or tenant-code segment** | D112 |
| 9.2 | Create two invoices back to back | `000001`, `000002` — no gap, no reuse | |
| 9.3 | `SELECT * FROM document_sequence;` | one row per (tenant, type, year) | |
| 9.4 | Set the tenant timezone to one where the **local** year differs from the server's, then create a doc on Dec 31 / Jan 1 | the `YY` segment follows the **tenant's** wall-clock year | D112 + D101 |
| 9.5 | Two users create the same document type simultaneously | both get distinct numbers | allocator is serialised |

## 10. D113 — expiry and age

| # | Do | Expect | Proves |
|---|---|---|---|
| 10.1 | Material form → toggle `expiryTracked` on | saved, badge shows on the material overview | dated track |
| 10.2 | Purchase invoice line for that material with an `expiryDate`, post it | the opened batch carries that `expiryDate` **and** a `warehouseEntryDate` = the receipt's actual arrival date | §3 |
| 10.3 | Warehouse stock → expand the batch sub-row | a `daysRemaining` column, sorted, coloured, `—` where null | **PART B — the newly shipped frontend surface** |
| 10.4 | Batch whose expiry has passed | negative `daysRemaining`, warning icon | |
| 10.5 | Material with `expiryTracked = false`, set `maxAgeDays` on the warehouse stock row | `daysRemaining` computed from `maxAgeDays` − age in this warehouse | fresh track |
| 10.6 | `expiryTracked = true` but no `expiryDate` on the batch | renders `—` and is otherwise invisible | accepted limit until the report exists |
| 10.7 | `SELECT * FROM stock_batch` | no stored age or `daysRemaining` column — both are computed | never persisted |
| 10.8 | Post a purchase, check FIFO consumption order afterwards | unchanged — still `movementDate ASC, id ASC` | D113 does not touch FIFO (D10) |

---

## 11. What is **not** built — what you will find when you look

Do this pass second, and expect these. None of them is a bug.

| Decision | Status | How the absence shows up |
|---|---|---|
| **D114** warehouse transfer | 🕓 not built | The admin web has transfer pages that call `/api/inventory/transfers` — **those routes do not exist**, so the screens fail. `InventoryTransfer` is an entity with no repository or service. `DocumentType` has no `TO`/`TI` values, which is all the numbering side needs. |
| **D128** media attachments | 🕓 not built | No `media_link` table, no migration, no upload endpoint anywhere. No expense receipt image, no product photo. |
| **D125** cashier performance | ⚠️ one of three surfaces missing | Shifts list and shift detail work (§5, §8). There is **no** cashier performance screen or endpoint: no cancellation ratio, no discount ratio, no mean or cumulative variance, no "shifts force-closed by others" and no "shifts they force-closed for others". O54 notes the cancellation ratio would be incomplete even if built, because the POS sends no event trail. |
| **O57** `Branch.varianceTolerance` | not built | Every non-zero variance flags; there is no per-branch threshold. |
| **O56** spot counts | deferred | Open and close are the only counted moments, both predictable to the person being measured. |
| **O63** split payment | deferred | An order paid part cash part card records entirely under one method — every drawer figure inherits that error. |
| **O55** card settlement | not built | Card takings are in no shift figure and there is no receivable tracking. |
| **O66** `V48` | recorded, not fixed | `V47 → V49`. Never author a migration below the current maximum; it would silently never run. |

### Open review findings to re-check while testing (not new work)

These are known and unresolved — see [SHIFT_REVIEW_FOLLOWUP.md](SHIFT_REVIEW_FOLLOWUP.md):

1. **Blind-count disclosure** — case 8.5 above reproduces it.
2. **Competing closes** — two close requests on one shift at the same instant are not serialised.
   Sequential rejection is not evidence; this needs concurrent requests to test properly.
3. **Expense/close race** — an expense created at the moment of close can miss both the frozen sum
   and the late classification. Try creating one while close is in flight.
4. **Shifts-list filter authorization** — the cashier/device filter options need unrelated
   user/device-management grants.
5. **Reconciliation detail** — an authorized manager still cannot see opening/closing/expected cash
   plus handover variance in one authorized view.
6. **Frontend/backend permission mismatch** — `shiftAccess.ts` still has owner shortcuts the backend
   does not honour. See [UI_PERMISSIONS_PLAN.md](UI_PERMISSIONS_PLAN.md).
