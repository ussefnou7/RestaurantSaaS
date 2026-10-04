# MVP issues, by priority

> Consolidated 2026-10-03 from [MVP_LEAK_LEDGER.md](MVP_LEAK_LEDGER.md), which remains the source
> of truth — this file is a reading order, not a second register. Evidence for every row observed
> this pass lives under
> [`restaurant-saas-web/docs/evidence/mvp-e2e/`](../../restaurant-saas-web/docs/evidence/mvp-e2e/).
>
> **Status discipline:** `VERIFIED` means Claude re-ran the originating scenario after the fix.
> `RESOLVED` means a fix was committed but has not been re-run. Only the first is a guarantee.

## Where things stand

| | Count |
|---|---|
| Open, blocking (P0) | **7** |
| Open, ship-blocking polish (P1) | **17** |
| Open, after-MVP (P2) | **3** |
| Fixed **and verified** | **5** |
| Dropped — no longer reproduces | **6** |
| Out of MVP scope (M2), not to be fixed | 12 |

---

# Tier 1 — cannot ship with these

## Operational readiness — nothing here is a code bug, and all of it blocks a real client

| # | ID | Issue |
|---|---|---|
| 1 | **L033** | **No production configuration.** `application.yml` is the only profile — no `application-prod.yml`, no compose file, no deployment target. `APP_JWT_SECRET` defaults to the literal `change-this-dev-secret-change-this-dev-secret` and nothing verifies it was overridden before a tenant's data sits behind it. |
| 2 | **L035** | **No backup or restore procedure, and none rehearsed.** Going live with a paying tenant's cash and stock history before a restore has been practised risks unrecoverable loss. |
| 3 | **L032** | **The entire `electron/` directory is untracked.** `main.ts`, `preload.ts`, `protocol.ts`, `printing/`, `settings.html` exist only in a working tree. The desktop shell is the one artefact the client installs, and no build of it is reproducible. |
| 4 | **L031** | **No Windows build exists, and the 32-bit half cannot be built.** Electron 44 removed Windows ia32; 32-bit requires pinning to 43.7.3. No installer has been produced or run on any Windows machine. |

## Correctness and safety

| # | ID | Issue |
|---|---|---|
| 5 | **L042** | **Branch creation is unachievable unaided.** `code: "MAIN"` → `400 LEGACY_ERROR`, `params: {}`; the requirement `"Code must start with TAGREBA-BR-"` sits only in `message`, which is **never rendered**. The user sees *"An error occurred, please try again"* forever. Branch gates warehouse → device → shift → orders → reports, so onboarding stops here. **Blast radius: 18 raw `ApiException` sites** (16 `tenant`, 2 `auth`). Note the irony: warehouses, suppliers and materials all auto-generate `TAGREBA-XX-NNNN`. |
| 6 | **L006** | **No guard-reversion audit has ever been done.** Passing tests do not establish that each protection fails when removed. The project's own history — a commented-out guard surviving 22 days behind a green suite — is why this matters. |
| 7 | **L015 / L016** | **No CI in the backend or the web app**, and `restaurant-saas-web` has **zero tests and no runner**. 129 backend test files run only when someone remembers. |

> **L007** (competing shift closes) and **L008** (the expense/close race) are also P0 in the ledger.
> They are listed under Tier 2 → *Needs a test, not a fix* because the mechanism is already in place
> and what is missing is concurrency evidence.

---

# Tier 2 — fix before the client sees it

## Onboarding blockers

| ID | Issue |
|---|---|
| **L056** | **A new tenant starts with zero expense categories, so no expense can be recorded at all.** Regression from the L041 fix — collapsing global categories into tenant-owned ones deleted the 13 bilingual defaults with no per-tenant seeding. `categoryId` is `@NotNull`, so the module is unusable on a fresh tenant and nothing says why. |

## Data integrity

