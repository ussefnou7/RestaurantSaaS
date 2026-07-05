---
name: flyway-migration
description: Author a DB migration. Use whenever a change touches the schema — "migration", "schema change", "add column/table/index".
---

Follow the repo's Flyway conventions (see `docs/CONVENTIONS.md` → Migrations).

## Rules
- Create a **NEW** file in `src/main/resources/db/migration/`, named
  `V<n>__snake_case_description.sql` — append the **next integer** above the current max
  (latest is `V15`; gaps are tolerated, never reuse or insert between). **Never edit an
  already-applied migration.**
- Idempotent-friendly DDL: `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`,
  `CREATE INDEX IF NOT EXISTS`. Naming: `uk_` / `idx_` / `chk_`; CHECK constraints mirror enums;
  money/qty `NUMERIC(18,6)`, percent `NUMERIC(10,4)`.
- **FK constraints ordered/deferred** per the squash convention (FKs last).
- **Dev**: clean+migrate is fine. **Prod**: additive only — no destructive change without an
  explicit, separate, reviewed step.
- Keep the JPA entity mapping and the migration **in sync in the same change** (`ddl-auto:
  validate` — every column must exist via migration or startup fails).
- Add a comment stating which invariant/feature the migration supports.
