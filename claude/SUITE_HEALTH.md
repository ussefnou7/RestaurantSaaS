# Test Suite Health Check

> Diagnostic pass, 2026-08-30, against `main` at `63ff8e7e`. **No production code was changed.**
> Companion: `claude/AUDIT_FINDINGS_CODE.md` (finding 9 is the bug; this file is the other half).

## Headline

The suite is **red and completely unenforced**. 687 tests ran: 686 passed, one failed, none
errored or skipped. The failure is deterministic, is finding 9, and has remained on `main` for
**22 days** across **33 later local commits**; 26 of those commits reached `origin/main`.

There is **no CI in any repository**. Not misconfigured, not disabled — absent. The only
automated build that exists runs `-DskipTests`.

---

## 1. What was run, and how

```bash
git worktree add --detach /tmp/restaurant-suite-health.1sfxs1 \
  63ff8e7e20a89b0e6d3fc6492724ca46855b55d0
cd /tmp/restaurant-suite-health.1sfxs1
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/restaurant_saas_suite_health_codex_20260830" \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
./mvnw -B test
```

**Checkout state.** This was a detached, separate Git worktree at the exact local `main` commit,
`63ff8e7e20a89b0e6d3fc6492724ca46855b55d0`; `git status --short --branch` reported only
`## HEAD (no branch)`. Local `main` is seven commits ahead of `origin/main` (`8e92353`), so the
history below reports both local and pushed counts. None of the shared checkout's documentation
changes entered the run.

**The database question — and why existing databases were not used.** 31 of the 99 executed test
classes carry `@SpringBootTest` and load the JPA application context, so a live PostgreSQL is
required. Clean `main` has no tracked test datasource file: `src/test/resources` contains only
the Mockito extension, so a run without an override inherits the main default,
`jdbc:postgresql://localhost:5432/restaurant-saas` — **the development database**.

The supplied `src/test/resources/application.yml` is untracked and therefore correctly absent
from the clean worktree. It points to `restaurant-saas-test`; PostgreSQL was accepting connections
and that database was available, with 58 public tables. It was not used because it was not empty
and its contents were not known to be disposable.

Docker was checked first and is installed but **not running**, so Testcontainers-style isolation
was unavailable. Instead the empty throwaway database
`restaurant_saas_suite_health_codex_20260830` was created on the local PostgreSQL 16.15 instance.
Flyway built it from scratch (49 successful migrations, up to `V50`), the suite ran against it,
and **that exact database was dropped afterward**. A final catalog query confirmed it no longer
exists; `restaurant-saas-test` still had its original 58-table count.

**Everything in the backend suite ran.** No class was skipped, excluded, or unrunnable. All 99
test classes and 687 test methods executed.

> One caveat on the environment, not a caveat on the result: this was a **fresh** database, so
> every migration applied in order. It does not exercise the `validate-on-migrate: false` +
> out-of-order-unset behaviour that can silently skip a late-arriving lower version on an
> existing database. A green suite on a fresh schema is not evidence about the dev schema.

---

## 2. Raw result

| | |
|---|---|
| Test classes | 99 |
| Tests run | **687** |
| Passed | **686** |
| Failures | **1** |
| Errors | 0 |
| Skipped | **0** |
| Maven total time (clean compile + tests) | 50.827 s |
| Build | `BUILD FAILURE` |

### Every failure

```
[ERROR] OrderConsumptionServiceTest.recalculateRejectsNonConflictDoc:427
        Expecting code to raise a throwable.
```

One line, one test. That is the complete list.

### Disabled / ignored tests

**None.** `rg '@Disabled|@Ignore' src/test/java` returns nothing, and surefire reports
`Skipped: 0`. Nobody has been silencing tests — which makes the outcome cleaner to interpret:
the suite was not gamed, it was simply not run.

---

## 3. Classification

| Test | Failure | Cause | Class |
|---|---|---|---|
| `OrderConsumptionServiceTest.recalculateRejectsNonConflictDoc` | `Expecting code to raise a throwable` at `:427` | The status guard in `OrderConsumptionService.recalculate` is commented out (`:182-191`), so a `POSTED` doc is accepted and reprocessed instead of rejected | **`PRODUCTION_BUG`** |

