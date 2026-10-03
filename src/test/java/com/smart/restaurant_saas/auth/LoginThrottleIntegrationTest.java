package com.smart.restaurant_saas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.rbac.enums.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LoginThrottleIntegrationTest {

    private static final long TENANT_ID = 978_000L;
    private static final long USER_ID = TENANT_ID + 100;
    private static final String TENANT_CODE = "login_throttle_test";
    private static final String USERNAME = "throttle_owner";
    private static final String PASSWORD = "Strongpass1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanUp();
        Long ownerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL",
                Long.class,
                RoleCode.OWNER.name());
        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, code, status, created_at, timezone)
                VALUES (?, 'Login Throttle Test', ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
                """, TENANT_ID, TENANT_CODE);
        jdbcTemplate.update("""
                INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                                   created_at, role_id)
                VALUES (?, ?, 'Throttle Owner', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, ?)
                """, USER_ID, TENANT_ID, USERNAME, passwordEncoder.encode(PASSWORD), ownerRoleId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM refresh_token WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM user_permissions WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
    }

    @Test
    void fifthFailurePersistsLockAndSuccessfulLoginAfterExpiryResetsIt() throws Exception {
        for (int attempt = 1; attempt < 5; attempt++) {
            login("wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, USER_ID))
                    .isEqualTo(attempt);
        }

        login("wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_TEMPORARILY_LOCKED"))
                .andExpect(jsonPath("$.params.retryAfterSeconds").value(300))
                .andExpect(jsonPath("$.params.lockedUntil").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, USER_ID))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_until IS NOT NULL FROM users WHERE id = ?", Boolean.class, USER_ID))
                .isTrue();

        login(PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_TEMPORARILY_LOCKED"));

        jdbcTemplate.update(
                "UPDATE users SET locked_until = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?",
                USER_ID);

        login(PASSWORD).andExpect(status().isOk());

        var state = jdbcTemplate.queryForMap("""
                SELECT failed_login_attempts, last_failed_login_at, locked_until
                FROM users
                WHERE id = ?
                """, USER_ID);
        assertThat(state.get("failed_login_attempts")).isEqualTo(0);
        assertThat(state.get("last_failed_login_at")).isNull();
        assertThat(state.get("locked_until")).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantCode":"%s","username":"%s","password":"%s"}
                        """.formatted(TENANT_CODE, USERNAME, password)));
    }
}
