package com.smart.restaurant_saas.inventory.material.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OpeningBalanceResponse {

    private final Long transactionId;
    private final Long warehouseId;
    private final String warehouseName;
    private final Long materialId;
    private final String materialName;

    /** Final stored quantity in the material's stock UOM. */
    private final BigDecimal stockQuantity;
    private final String stockUomCode;
    private final String stockUomSymbol;

    /** Unit cost in the stock UOM (i.e., per stockUom unit). */
    private final BigDecimal stockUnitCost;
    private final BigDecimal totalCost;

    /** What the user originally entered, for echo / UX confirmation. */
    private final BigDecimal enteredQuantity;
    private final String enteredUomCode;

    private final LocalDateTime transactionDate;

    /**
     * True if this request hit an existing opening balance and no new
     * transaction was created (the existing one was returned).
     */
    private final boolean idempotentHit;
}
