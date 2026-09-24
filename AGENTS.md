# AGENTS.md — for Codex (the writer, BE + FE)

You write code for this Restaurant SaaS platform (Spring Boot backend + React frontend).

- **Follow [docs/CONVENTIONS.md](docs/CONVENTIONS.md)** for all code — package layout, the stock
  engine, exceptions, migrations, and the frontend (BEM, `--color-*`, Lucide, RTL,
  `useTranslation`).
- **Never reopen a DECIDED item in [docs/DECISIONS.md](docs/DECISIONS.md).** Those are ground
  truth. Treat OPEN items as undecided — don’t ship irreversible code on a guessed answer; ask.
- **Project state is in [docs/PROJECT.md](docs/PROJECT.md)**; planned work in
  [docs/ROADMAP.md](docs/ROADMAP.md); Orders design in [docs/modules/ORDERS.md](docs/modules/ORDERS.md).
- **Self-check against [docs/REVIEW.md](docs/REVIEW.md) before handing off.** Every hard
  invariant there must hold in your diff. Don’t imitate the known pre-existing violations it lists.
- Mirror the nearest existing sibling file. When unsure, prefer the concrete over the abstract.

**During the MVP pass** ([docs/MVP_HARDENING_PLAN.md](docs/MVP_HARDENING_PLAN.md)): take work only
from [docs/MVP_LEAK_LEDGER.md](docs/MVP_LEAK_LEDGER.md) — `OPEN` rows, `P0` first, in ID order. Mark
a row `IN PROGRESS (codex)` before you start it. One finding per commit, referencing its ID, with a
regression test in the same commit. Do not edit the `Sev`, `Repro` or `Evidence` columns, and do not
close your own `DISPUTED` rows.
