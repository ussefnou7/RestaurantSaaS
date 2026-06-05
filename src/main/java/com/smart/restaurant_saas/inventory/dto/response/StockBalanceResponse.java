package com.smart.restaurant_saas.inventory.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockBalanceResponse(
        Long id,
        Long tenantId,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String warehouseNameAr,
        Long materialId,
        String materialCode,
        String materialName,
        String materialNameAr,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String categoryNameAr,
        Long uomId,
        String uomCode,
        String uomName,
        String uomNameAr,
        String uomSymbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal stockValue,
        BigDecimal displayQuantity,
        Long displayUomId,
        String displayUomCode,
        String displayUomName,
        String displayUomNameAr,
        String displayUomSymbol,
        BigDecimal minimumStockLevel,
        Boolean lowStock,
        LocalDateTime updatedAt
) {
}
