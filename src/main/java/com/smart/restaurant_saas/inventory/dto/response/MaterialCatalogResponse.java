package com.smart.restaurant_saas.inventory.dto.response;

import java.time.LocalDateTime;

public record MaterialCatalogResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String categoryNameAr,
        Long defaultStockUomId,
        String defaultStockUomCode,
        String defaultStockUomName,
        String defaultStockUomNameAr,
        String defaultStockUomSymbol,
        Long defaultDisplayUomId,
        String defaultDisplayUomCode,
        String defaultDisplayUomName,
        String defaultDisplayUomNameAr,
        String defaultDisplayUomSymbol,
        String code,
        String name,
        String nameAr,
        Boolean active,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
