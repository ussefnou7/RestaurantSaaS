package com.smart.restaurant_saas.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DevicePairingIntegrationTest {

    private static final long TENANT_ID = 994_100L;
    private static final long BRANCH_ID = 994_101L;
    private static final long DEVICE_ID = 994_102L;
    private static final String PAIRING_CODE = "12345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceSecretHasher secretHasher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM device WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, timezone, created_at)
            VALUES (?, 'Pairing Tenant', 'PAIRING_TEST', 'ACTIVE', 'Africa/Cairo', CURRENT_TIMESTAMP)
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Pairing Branch', 'PAIRING_BRANCH', true, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        seedPairingCode("CURRENT_TIMESTAMP + INTERVAL '10 minutes'");
    }

    @Test
    void successfulPairingConsumesCodeSoReplayFails() throws Exception {
        pair(PAIRING_CODE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(DEVICE_ID))
            .andExpect(jsonPath("$.branchId").value(BRANCH_ID))
            .andExpect(jsonPath("$.tenantName").value("Pairing Tenant"));

        assertThat(jdbcTemplate.queryForObject("""
            SELECT pairing_code_hash IS NULL AND pairing_code_expires_at IS NULL
            FROM device WHERE id = ?
            """, Boolean.class, DEVICE_ID)).isTrue();

        pair(PAIRING_CODE)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_DEVICE_PAIRING_CODE"));
    }

    @Test
    void expiredPairingCodeIsClearedAndRejected() throws Exception {
        jdbcTemplate.update("""
            UPDATE device
            SET pairing_code_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
            WHERE id = ?
            """, DEVICE_ID);

        pair(PAIRING_CODE)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("DEVICE_PAIRING_CODE_EXPIRED"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT pairing_code_hash IS NULL FROM device WHERE id = ?",
            Boolean.class, DEVICE_ID)).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions pair(String pairingCode) throws Exception {
        return mockMvc.perform(post("/api/devices/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pairingCode\":\"" + pairingCode + "\"}"));
    }

    private void seedPairingCode(String expiryExpression) {
        jdbcTemplate.update("""
            INSERT INTO device (
                id, tenant_id, name, branch_id, pairing_code_hash,
                pairing_code_expires_at, active, created_at
            )
            VALUES (?, ?, 'Pairing POS', ?, ?, %s, true, CURRENT_TIMESTAMP)
            """.formatted(expiryExpression),
            DEVICE_ID, TENANT_ID, BRANCH_ID, secretHasher.sha256Hex(PAIRING_CODE));
    }
}
