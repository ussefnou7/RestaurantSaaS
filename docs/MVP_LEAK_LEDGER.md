# MVP leak ledger

> The single shared artefact for [MVP_HARDENING_PLAN.md](MVP_HARDENING_PLAN.md). Claude files,
> Codex fixes, Claude verifies. Findings live **here and nowhere else**.
>
> Opened 2026-09-24, seeded from existing documentation — see §Seeded. Nothing below has been
> re-verified against running code yet; seeded rows carry the date of the doc they came from and
> must be confirmed or dropped during Phase 1/2.

## Baseline

Filled in by Phase 0. Until every cell is filled, **no scenario may be run.**

| | Value |
|---|---|
| `restaurant-saas` commit | _pending_ (last commit before freeze: `85d9b7a`) |
| `restaurant-saas-web` commit | _pending_ (last commit before freeze: `fa426b7`) |
| `restaurant-pos` commit | _pending_ (last commit before freeze: `99c6463`) |
| Backend suite | _pending_ — expected ≥ 687 tests, `Failures: 0` |
| POS suite | _pending_ — 21 Vitest files |
| Admin-web build | _pending_ |
| Flyway top version | _pending_ — expected ≥ V63 |
| MVP database name | _pending_ |

## Columns

| Column | Written by | Meaning |
|---|---|---|
| `ID` | Claude | `L###`, never reused |
| `Sev` | Claude | `P0` blocks MVP · `P1` ship-blocking polish · `P2` after MVP · `OUT` outside MVP scope (M2) |
| `Status` | both | `OPEN` → `IN PROGRESS (codex)` → `FIXED` → `VERIFIED` · or `DISPUTED` / `DROPPED` |
| `Repro / source` | Claude | Evidence folder for observed leaks; doc reference for seeded ones |
| `Fix commit` | Codex | Short SHA, one per row |

`VERIFIED` is set by Claude only, and only after re-running the scenario that found it.

---

## Open findings

_(Phase 1 and Phase 2 append here. Newest at the bottom.)_

| ID | Area | Sev | Finding | Repro / source | Status | Fix commit |
|---|---|---|---|---|---|---|
| — | — | — | _no observed findings yet — testing has not started_ | — | — | — |

---

## Seeded from existing documentation

Known before this pass began. They are here so Phase 2 does not spend a day rediscovering them, and
so none is quietly forgotten. **Each still needs confirmation against the frozen baseline** — a
seeded row that no longer reproduces goes to `DROPPED` with the commit that fixed it.

### Authorization and isolation

| ID | Area | Sev | Finding | Repro / source | Status | Fix commit |
|---|---|---|---|---|---|---|
| L001 | inventory/uom | P0 | Tenant `UomController` carries **no** `@PreAuthorize`. Global authentication still applies, but the documented permission gate is absent | PROJECT.md → Known defects | OPEN | |
| L002 | inventory/purchase | P0 | Both purchase `POST /{id}/post` transitions omit the `@PreAuthorize` their adjacent transitions carry | PROJECT.md → Known defects | OPEN | |
| L003 | pos/shifts | P0 | **Blind-count disclosure.** A cashier can reconstruct expected cash from order, payment, expense, opening-count and prior-closing-count detail. Omitting named variance fields does not prevent reconstruction | SHIFT_REVIEW_FOLLOWUP finding 1 | OPEN | |
| L004 | web/auth | P0 | Frontend/backend permission mismatch: the owner shortcut in `shiftAccess.ts` is inconsistent with backend authorization. Login responses already carry direct permissions | SHIFT_REVIEW_FOLLOWUP finding 14; [UI_PERMISSIONS_PLAN.md](UI_PERMISSIONS_PLAN.md) | OPEN | |
| L005 | pos/shifts | P1 | Shifts-list cashier/device filter options require unrelated user-management or device-management permissions | SHIFT_REVIEW_FOLLOWUP finding 12 | OPEN | |
| L006 | all | P0 | No guard-reversion / mutation audit has been done. Passing focused tests do not establish that each protection fails when removed | ROADMAP → Shift/POS follow-up | OPEN | |

### Concurrency and correctness

