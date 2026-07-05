---
name: security-review
description: Security review with multi-tenant focus. Use on anything touching data access, auth, RBAC, tenant scope, or queries — "security review", auth/RBAC/query changes.
allowed-tools: Read, Grep, Bash(git diff:*)
---

Multi-tenant SaaS: the top risk is cross-tenant data leakage. Read-only.

## Checks
- **TOP — tenant isolation:** every read/write of tenant-owned data MUST be scoped by
  `tenantId`. A query or repository call missing the tenant filter = **BLOCK** (cross-tenant
  leak). Grep the diff for repository methods that don't take/scope a tenant id.
- **RBAC:** endpoints enforce the correct permission (`@PreAuthorize @securityService...`);
  never trust client-supplied ids implicitly — resolve against the caller's tenant.
- **Auth/JWT:** per conventions; no token/secret leakage; no secrets or PII in logs.
- **Injection:** parameterized queries / correct JPA usage; no string-built SQL/JPQL.
- **Error surface:** no sensitive data in `errorCode` `params` or any user-facing text (the
  server `message` is logs-only and must not carry secrets/PII either).

## Output
Grouped **BLOCK / WARN / NIT** with `file:line`. Missing tenant scoping is always BLOCK.
