package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

/** Outstanding display-UOM quantity and cost per display unit (D87). */
public interface UnpostedConsumptionCostRow {

    Long getBranchId();

    Long getWarehouseId();

    Long getMaterialId();

    BigDecimal getRequiredQuantity();

    BigDecimal getAverageCost();
}
