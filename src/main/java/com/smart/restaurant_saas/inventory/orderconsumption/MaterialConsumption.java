package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

record MaterialConsumption(
    Long materialId,
    String materialName,
    Long uomId,
    BigDecimal quantity
) {
}