**The test is right and the code is wrong.** It asserts D45/D94 — recalculate is a manual retry
for `PARTIAL` and `CONFLICT` documents only — which is still what the method's own javadoc says
three lines above the commented block, still what `InventoryErrorCode` carries a dedicated code
for (`ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT`, `:37`), and still what the endpoint's Swagger
`@Operation` description publishes. Four independent artefacts agree with the test. Only the
executable code disagrees. No assertion should be weakened here.

**Not flaky — established by three independent runs**, all identical: once inside the full suite,
once as a re-run of the whole `OrderConsumptionServiceTest` class (16 tests, same one failure),
and once as the individual method. It is a pure Mockito unit test with no clock, ordering, or
database dependency; there is no mechanism by which it could pass without a code change.

**Not environmental.** It fails identically with and without a database present, because it never
touches one.

Recorded as **finding 9 (P0)** in `claude/AUDIT_FINDINGS_CODE.md` and updated there with this
health-check confirmation. **No additional production-bug entry was produced** — the suite
surfaced exactly one defect, and finding 9 is that defect.
That is worth stating plainly: the 686 passing tests are real coverage, and this pass did not
uncover a hidden backlog behind them.

---

## 4. How this went unnoticed

### There is no CI. Anywhere.

Searched all six actual Git repositories beside this workspace for `.github/workflows/`,
`.gitlab-ci.yml`, `Jenkinsfile`, `.circleci/`, `azure-pipelines.yml`, `.travis.yml`,
`bitbucket-pipelines.yml`, `.buildkite/`, `.husky/`, and versioned hooks. The adjacent
`restaurant-saas-client-web/` directory has an empty `.git/` directory and is not a Git
repository, so it is not counted.

| Repo | CI config | Git hooks |
|---|---|---|
| `restaurant-saas` | none | none |
| `restaurant-saas-web` | none | active versioned pre-commit hook: ESLint on staged TS/TSX only |
| `restaurant-pos` | none | none |
| `restaurant-saas-panel` | none | none |
| `restaurant_saas_mobile` | none | none |
| `web-static` | none | none |

`.git/hooks/` in the backend contains **only** the stock `.sample` files — nothing active.

**The one automated build that exists skips tests.** `nixpacks.toml`:

```toml
[phases.build]
cmds = ["./mvnw -B -DskipTests package"]
```

That is the deploy build. So the only pipeline touching this code is explicitly configured not
to run the suite. Nothing, at any point between a text editor and production, executes
`mvn test`.

`restaurant-saas-web` is the only repository with an active hook. Its `prepare` script sets
`core.hooksPath=.githooks`, and `.githooks/pre-commit` runs ESLint over staged TS/TSX files. It is
useful local feedback, but it neither runs tests nor touches the backend, it is bypassable, and
it does not block a direct push. No test failure currently blocks a commit, push, merge, deploy,
or direct update to `main`.

### When it started, and what landed on top

| | |
|---|---|
| Guard + test introduced together | `e6fec59` — `feat: add order, device, loyalty, and asset modules…` |
| Guard commented out | **`2ae6132`, 2026-08-08** — `chore: commit pre-existing uncommitted work tree` |
| Later commits on local `main` | **33** |
| Of those, touching `src/main` | 23 |
| Of those, adding or changing tests | **10** |
| Later commits pushed to `origin/main` | **26** (17 touching `src/main`, 5 touching tests) |
| Red for | **22 days** (19 days to the last commit, `63ff8e7` on 2026-08-27) |

The regression's entry point is the most informative detail here. `2ae6132` is a **121-file,
8,249-insertion bulk commit** whose message is literally "commit pre-existing uncommitted work
tree." Inside it, the change that broke the suite is two lines: a `/*` inserted before the guard
and a `*/` after it. Nothing about that commit invited line-by-line review, and no mechanism
existed to catch what review would have missed.

Yes, other work landed while the suite was red: 26 later commits were pushed to `origin/main`,
and seven more sit on local `main`. Five of the pushed commits and ten of the local 33
**wrote or modified tests** while the suite was already red. Whoever wrote them ran their own
tests, or none — but not the suite. That is the failure mode worth naming: it is not that a test
was ignored, it is that "the suite" was never a thing anyone executed as a unit.

### Why this is sharper here than in a hand-written codebase

