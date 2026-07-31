package com.smart.restaurant_saas.inventory.physicalcount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ledger movement fields needed to reconcile physical-count lines against their own count time.
 */
public record PhysicalCountMovementRow(
    Long materialId,
    BigDecimal signedStockQuantity,
    LocalDateTime movementDate
) {
}
