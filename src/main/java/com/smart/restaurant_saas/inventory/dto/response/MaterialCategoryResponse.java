package com.smart.restaurant_saas.inventory.dto.response;

import java.time.LocalDateTime;

public record MaterialCategoryResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String nameAr,
        Boolean active,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
