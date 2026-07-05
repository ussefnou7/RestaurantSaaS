package com.smart.restaurant_saas.inventory.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;

@Getter
@Builder
public class LedgerCommand {

    private final Long tenantId;
    private final Long warehouseId;
    private final Long materialId;
    private final InventoryTransactionType transactionType;
    private final InventoryTransactionDirection direction;
    private final BigDecimal enteredQuantity;
    private final Long enteredUomId;
    private final BigDecimal enteredUnitCost;
    private final String referenceType;
    private final Long referenceId;
    private final Long sourceInvoiceLineId;
    private final String reasonCode;
    private final String idempotencyKey;
    private final String batchNumber;
    private final LocalDate expiryDate;
    private final Long shiftId;
    private final String notes;
    private final LocalDateTime transactionDate;
    private final LocalDateTime movementDate;
    private final Long createdBy;
}
