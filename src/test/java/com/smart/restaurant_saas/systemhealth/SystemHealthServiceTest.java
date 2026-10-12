package com.smart.restaurant_saas.systemhealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.systemhealth.dto.SystemHealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemHealthServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SystemHealthService service = new SystemHealthService(jdbcTemplate);

    @Test
    void reportsServerAndDatabaseSeparatelyWhenDatabaseIsUp() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        SystemHealthResponse response = service.getHealth();

        assertThat(response.server().status()).isEqualTo("UP");
        assertThat(response.database().status()).isEqualTo("UP");
        assertThat(response.checkedAt()).isNotNull();
    }

    @Test
    void keepsServerUpAndReportsDatabaseDownWhenQueryFails() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        SystemHealthResponse response = service.getHealth();

        assertThat(response.server().status()).isEqualTo("UP");
        assertThat(response.database().status()).isEqualTo("DOWN");
    }
}
