package com.smart.restaurant_saas.auth.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.device.DeviceSecretHasher;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenIntegrationTest {

    private static final long BASE = 972_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private DeviceSecretHasher secretHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private CrossTenantFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CrossTenantFixture(jdbcTemplate, jwtService, BASE);
        fixture.reset(1);
        fixture.seedTenantWithUser(0, "REFRESH");
    }

    @Test
    void refreshSucceedsForAHealthyAccountAndRotatesTheStoredCredential() throws Exception {
        String original = issueRefreshToken();

        JsonNode response = refresh(original, 200);
        String replacement = response.get("refreshToken").asText();
        CurrentUserPrincipal accessPrincipal = jwtService.parseToken(response.get("accessToken").asText());
        entityManager.flush();

        assertThat(replacement).isNotEqualTo(original);
        assertThat(accessPrincipal.userId()).isEqualTo(fixture.userId(0));
        assertThat(accessPrincipal.roleCode()).isEqualTo(RoleCode.OWNER.name());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NOT NULL FROM refresh_token WHERE token_hash = ?",
            Boolean.class, secretHasher.sha256Hex(original))).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM refresh_token WHERE token_hash = ? AND revoked_at IS NULL",
            Integer.class, secretHasher.sha256Hex(replacement))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM refresh_token WHERE token_hash = ?",
            Integer.class, original)).as("the plaintext refresh token must never be stored").isZero();
    }

    @Test
    void issuedRefreshTokensHaveTheConfiguredSevenDayLifetime() {
        String token = issueRefreshToken();
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject("""
            SELECT expires_at > created_at + INTERVAL '6 days'
               AND expires_at < created_at + INTERVAL '8 days'
            FROM refresh_token
            WHERE token_hash = ?
            """, Boolean.class, secretHasher.sha256Hex(token))).isTrue();
    }

    @Test
    void anExpiredRefreshTokenIsRejectedWithTheRoutineExpiryCode() throws Exception {
        String token = issueRefreshToken();
        entityManager.flush();
        jdbcTemplate.update(
            "UPDATE refresh_token SET expires_at = created_at - INTERVAL '1 second' WHERE token_hash = ?",
            secretHasher.sha256Hex(token));
        entityManager.clear();

        refresh(token, 401, "TOKEN_EXPIRED");
    }

    @Test
    void aRefreshTokenCannotBeReusedAfterRotation() throws Exception {
        String original = issueRefreshToken();

        refresh(original, 200);

        refresh(original, 401, "TOKEN_INVALID");
    }

    @Test
    void refreshIsRejectedWhenTheUserWasDeactivatedAfterLogin() throws Exception {
        String token = issueRefreshToken();
        jdbcTemplate.update("UPDATE users SET status = 'INACTIVE' WHERE id = ?", fixture.userId(0));

        refresh(token, 401, "USER_INACTIVE");
    }

    @Test
    void refreshIsRejectedWhenTheUsersRoleWasDeactivatedAfterLogin() throws Exception {
        String token = issueRefreshToken();
        jdbcTemplate.update(
            "UPDATE roles SET is_active = false WHERE id = (SELECT role_id FROM users WHERE id = ?)",
            fixture.userId(0));

        refresh(token, 401, "ROLE_INACTIVE");
    }

    @Test
    void refreshedAccessTokenUsesTheCurrentDatabaseRoleCode() throws Exception {
        String token = issueRefreshToken();
        Long cashierRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = 'CASHIER'", Long.class);
        jdbcTemplate.update("UPDATE users SET role_id = ? WHERE id = ?", cashierRoleId, fixture.userId(0));

        JsonNode response = refresh(token, 200);

        assertThat(jwtService.parseToken(response.get("accessToken").asText()).roleCode())
            .isEqualTo(RoleCode.CASHIER.name());
    }

    @Test
    void refreshRechecksADeviceThatWasDeactivatedAfterLogin() throws Exception {
        long deviceId = seedDevice();
        String token = refreshTokenService.issue(fixture.userId(0), fixture.tenantId(0), deviceId);
        jdbcTemplate.update("UPDATE device SET active = false WHERE id = ?", deviceId);

        refresh(token, 401, "DEVICE_INACTIVE");
    }

    @Test
    void refreshPreservesAHealthySignedDeviceIdentity() throws Exception {
        long deviceId = seedDevice();
        String token = refreshTokenService.issue(fixture.userId(0), fixture.tenantId(0), deviceId);

        JsonNode response = refresh(token, 200);

        assertThat(jwtService.parseToken(response.get("accessToken").asText()).deviceId())
            .isEqualTo(deviceId);
    }

    @Test
    void logoutInvalidatesTheRefreshToken() throws Exception {
        String token = issueRefreshToken();

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token)))
            .andExpect(status().isNoContent());

        refresh(token, 401, "TOKEN_INVALID");
    }

    private String issueRefreshToken() {
        return refreshTokenService.issue(fixture.userId(0), fixture.tenantId(0), null);
    }

    private JsonNode refresh(String token, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token)))
            .andExpect(status().is(expectedStatus))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void refresh(String token, int expectedStatus, String expectedErrorCode) throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token)))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.errorCode").value(expectedErrorCode));
    }

    private String body(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private long seedDevice() {
        long branchId = BASE + 500;
        long deviceId = BASE + 600;
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Refresh Branch', 'REFRESH_BRANCH', true, CURRENT_TIMESTAMP)
            """, branchId, fixture.tenantId(0));
        jdbcTemplate.update("""
            INSERT INTO device (id, tenant_id, name, branch_id, secret_key_hash, active, created_at)
            VALUES (?, ?, 'Refresh POS', ?, ?, true, CURRENT_TIMESTAMP)
            """, deviceId, fixture.tenantId(0), branchId, "refresh-device-hash-" + BASE);
        return deviceId;
    }
}
