package com.smart.restaurant_saas.inventory.dto.response;

import com.smart.restaurant_saas.inventory.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long id,
        Long tenantId,
        InventoryTransactionType transactionType,
        InventoryTransactionDirection direction,
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
        BigDecimal enteredQuantity,
        Long enteredUomId,
        String enteredUomCode,
        String enteredUomName,
        String enteredUomNameAr,
        String enteredUomSymbol,
        BigDecimal stockQuantity,
        Long stockUomId,
        String stockUomCode,
        String stockUomName,
        String stockUomNameAr,
        String stockUomSymbol,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        Long referenceId,
        LocalDateTime transactionDate,
        String notes,
        Long createdBy,
        LocalDateTime createdAt
) {
}
