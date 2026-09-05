package com.smart.restaurant_saas.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.JwtService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The token establishes who is asking. It never establishes whether they still exist.
 *
 * <p>These tests hold the rule that account state is read live on every request rather than
 * trusted from the claims. The token minted here stays cryptographically valid throughout — only
 * the database row changes — which is exactly the situation the fix exists for: with a 24h token
 * and no revocation, a snapshot taken at login lets a dismissed employee keep access for the rest
 * of the day.
 *
 * <p>{@code /api/expenses} is used as the probe because it is an ordinary authenticated endpoint
 * with a permission gate; nothing here is about expenses specifically.
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

    @Test
    void activeUserIsAdmitted() throws Exception {
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
        setStatus(status);

        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("USER_INACTIVE"));
    }

    /**
     * DELETED is a soft delete: the row survives so that historical attribution — an expense's
     * {@code createdBy}, a void's {@code voidedBy} — keeps pointing at a real user. The token is
     * rejected, but the record is not erased. If this assertion ever fails because someone made
     * DELETED a hard delete, the attribution trail is what broke.
     */
    @Test
    void deletedUserRowSurvivesForHistoricalAttribution() throws Exception {
        setStatus(UserStatus.DELETED);

        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(token)))
            .andExpect(status().isUnauthorized());

        Integer rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE id = ?", Integer.class, fixture.userId(0));
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
    }

    /**
     * The two login routes are {@code permitAll} and are skipped by the filter entirely. They must
     * stay reachable without a token — the endpoints that establish identity cannot be allowed to
     * require one.
     */
    @Test
    void loginRouteStaysReachableWithoutAToken() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"tenantCode\":\"nope\",\"username\":\"nope\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    private void setStatus(UserStatus status) {
        jdbcTemplate.update(
            "UPDATE users SET status = ? WHERE id = ?", status.name(), fixture.userId(0));
    }
}
