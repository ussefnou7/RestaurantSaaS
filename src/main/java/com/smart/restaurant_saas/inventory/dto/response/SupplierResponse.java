package com.smart.restaurant_saas.inventory.dto.response;

import java.time.LocalDateTime;

public record SupplierResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String nameAr,
        String phone,
        String email,
        String address,
        String taxNumber,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
