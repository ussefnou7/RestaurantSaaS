package com.smart.restaurant_saas.inventory.dto.request;

import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateManualInventoryTransactionRequest(
        @NotNull Long warehouseId,
        @NotNull Long materialId,
        @NotNull InventoryTransactionType transactionType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotNull Long uomId,
        @DecimalMin(value = "0.0") BigDecimal unitCost,
        LocalDateTime transactionDate,
        String notes
) {
}
