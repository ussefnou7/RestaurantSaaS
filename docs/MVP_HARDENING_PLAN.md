# MVP hardening plan — Claude × Codex

> Written 2026-09-24. Purpose: drive the platform to a **first shippable MVP** by running the real
> stack end to end, recording every leak in one ledger, and closing the P0s.
>
> This file is the **plan**. Findings do not live here — they live in
> [MVP_LEAK_LEDGER.md](MVP_LEAK_LEDGER.md), which is the single shared artefact both agents write to.
>
> Roles are unchanged from [AGENTS.md](../AGENTS.md) / [CLAUDE.md](../CLAUDE.md):
> **Codex writes, Claude reviews.** This plan adds a third activity — *running the thing* — and
> assigns it to Claude, because the finder must not be the fixer.

## Decisions that scope this plan

Taken 2026-09-24 with the product owner. They are settled; do not reopen them mid-pass.

| # | Decision |
|---|---|
| M1 | **MVP = the cash-to-stock core.** POS order → shift close → inventory consumption → purchase/waste/count → expenses → sales & inventory reports, plus the auth/RBAC/tenant/branch/device/menu foundation those need. |
| M2 | **Out of MVP:** HR, Fixed Assets, aggregator connectors, approval workflows, inventory transfers, ESC/POS printing, media beyond the product image already built, loyalty beyond customer-by-phone. Out-of-scope modules are **not tested and not fixed** — a defect found in one is filed `OUT` and left. |
| M3 | **Manual E2E first, CI second.** Find real leaks against a running stack before investing in test scaffolding. The automated gate is built in Phase 5 around what actually broke, not speculatively. |
| M4 | **Freeze a baseline before testing.** All three working trees are committed and green before the first scenario runs, so every finding cites a commit, not a mood. |

## Why this plan exists at all

Three facts from [SUITE_HEALTH.md](../claude/SUITE_HEALTH.md) (2026-08-30) and a 2026-09-24 re-check:

- **There is no CI in any repository.** The only automated build runs `-DskipTests`.
- **`restaurant-saas-web` has zero tests and no test framework.** It is the largest surface in the
  product and nothing mechanical guards it.
- **`restaurant-pos` has 21 Vitest files that nobody runs.** It is offline-capable, so a regression
  there fails on a device, in a restaurant, hours later.

Consequently the code has never been *exercised as a system*. Module-level confidence is high and
system-level confidence is unmeasured. That gap is what this plan closes.

---

## Phase 0 — Freeze the baseline (blocking)

Nothing else starts until this is done. Today every repo has a large uncommitted tree
(backend 129 files, admin-web 104, POS 37); a bug found against an uncommitted tree is not
reproducible tomorrow.

| Step | Owner | Done when |
|---|---|---|
| 0.1 Review each working tree against [REVIEW.md](REVIEW.md); split it into coherent commits | Codex proposes the split, Claude reviews the diff | Three repos at `git status --short` = empty |
| 0.2 Backend suite on a **throwaway** database | Claude | `./mvnw -B test` → `Failures: 0, Errors: 0`. Record the test count |
| 0.3 POS suite | Claude | `npm ci && npm test` → green. Record the file/test count |
| 0.4 Admin-web build (runs lint + `tsc -b` + Vite) | Claude | `npm ci && npm run build` → clean |
| 0.5 Flyway state | Claude | `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;` — record the top version |

> 0.2 must use an **empty throwaway database**, never `restaurant-saas` (the dev DB) and never
> `restaurant-saas-test` (contents not known to be disposable). Create it, let Flyway build it,
> drop it after. See SUITE_HEALTH.md §1 for the exact invocation.

Write the five results into the **Baseline** block at the top of the ledger. Any Phase 0 step that
is not green is itself a P0 finding — file it and fix it before Phase 1.

## Phase 1 — Static leak sweep (before the stack is even up)

Cheap, mechanical, and it finds the class of bug an E2E pass finds slowly and expensively: a screen
that calls a route nobody implemented. Claude runs all of it and files findings; Codex fixes nothing
yet.

