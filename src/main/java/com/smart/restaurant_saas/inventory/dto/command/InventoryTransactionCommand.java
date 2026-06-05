package com.smart.restaurant_saas.inventory.dto.command;

import com.smart.restaurant_saas.inventory.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTransactionCommand(
        Long warehouseId,
        Long materialId,
        InventoryTransactionType transactionType,
        InventoryTransactionDirection direction,
        BigDecimal enteredQuantity,
        Long enteredUomId,
        BigDecimal unitCost,
        String referenceType,
        Long referenceId,
        LocalDateTime transactionDate,
        String notes
) {
}
