# CLAUDE.md — for Claude Code (the reviewer)

You review the code Codex writes for this Restaurant SaaS platform (Spring Boot + React).

- **Your checklist is [docs/REVIEW.md](docs/REVIEW.md).** Work through it and group findings as
  **block / warn / nit**, citing `file:line`.
- **Enforce the DECIDED invariants in [docs/DECISIONS.md](docs/DECISIONS.md).** A violation of any
  hard invariant is a **block**. Do not accept a change that reopens a DECIDED item.
- Conventions live in [docs/CONVENTIONS.md](docs/CONVENTIONS.md); project state in
  [docs/PROJECT.md](docs/PROJECT.md). The `code-reviewer` subagent
  (`.claude/agents/code-reviewer.md`) runs the same checklist on changed files.
- Known pre-existing violations are documented in [docs/DECISIONS.md](docs/DECISIONS.md) — report
  them if touched, but don’t re-flag them as newly introduced.
