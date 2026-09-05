package com.smart.restaurant_saas.tenant.support;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds two independent tenants, each with a real user, role and permission grants, and mints a
 * genuine signed JWT for each. Built for the cross-tenant isolation tests and intended for reuse
 * by the wider endpoint matrix in the resumed tenant isolation audit.
 *
 * <p>The tokens are produced by the application's own {@link JwtService}, not hand-built, so a
 * test using this fixture exercises the same parsing and principal construction a real request
 * does. That matters for isolation tests specifically: a hand-made principal could carry a shape
 * the real login path never produces, and would prove nothing about the real path.
 *
 * <p>All ids are allocated from a caller-supplied base so that concurrently-running test classes
 * do not collide in the shared test database.
 */
public class CrossTenantFixture {

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final long base;

    public CrossTenantFixture(JdbcTemplate jdbcTemplate, JwtService jwtService, long base) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.base = base;
    }

    public long tenantId(int index) {
        return base + index;
    }

    public long userId(int index) {
        return base + 100 + index;
    }

    /**
     * Roles are global, not per-tenant: {@code roles.code} carries a plain {@code UNIQUE (code)}
     * constraint, so OWNER exists exactly once for the whole installation and is shared by every
     * tenant. The fixture therefore references the existing row rather than creating one per
     * tenant. (The table also has a {@code tenant_id} column, which for OWNER is NULL — a
     * per-tenant column under a global unique key is worth a look in the isolation audit.)
     */
    private long ownerRoleId() {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL",
            Long.class, RoleCode.OWNER.name());
    }

    /** Removes everything this fixture creates, children first. Safe to call before seeding. */
    public void reset(int tenantCount) {
        for (int i = 0; i < tenantCount; i++) {
            jdbcTemplate.update("DELETE FROM refresh_token WHERE tenant_id = ?", tenantId(i));
            jdbcTemplate.update("DELETE FROM device WHERE tenant_id = ?", tenantId(i));
            jdbcTemplate.update("DELETE FROM user_permissions WHERE tenant_id = ?", tenantId(i));
            jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenantId(i));
            jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", tenantId(i));
        }
    }

    /**
     * Creates tenant {@code index} with an OWNER user holding {@code permissionCodes}, and returns
     * a signed access token for that user.
     */
    public String seedTenantWithUser(int index, String label, String... permissionCodes) {
        long tenantId = tenantId(index);
        long userId = userId(index);
        long roleId = ownerRoleId();

        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, tenantId, "Cross Tenant " + label, "XT_" + label);

        jdbcTemplate.update("""
            INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                               created_at, role_id)
            VALUES (?, ?, ?, ?, 'x', 'ACTIVE', CURRENT_TIMESTAMP, ?)
            """, userId, tenantId, "Owner " + label, "owner_" + label.toLowerCase(), roleId);

        for (String code : permissionCodes) {
            grantPermission(tenantId, userId, code);
        }

        return jwtService.generateAccessToken(
            userId, tenantId, "owner_" + label.toLowerCase(), RoleCode.OWNER.name());
    }

    private void grantPermission(long tenantId, long userId, String permissionCode) {
        Long permissionId = jdbcTemplate.queryForObject(
            "SELECT id FROM permissions WHERE code = ?", Long.class, permissionCode);
        jdbcTemplate.update("""
            INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, tenantId, userId, permissionId);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
