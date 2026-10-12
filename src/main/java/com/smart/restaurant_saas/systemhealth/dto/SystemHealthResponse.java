package com.smart.restaurant_saas.systemhealth.dto;

import java.time.Instant;

public record SystemHealthResponse(
        HealthCheck server,
        HealthCheck database,
        Instant checkedAt
) {
    public record HealthCheck(String status, long responseTimeMs) {
    }
}