| ID | Issue |
|---|---|
| **L048** | **Unknown JSON fields are silently ignored app-wide.** `{"amount":11,"amuont":99999,"branchID":9999}` → `201` storing `amount 11`, `branchId null`. One mistyped character loses data and returns success. Compounded by dead API surface: `PurchaseInvoiceRequest` declares `lines` and `invoiceNumber` and is referenced nowhere — a client built from it creates empty invoices. |
| **L044** | **No chronological validation on any asset date.** Disposal a year before purchase, disposal in 2030, maintenance before the asset existed, purchase date 2030 — all `201`. `INVALID_DATE_RANGE` guards only a `dateFrom`/`dateTo` filter pair, never a stored date. |
| **L049** | **`unpost` leaves `last_purchase_price` / `last_purchase_date` stale.** Quantity and average cost revert correctly; these two keep pointing at a withdrawn invoice and surface on the stock detail screen. |
| **L053** | **Customer phone is never normalised server-side, so one person becomes several customers.** `+201012345678`, `00201012345678`, `0101 234 5678` and `01012345678` with a trailing space each created a new record. Proved on the POS path: an order with a `+20` prefix attached to the duplicate, not the original. The rule exists only in the browser. |

## HR — tracked separately in [HR_PRE_MVP_FIXES.md](HR_PRE_MVP_FIXES.md)

| ID | Issue |
|---|---|
| **L050** | **An employee's salary can never be changed after the first one.** Seven dates, all `409 DATA_INTEGRITY_VIOLATION`. Hibernate flushes the new `active=true` INSERT before the old row's `active=false` UPDATE, colliding with `ux_employee_salaries_active`. |
| **L052** | **Overlapping leave is approved and the balance charged for each.** Identical dates twice plus an overlap → `usedDays 3 → 9` for 4 calendar days. |
| **L051** | **Adjustments accept impossible amounts and dates.** A 999,999 deduction against a 6,000 salary, and adjustments dated 2030 or before the hire date. |

## User-facing

| ID | Issue |
|---|---|
| **L055** | **The Void action is clipped out of the expenses list — invisible in Arabic.** `width: 110px` + `white-space: nowrap` on a cell whose content needs 234–247px. In `ar` the button sits at `x −41→41` against a cell starting at 41: zero pixels. In `en`, ~10px of 79 survive. Void is the only correction path for an expense; the detail page is the workaround. |
| **L045** | **Six translation keys render raw.** Five are missing from **both** dictionaries, so they show raw in English too: `common.accessDenied` (the entire text of an error banner), `expenses.categories.globalReadOnlyNotice`, and `orders.type.DINE_IN/TAKEAWAY/DELIVERY` in the sales-by-hour filter. Measured: 2,873 `en` keys vs 2,872 `ar` — coverage is not the problem, these six are. |
| **L043** | **Asset audit columns are 100% null and attribution is spoofable.** `X-User-Id: 999` → `created_by = 999` for a user that exists in no tenant. **Sequenced behind O29** — DECISIONS.md warns that fixing this in isolation produces "a complete, uniform, and untrustworthy audit trail". |

## Needs a test, not a fix

| ID | Issue |
|---|---|
| **L007** | **Competing shift closes.** The row lock is in place (`ShiftService.java:163`) and held under two concurrent `curl` requests — `777` → `200`, `999` → `409`, DB kept 777. But parallel curl cannot be shown to interleave inside the critical window, so a pass is weak evidence. Needs a deterministic latch-based test. |
| **L008** | **The expense/close race.** A millisecond window between freezing the sum and writing `closedAt`. Not hand-reproducible. |
| **L018** | `@SpringBootTest` runs against the configured development database — no Testcontainers. |

## Carried from earlier rounds

**L005** shift-list filters need unrelated grants · **L009** purchase return is UOM-locked in the UI only · **L010** displayed line values need not sum to the document total · **L011** orders have no `cancelledAt` · **L013** no authorized reconciliation view · **L014** enum translation keys (in progress) · **L034** `.env.example` advertises port 8080 · **L036** Electron 43 EOL January 2027 · **L037** the Windows build targets one architecture silently.

---

# Tier 3 — after the MVP

