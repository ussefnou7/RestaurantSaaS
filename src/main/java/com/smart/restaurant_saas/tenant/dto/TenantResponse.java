package com.smart.restaurant_saas.tenant.dto;

import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantStatus;
import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String name,
        String code,
        TenantStatus status,
        String timezone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getCode(),
                tenant.getStatus(),
                tenant.getTimezone(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
