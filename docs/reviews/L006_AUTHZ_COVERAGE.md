# L006 authorization coverage follow-up

2026-10-06. Scope: the authorization gates in `scripts/audit/cases/authz.json`.

## Regression coverage

`ControllerAuthorizationContractTest` checks a fixed permission contract in
`src/test/resources/authz-gates.txt`. It covers 238 actual gates, expanded to 251
controller methods because class-level gates protect multiple endpoints. All 753
parameterized checks pass:

- Unauthorized callers are denied before the controller body executes. Every other
  permission in the contract is also tried individually and rejected.
- Each permitted alternative is allowed independently, including both shift-close
  permissions. Sysadmin bypass is checked only where the contract allows it.
  Authentication-only media gates reject anonymous callers and admit authenticated ones.
- Each guard is independently removed from an in-memory bytecode copy. The same
  previously forbidden invocation then reaches the controller mock. This proves the
  denial assertion depends on that particular guard. The mutation asserts that exactly
  one annotation was removed; production files are never rewritten.

The tests execute Spring Security's real pre-authorization advisor and expression
handler. Controller bodies and `SecurityService` are mocked to isolate the gate from
validation, tenant resolution, and business logic. Existing MVC and integration tests
remain responsible for HTTP wiring, real permission resolution, and tenant isolation.
The fixed contract is deliberately independent of live annotations: do not regenerate
it automatically when a guard changes.

Run the focused checks:

```sh
./mvnw -o -q -Dtest=ControllerAuthorizationContractTest test
```

JUnit results are in `target/surefire-reports/`. This is an in-process mutation check,
not a rerun of the historical full-suite-per-source-mutation `authz.jsonl`. Keep that
historical artifact unchanged.

Full verification: `./mvnw -o -q test` passed on 2026-10-06 with **1,832 tests,
0 failures, 0 errors, 0 skipped** across 148 test classes, including these 753 checks.
The first sandboxed attempt could not open PostgreSQL sockets; the successful run
used local database access and the repository's test datasource configuration.

## Case inventory corrections

Both `authz.json` and `authz_prioritised.json` now agree:

- Removed A168: it targeted a Javadoc mention of `@PreAuthorize`, not a guard.
  IDs are otherwise unchanged; A169-A173 still cover media's five real gates.
- Corrected eleven method guards mislabeled as class-level guards: A081, A082,
  A086, A089, A126, A128, A129, A131, A195, A203, A204.
- Added the annotation's end line to A211 so the source harness removes the entire
  wrapped shift-close annotation instead of leaving invalid Java.

All 238 source anchors were checked against the current checkout. The inventory
does not claim to cover endpoints introduced after the original audit, such as the
dashboard work currently in progress.

L006 remains open for its separate tenant-isolation and document-state-machine work.
