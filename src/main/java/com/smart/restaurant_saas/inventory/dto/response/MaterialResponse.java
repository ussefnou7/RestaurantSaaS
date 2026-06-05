package com.smart.restaurant_saas.inventory.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MaterialResponse(
        Long id,
        Long tenantId,
        Long catalogId,
        String catalogCode,
        String catalogName,
        String catalogNameAr,
        Long categoryId,
        Long categoryTenantId,
        String categoryCode,
        String categoryName,
        String categoryNameAr,
        Long stockUomId,
        String stockUomCode,
        String stockUomName,
        String stockUomNameAr,
        String stockUomSymbol,
        Long displayUomId,
        String displayUomCode,
        String displayUomName,
        String displayUomNameAr,
        String displayUomSymbol,
        String code,
        String name,
        String nameAr,
        BigDecimal minimumStockLevel,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
