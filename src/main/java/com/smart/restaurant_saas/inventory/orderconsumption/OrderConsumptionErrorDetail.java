package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

public record OrderConsumptionErrorDetail(
    Long materialId,
    String materialName,
    BigDecimal requiredQuantity,
    BigDecimal availableQuantity,
    Long uomId,
    String uomSymbol,
    Long warehouseId,
    String warehouseName,
    String exceptionClass,
    String message
) {

    public OrderConsumptionErrorDetail(
            Long materialId,
            String materialName,
            String exceptionClass,
            String message) {
        this(materialId, materialName, null, null, null, null, null, null,
            exceptionClass, message);
    }
}