| Sweep | Method | Known instance it should re-find |
|---|---|---|
| 1.1 **Route contract drift** | Extract every path from `restaurant-saas-web/src/api/*.ts` and the POS API client; diff against every `@RequestMapping`/`@GetMapping`/… in the backend | The admin transfers UI calls `/api/inventory/transfers`, which **does not exist** (PROJECT.md → Known defects) |
| 1.2 **Permission drift** | For each MVP controller method, compare its `@PreAuthorize` against the permission the UI gates the same action on | Tenant `UomController` has **no** `@PreAuthorize`; both purchase `POST /{id}/post` transitions omit theirs. Owner shortcut in `shiftAccess.ts` diverges from backend authorization |
| 1.3 **i18n dead ends** | Collect every `t('…')` key in admin-web, diff against `src/i18n/locales/{en,ar}/`. `useTranslation` has **no** `defaultValue` — a missing key renders raw to the user | Enum values with no key (ROADMAP §6); `leaveAssign.errors.*` dead keys |
| 1.4 **Error-code coverage** | Every `ErrorCode` an MVP path can throw must have an `en` **and** `ar` translation reachable by `translateApiError` | Auth codes were only recently added; assume gaps elsewhere |
| 1.5 **Tenant-isolation read** | Every MVP repository query and every `@Query` filters on `tenant_id`, or the entity is documented global | See [TENANT_ISOLATION_AUDIT.md](../claude/TENANT_ISOLATION_AUDIT.md) for the method |
| 1.6 **Scaffolding masquerading as features** | Anything routed in the UI whose service has no backend, or any entity with no repository/service | `InventoryTransfer`, `DocumentHistory` |

Sweeps 1.1–1.4 are scriptable. Put the scripts in `restaurant-saas-web/scripts/` if they are worth
keeping (there is precedent: `sync-permissions.mjs`); otherwise run them from the scratchpad and
paste the output into the evidence folder.

## Phase 2 — Manual E2E on the real stack

The main event. Claude drives; every scenario ends with evidence on disk.

### Stack

| Piece | Command | Where |
|---|---|---|
| Postgres | already running | `localhost:5432` |
| Backend | `./mvnw spring-boot:run` | `localhost:2020`, Swagger at `/swagger-ui.html` |
| Admin web | `npm run dev` | `localhost:5188` (`.claude/launch.json` → `web`) |
| POS | `npm run dev -- --port 5173` | `localhost:5173` (`.claude/launch.json` → `pos-dev`) |

Run against a **dedicated MVP database**, seeded from empty by Flyway. Not the dev database — the
scenarios deliberately create bad states, and §S12 needs a second tenant.

Evidence goes to `restaurant-saas-web/docs/evidence/mvp-e2e/<scenario-id>/`: screenshots for UI
claims, request/response captures for API claims, SQL output for persistence claims.

### Scenarios, in dependency order

Each one builds state the next one needs. **Do not reorder.** For S1, S5 and S7 the existing
[MANUAL_TEST_PLAN_D112_D128](MANUAL_TEST_PLAN_D112_D128.md) already has case-level tables — run
those sections rather than duplicating them here.

