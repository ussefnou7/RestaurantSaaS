package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

public interface MaterialSummary {

    Long getMaterialId();

    String getMaterialName();

    String getUom();

    BigDecimal getTotalQtyConsumed();

    Long getOrderCount();
}
