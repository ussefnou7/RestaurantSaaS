package com.smart.restaurant_saas.systemhealth;

import com.smart.restaurant_saas.systemhealth.dto.SystemHealthResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;

    public SystemHealthResponse getHealth() {
        long databaseStartedAt = System.nanoTime();
        String databaseStatus;
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            databaseStatus = Integer.valueOf(1).equals(result) ? "UP" : "DOWN";
        } catch (DataAccessException exception) {
            databaseStatus = "DOWN";
        }
        long databaseResponseTimeMs = elapsedMillis(databaseStartedAt);
        return new SystemHealthResponse(
                new SystemHealthResponse.HealthCheck("UP", 0),
                new SystemHealthResponse.HealthCheck(databaseStatus, databaseResponseTimeMs),
                Instant.now()
        );
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