| ID | Scenario | Covers | Reference |
|---|---|---|---|
| **S1** | Tenant, auth, RBAC, device binding | Login with/without `deviceId`, live user/role/device revocation, refresh rotation, logout | Manual plan §1 |
| **S2** | Master data bootstrap | Branch (+timezone), warehouse, two devices, four users with distinct roles, UOMs, material categories, materials, supplier | — |
| **S3** | Menu and recipes | Menu categories, products, parent/variant products, add-ons, an immutable recipe version + its history read | — |
| **S4** | Stock in | Purchase invoice DRAFT→COMPLETE→POSTED, unpost, re-post; purchase return depleting a source batch; batch/FIFO state and derived average cost after each | — |
| **S5** | POS session | Device login, open shift, dine-in + takeaway + delivery orders, variants and add-ons, cancellation with stage/reason/note, **offline outbox** (kill the network mid-order), token expiry mid-session, recovery | Manual plan §3 |
| **S6** | Consumption pipeline | `COMPLETE` order → `OrderConsumption` PENDING → batching scheduler (50 lines **or** 8h, polled 60s) → `CONSUMPTION_SUMMARY` ledger → `stock_balance`. Then the `PARTIAL`/`CONFLICT` paths and `recalculate`. Availability = posted balance − outstanding | — |
| **S7** | Shift close | Counts, frozen close totals, the **blind** cashier surface, expected cash, handover variance, force close, and two competing closes | Manual plan §§2, 6, 7, 8 |
| **S8** | Expenses | Create against an open shift and a closed one, the branch/date-window picker, void with mandatory reason, global vs tenant categories, split permissions, the **expense/close race** | Manual plan §§4, 5 |
| **S9** | Waste and physical count | Waste document lifecycle; count freeze → reconcile, surplus opening a batch at current average, shortfall valued at current average | — |
| **S10** | Reports | Sales over time / hour / product / payment method; low stock, valuation, shrinkage, waste analysis, purchase-price drift, loss comparison. Cross-check at least two against raw SQL | — |
| **S11** | Arabic + RTL pass | Re-walk S4, S5, S7 in `ar`. Mirroring, the pinned actions column in both directions, number/date formatting, no raw keys, no clipped text | CONVENTIONS → Layout (D109) |
| **S12** | Tenant isolation | Second tenant with its own branch/menu/stock. Attempt cross-tenant reads by id on every MVP endpoint. Confirm global reference data (UOMs, catalog, material categories) is shared read-only and **not writable** from a tenant | — |

### Rules while running

1. **One finding = one ledger row, filed immediately.** Do not batch findings until the end of a
   scenario; you will lose the repro.
2. **Reproduce before filing.** A row must carry steps that make it happen a second time.
3. **Do not fix anything.** Claude finds, Codex fixes. A one-line fix applied mid-scenario
   invalidates the run and hides the fact that the leak ever existed.
4. **A blocked scenario is a P0.** If S6 cannot be reached because S4 cannot post an invoice, that
   is the most important fact of the day — file it and move to a scenario that does not depend on it.
5. **Failure ≠ change the decision.** A case that fails a DECIDED invariant is a code bug
   (CLAUDE.md). Never edit DECISIONS.md to match observed behaviour.

## Phase 3 — Triage

Claude sorts the ledger. Severity is defined against MVP, not against craftsmanship:

| Severity | Definition |
|---|---|
| **P0 — blocks MVP** | Money or stock is wrong; data loss; cross-tenant leak; auth/permission bypass; a core cash-to-stock path cannot be completed at all; raw error text or a raw i18n key on a path a cashier hits |
| **P1 — ship-blocking polish** | Path completes but is wrong in a recoverable way: bad label, missing filter, confusing error, layout broken in RTL, a report disagreeing with SQL in a way that does not affect stored data |
| **P2 — after MVP** | Real defect, no user impact before launch |
| **OUT** | In a module M2 excludes. Recorded so it is never re-found, never fixed now |

The triaged P0 list is the **MVP definition of done**. Product owner confirms it before Phase 4 —
that is the one approval gate in this plan.

## Phase 4 — Fix loop

Standard roles, tightened for this pass.

| Rule | Why |
|---|---|
| **One finding per commit**, message references the ledger ID | A bad fix reverts without taking four others with it |
| **Every P0 gets a regression test in the same commit** — backend JUnit, POS Vitest, or the first admin-web test | Otherwise Phase 5's gate protects nothing that actually broke |
| **Write the failing test first** where the defect is unit-testable | Proves the test can fail; see finding 9, where a correct test sat red for 22 days |
| **Claude re-runs the original scenario**, not just the new test | A green unit test is not evidence the screen works |
| Claude reviews against [REVIEW.md](REVIEW.md), grouped **block / warn / nit**, citing `file:line` | Existing convention |
| Ledger row moves to `FIXED` only after Claude's re-run passes | `FIXED` must mean verified, not attempted |