| ID | Area | Sev | Finding | Repro / source | Status | Fix commit |
|---|---|---|---|---|---|---|
| L007 | pos/shifts | P0 | **Competing closes.** The first successful close is not immutable under concurrent requests. Needs serialization or a conditional write, with concurrency — not sequential — coverage | SHIFT_REVIEW_FOLLOWUP finding 4 | OPEN | |
| L008 | expense/shifts | P0 | **Expense/close race.** An expense can miss both the frozen close sum *and* the late-expense classification | SHIFT_REVIEW_FOLLOWUP finding 6 | OPEN | |
| L009 | inventory/purchase | P1 | A purchase return is UOM-locked in the UI (D108) but the backend still accepts and converts a different `uomId`. Another client can submit one | PROJECT.md → Known drift | OPEN | |
| L010 | inventory/purchase | P1 | Displayed line values need not sum to the displayed document total — six-decimal storage, two-decimal display. Product/accounting decision, not yet taken | DEFERRED_BACKEND_GAPS.md | OPEN | |
| L011 | order | P1 | Orders have no `cancelledAt`; the only event time is a client-generated `orderDate` from the POS | PROJECT.md → Known defects | OPEN | |

### Broken or missing surfaces

| ID | Area | Sev | Finding | Repro / source | Status | Fix commit |
|---|---|---|---|---|---|---|
| L012 | web/inventory | P1 | The admin transfers UI is routed and calls `/api/inventory/transfers`, which **does not exist**. A user-reachable screen that cannot work. Transfers are out of MVP (M2), so the MVP fix is to remove the route, not build the backend | PROJECT.md → Known defects | OPEN | |
| L013 | pos/shifts | P1 | No authorized reconciliation view: opening count, closing count, expected cash and handover variance are not shown to managers who may see them, while the restricted caller's blind surface must be preserved | SHIFT_REVIEW_FOLLOWUP finding 13 | OPEN | |
| L014 | web/i18n | P1 | Enum values (statuses, types, reason codes) have no systematic translation keys. `useTranslation` has no `defaultValue`, so a missing key **renders raw to the user**. Coverage has never been measured | ROADMAP §6 | IN PROGRESS (codex) | |

### Process

| ID | Area | Sev | Finding | Repro / source | Status | Fix commit |
|---|---|---|---|---|---|---|
| L015 | infra | P0 | **No CI in any repository.** The only automated build runs `-DskipTests`. Nothing between a text editor and production runs a test | SUITE_HEALTH.md §4 | OPEN | |
| L016 | web | P0 | `restaurant-saas-web` has **zero** test files and no test framework — the largest surface in the product, mechanically unguarded | SUITE_HEALTH.md §5 | OPEN | |
| L017 | pos | P1 | 21 Vitest files exist and are never run. The POS is offline-capable, so a regression fails on a device, in a restaurant, hours later | SUITE_HEALTH.md §5 | OPEN | |
| L018 | infra | P1 | `@SpringBootTest` runs against the **configured development database** — no Testcontainers, no test datasource | ROADMAP §5 | OPEN | |

### Deliberately out of MVP

Recorded so they are never re-found. **Do not fix during this pass.**

| ID | Area | Sev | Finding | Source |
|---|---|---|---|---|
| L019 | pos | OUT | No ESC/POS printing. "Print/reprint" opens a visual preview only | ROADMAP §1 |
| L020 | order | OUT | `IncomingOrderRequest` intake is wired but has **zero** test references | ROADMAP §1 |
| L021 | order | OUT | Automatic order → request linking is unreconciled with D24 | ROADMAP §1 |
| L022 | aggregators | OUT | No branded connectors, no manual-entry creation UI | ROADMAP §2 |
| L023 | workflow | OUT | No `ApprovalWorkflow`; `DocumentHistory` is dormant scaffolding that also violates two timestamp conventions | ROADMAP §3, PROJECT.md |
| L024 | inventory | OUT | `InventoryTransfer` is entities + enum only — no repository, service, controller or tests | PROJECT.md |
| L025 | assets | OUT | No depreciation; no profit/ROI/payback reporting | ROADMAP §7 |
| L026 | hr | OUT | No payroll engine; `SalaryService` / `SalaryAdjustmentService` have no test references | PROJECT.md |
| L027 | media | OUT | EXIF orientation ignored; product **hard delete** orphans `media_link` rows and leaks bytes; no orphan sweep | ROADMAP §8 |
| L028 | inventory/purchase | OUT | Per-line discount is backend-only and per-line tax does not exist on either side | DEFERRED_BACKEND_GAPS.md |
| L029 | order | OUT | D20 (cancellation stage → waste/consumption mapping) is recorded DECIDED but unimplemented; moved to DECISIONS → OPEN | PROJECT.md |
| L030 | web/i18n | OUT | Five unused `leaveAssign.errors.*` keys in `en` and `ar` | ROADMAP §6 |

---

## Disputed

_(Codex moves a row here with a one-line reason; Claude adjudicates against DECISIONS.md. Codex does
not close its own disputes.)_

| ID | Codex's reason | Claude's adjudication |
|---|---|---|
| — | — | — |
