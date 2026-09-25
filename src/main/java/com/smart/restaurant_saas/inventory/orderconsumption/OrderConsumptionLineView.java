package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderLineType;

public interface OrderConsumptionLineView {

    Long getId();

    Long getOrderId();

    Long getCreatedBy();

    /** SALE or WASTE (D20) — the order line's own kind, not the doc's. */
    OrderLineType getLineType();

    /** Null on a sale line. */
    CancellationStage getWasteStage();
}
