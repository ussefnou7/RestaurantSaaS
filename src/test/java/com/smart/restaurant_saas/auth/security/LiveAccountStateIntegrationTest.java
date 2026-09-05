package com.smart.restaurant_saas.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import com.smart.restaurant_saas.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The token establishes who is asking. It never establishes what they may do, or whether they
 * still exist.
 *
 * <p>These tests hold that rule across three consequences: account status is read live, role state
 * is read live and gates entry, and the role <em>code</em> behind every {@code @PreAuthorize} gate
 * is the database's rather than a login-time snapshot.
 *
 * <p>Every token minted here stays cryptographically valid throughout — only the database changes
 * underneath it. That is exactly the situation the fix exists for: with a 24h lifetime and no
 * revocation, anything decided at login otherwise stays decided for a day.
 *
 * <p>{@code /api/expenses} is the probe because it is an ordinary authenticated endpoint behind a
 * permission gate. Nothing here is about expenses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LiveAccountStateIntegrationTest {

    private static final long BASE = 971_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private CrossTenantFixture fixture;
    private String token;

    @BeforeEach
    void setUp() {
        fixture = new CrossTenantFixture(jdbcTemplate, jwtService, BASE);
        fixture.reset(1);
        token = fixture.seedTenantWithUser(0, "LIVE", "EXPENSES_VIEW");
    }

    // ---------------------------------------------------------------- user status (C / B1)

    @Test
    void activeUserWithActiveRoleIsAdmitted() throws Exception {
        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(token)))
            .andExpect(status().isOk());
    }

    /**
     * All three non-ACTIVE states are rejected, and all three return the <em>same</em> code. A
     * client able to tell INACTIVE from LOCKED has an account-state oracle; the specific status is
     * logged instead.
     */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "LOCKED", "DELETED"})
    void nonActiveUserIsRejectedWithOneSharedCode(UserStatus status) throws Exception {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status.name(), fixture.userId(0));

        expectRejection(token, "USER_INACTIVE");
    }

    /**
     * DELETED is a soft delete: the row survives so historical attribution — an expense's
     * {@code createdBy}, a void's {@code voidedBy} — keeps pointing at a real user. The token is
     * rejected, but the record is not erased. If this fails because someone made DELETED a hard
     * delete, the attribution trail is what broke.
     */
    @Test
    void deletedUserRowSurvivesForHistoricalAttribution() throws Exception {
        jdbcTemplate.update("UPDATE users SET status = 'DELETED' WHERE id = ?", fixture.userId(0));

        expectRejection(token, "USER_INACTIVE");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE id = ?", Integer.class, fixture.userId(0)))
            .isEqualTo(1);
    }

    // ---------------------------------------------------------------- role gate (C)

    /**
     * Deactivating a role is a full lockout of everyone holding it. It is not a reduction of their
     * permissions — the grant rows are untouched, as asserted below — because permissions come
     * from a direct user→permission grant that the role does not participate in. The role is a
     * gate, not a source.
     */
    @Test
    void inactiveRoleLocksTheUserOutWithoutTouchingTheirPermissions() throws Exception {
        deactivateFixtureUsersRole();

        expectRejection(token, "ROLE_INACTIVE");

        assertThat(jdbcTemplate.queryForObject("""
            SELECT count(*) FROM user_permissions up
            JOIN permissions p ON p.id = up.permission_id
            WHERE up.user_id = ? AND p.code = 'EXPENSES_VIEW'
            """, Integer.class, fixture.userId(0)))
            .as("the role gate must not touch permission grants")
            .isEqualTo(1);
    }

    /** The user gate runs before the role gate, so a doubly-broken account reports USER_INACTIVE. */
    @Test
    void userStatusIsCheckedBeforeRoleState() throws Exception {
        jdbcTemplate.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", fixture.userId(0));
        deactivateFixtureUsersRole();

        expectRejection(token, "USER_INACTIVE");
    }

    // The matching rule at login is guarded by AuthServiceTest#loginRejectsAUserWhoseRoleHasBeen-
    // Deactivated, not here. It cannot be asserted through this fixture: its users carry the
    // literal password hash 'x', so login never succeeds for them, and a login that fails for a
    // deactivated role would be indistinguishable from one that fails for the wrong password —
    // a test that passes whether or not the gate exists.

    // ---------------------------------------------------------------- live role code (D)

    /**
     * The security-critical direction: a token <em>claiming</em> SYS_ADMIN must not grant the
     * sysadmin bypass when the database says otherwise.
     *
     * <p>Under the old behaviour {@code isSysAdmin()} read the token's {@code roleCode} claim and
     * returned true with no repository call, short-circuiting every permission gate in the
     * application. The user's real role is OWNER and they hold no EXPENSES_VIEW grant, so with a
     * live role read this is 403.
     */
    @Test
    void aTokenClaimingSysAdminDoesNotGrantTheBypass() throws Exception {
        jdbcTemplate.update("DELETE FROM user_permissions WHERE user_id = ?", fixture.userId(0));

        String forgedRoleToken = jwtService.generateAccessToken(
            fixture.userId(0), fixture.tenantId(0), "owner_live", RoleCode.SYS_ADMIN.name());

        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(forgedRoleToken)))
            .andExpect(status().isForbidden());
    }

    /**
     * The same principle applied to the other two helpers. {@code GET /api/hr/leave-requests} is
     * gated purely by {@code @securityService.isOwnerOrBranchManager()} with no permission check,
     * so it isolates the role helper rather than permission resolution.
     *
     * <p>The token claims CASHIER; the database says OWNER. Under the old snapshot behaviour the
     * helper read the claim and returned 403. Reading the database it is admitted — which is also
     * the proof that a role <em>promotion</em> takes effect without re-issuing a token, the mirror
     * of the revocation case above.
     */
    @Test
    void roleHelpersReadTheDatabaseNotTheClaim() throws Exception {
        String staleRoleToken = jwtService.generateAccessToken(
            fixture.userId(0), fixture.tenantId(0), "owner_live", RoleCode.CASHIER.name());

        mockMvc.perform(get("/api/hr/leave-requests")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(staleRoleToken)))
            .andExpect(status().isOk());
    }

    /**
     * The two login routes are permitAll and skipped by the filter entirely. They must stay
     * reachable without a token — the endpoints that establish identity cannot require one.
     */
    @Test
    void loginRouteStaysReachableWithoutAToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantCode\":\"nope\",\"username\":\"nope\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    // ---------------------------------------------------------------- helpers

    private void expectRejection(String bearerToken, String expectedCode) throws Exception {
        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(bearerToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value(expectedCode));
    }

    /**
     * {@code roles} rows are global (one OWNER for the whole installation), so this mutates a
     * shared row. Safe because the class is {@code @Transactional} and rolls back.
     */
    private void deactivateFixtureUsersRole() {
        jdbcTemplate.update(
            "UPDATE roles SET is_active = false WHERE id = (SELECT role_id FROM users WHERE id = ?)",
            fixture.userId(0));
    }

}
