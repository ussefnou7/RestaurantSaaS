package com.smart.restaurant_saas.inventory.dto.response;

import com.smart.restaurant_saas.inventory.enums.UomType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UomResponse(
        Long id,
        String code,
        String name,
        String nameAr,
        String symbol,
        UomType type,
        String baseCode,
        BigDecimal factorToBase,
        Boolean active,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
