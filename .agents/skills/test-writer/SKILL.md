---
name: test-writer
description: Write tests, especially inventory ledger/batch/lifecycle behavior. Use when adding coverage — "write tests", "add coverage", "test this".
---

Test the invariants, which are DB behavior — so prefer **integration** tests over mock-heavy
unit tests.

## Stack
JUnit 5 (Jupiter) + AssertJ, Spring Boot 4.0.6 `@SpringBootTest` + `@Transactional`, run against
a real Flyway-migrated Postgres (see existing tests under `src/test/java/.../inventory/**`, e.g.
`WasteDocumentRepositoryJsonTest`, `InventoryLedgerServiceTest`). Testcontainers is **not** yet a
dependency — add `org.testcontainers:postgresql` (+ `@Testcontainers`/`PostgreSQLContainer`) if a
test needs an isolated ephemeral DB; otherwise follow the existing `@SpringBootTest` convention.

## Priority chain (inventory)
average cost (open batches only) → batch creation/ordering → FIFO consumption → document
lifecycles (Purchase Invoice / Return, Waste, Physical Count) → optimistic-locking retry.

## Invariant coverage
Every DECIDED invariant (`docs/DECISIONS.md`) needs at least one test that **fails if the
invariant breaks**, e.g.:
- signed-delta balance may go **negative** on a FIFO shortfall (D1);
- average cost ignores **CLOSED** batches / is derived from open batches only (D2);
- **Waste has no unpost/reversal** path (D7);
- Purchase Invoice **unpost guard order**: return-existence before batch-consumption (D8);
- shortfall priced at **pre-movement** average cost (D11).

Match existing naming and assertion style.
