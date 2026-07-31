package com.smart.restaurant_saas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the global {@code lock_timeout} (Hikari {@code connection-init-sql}) is actually live on
 * a pooled connection, not just parsed from config. A config entry that silently does nothing is
 * the exact failure the setting exists to prevent: it guards against indefinite, undetectable
 * lock waits (e.g. a REQUIRES_NEW transaction requesting a row its outer transaction holds).
 */
@SpringBootTest
class DataSourceLockTimeoutIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pooledConnectionsCarryTheGlobalLockTimeout() {
        // PostgreSQL reports the 5000ms default as "5s".
        assertThat(jdbcTemplate.queryForObject("SHOW lock_timeout", String.class))
            .isEqualTo("5s");
    }
}
