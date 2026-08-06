package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

/** A per-material quantity in the material's display UOM (D87 layer 2). */
public interface MaterialQuantity {

    Long getMaterialId();

    BigDecimal getQuantity();
}
