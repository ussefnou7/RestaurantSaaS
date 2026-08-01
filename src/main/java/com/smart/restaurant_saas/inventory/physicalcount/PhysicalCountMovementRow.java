package com.smart.restaurant_saas.inventory.physicalcount;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;

/**
 * Ledger movement fields needed to reconcile physical-count lines against their own count time.
 */
public record PhysicalCountMovementRow(
    Long transactionId,
    Long materialId,
    BigDecimal signedStockQuantity,
    InventoryTransactionDirection direction,
    LocalDateTime movementDate,
    LocalDateTime createdAt,
    String referenceType,
    Long referenceId
) {

    public PhysicalCountMovementRow(
            Long materialId,
            BigDecimal signedStockQuantity,
            LocalDateTime movementDate) {
        this(null, materialId, signedStockQuantity,
            signedStockQuantity.signum() < 0
                ? InventoryTransactionDirection.OUT
                : InventoryTransactionDirection.IN,
            movementDate, null, null, null);
    }
}