| ID | Issue |
|---|---|
| **L054** | Two tables may carry the same name in the same section. Orders bind by id so data is correct; the floor plan shows identical labels. |
| **L046** | **A comment asserts the opposite of the code.** `shiftAccess.ts:18-23` claims no endpoint enforces `SHIFTS_VIEW_VARIANCE`; `ShiftQueryService.java:65,75` does, gating seven fields. The false belief propagated into a type-system bypass, an exclusion from `screen-map.ts`, and **a P0 that was re-confirmed on its strength**. |

---

# Fixed and verified

| ID | Fix | Verification |
|---|---|---|
| **L038** | `fde7f37` | Min 8 chars, letter + digit, on five DTOs. Boundary probed: `abc12` → length, `abcdefgh` → pattern, `Passw0rd` → accepted. *Residual: pre-existing weak passwords are not retroactively invalidated.* |
| **L039** | `289a2af` | 12 failed logins → four `401` then **`429`** from the fifth. |
| **L040** | `d9bd565` | `CASH_DRAWER` with no branch → **`400 EXPENSE_DRAWER_BRANCH_REQUIRED`**. |
| **L047** | `b4acdc6` | Invoice dated 2030 → **`400 INVENTORY_MOVEMENT_DATE_IN_FUTURE`**. |
| **L041** | `8e20e94` | Model change, not a constraint: global scope removed, one unconditional unique index, `Gas`/`gas`/`GAS` all `409`. **Introduced L056.** |

> **L003 drop stands** — the blind-count disclosure does not reproduce; a cashier gets `openingCount`
> absent, `cashSales: null`, `expenses: []`, enforced server-side at `ShiftQueryService.java:65,75`.
> The ledger had re-confirmed it as a P0 by reading a stale **frontend** comment (see L046).
>
> **L042's drop was reversed.** It was set to `DROPPED` with no reason and no fix commit; re-running
> the scenario shows it reproducing unchanged. Per the handoff protocol such a row belongs in
> `DISPUTED` with a reason, and Codex does not close its own disputes.

---

# What was tested and holds

Recorded so fixes are not mistaken for rewrites, and so this is not read as a list of a broken
system. All verified against a running stack on a fresh database this pass:

- **The cash-to-stock core, end to end.** Purchase → stock (UOM conversion correct across ledger,
  balance and batch) → recipe → cash order → consumption staging → scheduler → `CONSUMPTION_SUMMARY`
  → FIFO depletion in the right batch order → purchase return → waste → physical count with both
  surplus and shortfall, each valued at the current average.
- **Shift arithmetic with real cash.** `opening + cashSales − expenses = expectedCash`, card and
  cancelled orders correctly excluded from the drawer. The frozen close sum holds: a late expense is
  stored and linked and does **not** move the recorded variance.
- **Branch-time handling.** Shift timestamps in branch time, expense audit times in tenant time, and
  an explicit documented conversion where they are compared.
- **Tenant isolation.** 9 cross-tenant reads and 6 cross-tenant writes all blocked; lists, flat
  cross-asset reads and reports all scoped; `X-Tenant-Id` ignored entirely.
- **Permissions.** 20/20 — every denial correct, every privilege-escalation path blocked, and an
  `OWNER` holding 99 permissions still cannot cross into system administration.
- **Reports.** Ten figures cross-checked against raw SQL and matching.
- **Media.** 13/13 — spoofed content types rejected, size ceiling enforced, single-valued cardinality
  held by a partial unique index, superseded files enqueued for deletion, no URL ever stored.
- **Arabic / RTL.** 16 screens with zero raw keys and, apart from L055, zero clipping at 1440×900.
- **Asset status derivation** across every transition including multi-line aggregation, and 8/8 guards.

## Four false alarms, recorded so they are not re-investigated

`stock_balance.uom_id` (the balance is denominated in display UOM — a 1000× error that was not one) ·
the purchase-price-drift report (it reads the invoices, not the stale columns) · a 1 KG count
discrepancy (a consumption document posted in the same window) · and my first L041 probe.

Each looked like a P0 and dissolved on inspection. The pattern is worth keeping: **read the ledger
before filing the number.**