Concurrency findings (competing closes, the expense/close race) need **concurrency evidence** —
parallel requests, not sequential ones. Sequential rejection tests do not establish serialization,
and SHIFT_REVIEW_FOLLOWUP.md already calls this out.

## Phase 5 — The automated gate

Only after the P0 list is closed. Built around what broke, not speculatively.

| Repo | Required check | Notes |
|---|---|---|
| `restaurant-saas` | `./mvnw -B test` with a Postgres service container | The workflow is already written out in SUITE_HEALTH.md §5 — copy it |
| `restaurant-pos` | `npm ci && npm test && npm run build` | Turns 21 already-written, never-run test files into enforcement |
| `restaurant-saas-web` | `npm ci && npm run build` (build already runs lint + `tsc -b`) | Plus whatever tests Phase 4 created — this repo's first ever |

Then **branch protection requiring the check on each default branch**. CI that reports red without
blocking is a notification, and this project's own evidence is that notifications get missed for
three weeks. Pre-push hooks are explicitly rejected: bypassable with `--no-verify`, absent in fresh
clones.

Also in Phase 5, once the suite is enforced: an **isolated test datasource** (Testcontainers) so
`@SpringBootTest` stops running against the configured development database (ROADMAP §5).

## Phase 6 — MVP exit criteria

Ship when **all** of these hold. No partial credit.

- [ ] Every P0 in the ledger is `FIXED` and re-verified by its original scenario
- [ ] S1–S12 each have a complete evidence folder
- [ ] S12 passes with zero cross-tenant reads and zero tenant writes to global reference data
- [ ] A cashier can complete a full day unaided: open shift → sell (all three order types, online and offline) → link an expense → close with a variance the manager can review
- [ ] Reports agree with raw SQL on at least two independently checked figures
- [ ] The Arabic pass has no raw i18n keys and no broken RTL layout on any MVP screen
- [ ] All three repos have a required, currently-green CI check with branch protection on
- [ ] ROADMAP.md and PROJECT.md updated to match what was actually found — including anything this
      pass proved was *not* built

---

## Division of labour

| | Claude (reviewer / operator) | Codex (writer) |
|---|---|---|
| Phase 0 | Runs the three suites, records the baseline, reviews the commit split | Splits and commits the working trees |
| Phase 1 | Runs all six sweeps, files findings | — |
| Phase 2 | Drives the stack, runs S1–S12, files findings, captures evidence | — |
| Phase 3 | Triages and severities | — |
| Phase 4 | Reviews each fix, re-runs the originating scenario, flips ledger status | Fixes, one finding per commit, with the regression test |
| Phase 5 | Reviews the workflows, confirms the first red→green cycle | Writes the three workflows + the Testcontainers datasource |
| Phase 6 | Verifies the checklist | Updates ROADMAP/PROJECT to match findings |

**Why Claude runs the tests:** the finder must not be the fixer. An agent that both writes the code
and decides whether it works will report that it works — that is precisely how a commented-out guard
survived 22 days and 33 commits behind a passing-looking suite.

## Handoff protocol

- The ledger is the **only** channel for findings. Not chat, not commit messages, not a summary.
- Codex picks up rows with status `OPEN`, severity `P0`, in ID order; sets them to `IN PROGRESS`
  with its name before starting. Nothing else prevents both agents touching the same file.
- Codex never edits the `Severity`, `Repro` or `Evidence` columns. Claude never edits `Fix commit`.
- A row Codex believes is not a defect goes to `DISPUTED` with a one-line reason; Claude adjudicates
  against DECISIONS.md. **Codex does not close its own disputes.**
- Long-form fix instructions, if a row needs them, go in `restaurant-saas-web/docs/reviews/` under
  the existing `PROMPT_*.md` convention and are linked from the row.
