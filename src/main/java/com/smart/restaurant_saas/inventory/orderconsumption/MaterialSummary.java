package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

public interface MaterialSummary {

    Long getMaterialId();

    String getMaterialName();

    default String getMaterialNameAr() {
        return null;
    }

    String getUom();

    BigDecimal getTotalQtyConsumed();

    Long getOrderCount();
}