The prompt's framing holds up and the numbers support it. The clean compile plus all 687 tests
took **50.827 seconds**, with zero flakes and zero disabled tests — this is a fast, trustworthy
suite. It is the primary evidence that agent-written code works, because agent-written code reads
plausibly whether or not it does. An unexecuted suite converts that evidence into decoration. The
cost of running it is under a
minute locally; the cost of not running it was a silent 22-day regression in the stock-deduction
path.

---

## 5. Recommendation

**Add GitHub Actions running `./mvnw -B test` on push and pull request to `main`, with a
Postgres service container. Then turn on branch protection requiring that check.**

One recommendation, not a menu — but the reasoning for the rejected alternatives is short and
worth keeping, because the obvious cheap option is the wrong one here.

### Why not a pre-push hook

It looks cheaper and it is not enforcement. A hook can be versioned, as the admin frontend's
working `.githooks/pre-commit` demonstrates, but installation still depends on local Git config,
fresh worktrees and clones can miss it, and `--no-verify` bypasses it. A hook is a useful reminder,
not the mechanism that makes a red suite impossible to merge.

### Why CI, sized for one developer

```yaml
# .github/workflows/test.yml
name: test
on:
  push: { branches: [main] }
  pull_request:
jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_PASSWORD: postgres, POSTGRES_DB: restaurant_saas_test }
        options: >-
          --health-cmd pg_isready --health-interval 10s
          --health-timeout 5s --health-retries 5
        ports: ['5432:5432']
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { java-version: '21', distribution: temurin, cache: maven }
      - run: ./mvnw -B test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/restaurant_saas_test
          SPRING_DATASOURCE_USERNAME: postgres
          SPRING_DATASOURCE_PASSWORD: postgres
```

**Setup cost: one file, ~30 lines, about 20 minutes including the first red-then-green cycle.**
Runtime should be about two minutes per push (under a minute of local compile/tests plus runner
startup and dependency setup, cached after the first run).

Then, once one run has passed: **Settings → Branches → protect `main` → require the `backend`
status check.** That is the half that actually matters. CI that reports red without blocking is
a notification, and this project's evidence is that notifications get missed for three weeks.

**Fix finding 9 first** — CI added today would go red immediately, and a pipeline whose first
and permanent state is failing teaches everyone to ignore it. Order: restore the guard, confirm
687/687, then add the workflow, then protect the branch.

### The frontend repos

They are separate repositories, so each needs its own small required workflow on its default
branch; no backend workflow can enforce them. Their current test surfaces are:

| Repo | Tests | Action |
|---|---|---|
| `restaurant-pos` | **21 Vitest files**; `npm test` runs `vitest run` | Require `npm ci && npm test && npm run build`; the offline outbox and submitted order payload are covered but currently unenforced |
| `restaurant-saas-web` | **0 test files**, no test framework; active pre-commit lint hook only | Require `npm ci && npm run build` (`build` already runs lint, TypeScript, and Vite) |
| `restaurant-saas-panel` | **0 test files**, no test framework | Require `npm ci && npm run lint && npm run build` |
| `restaurant_saas_mobile` | **6 normal Flutter test files** plus **2 credential-gated integration files** | Require `flutter analyze && flutter test test`; keep the live-backend integration pair explicit because they skip without runtime credentials |
| `web-static` | **0 test files**; static `serve` wrapper only | No test job is justified; deployment can remain a packaging/startup check |

This is the same enforcement recommendation repeated at repository boundaries: a required
`verify` status check, not local hooks. The backend is the first 20-minute change because it is
already red and is the prerequisite for the tenant-isolation harness. Adding the four active
frontend checks is roughly another hour of setup, mostly copying the workflow and selecting the
existing command. `restaurant-pos` having 21 unrun test files is the second-order version of the
same finding, and it is the repo where a silent regression is least visible: the POS is
offline-capable, so a
broken outbox fails on a device, in a restaurant, hours later.

---

## 6. What could not be run

Nothing in the backend suite. All 99 classes and 687 tests executed.

Stated for completeness, these were **out of scope and not run**, and no claim is made about them:

- `restaurant-pos`'s 21 vitest files — not executed in this pass.
- `restaurant_saas_mobile`'s six normal test files and two credential-gated integration files —
  not executed in this pass.
- Any end-to-end or manual verification path. `docs/CONVENTIONS.md` → Verification (D109) requires
  rendered measurement for layout and interaction changes; nothing in this pass addresses that,
  and a green backend suite is not evidence about the frontend.
- The dev database's own schema state, per the migration caveat in §1.
