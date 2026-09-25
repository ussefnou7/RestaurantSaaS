package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderLineType;
import lombok.Builder;
import lombok.Getter;

/**
 * An order line attached to the doc. Carries no consumed flag: consumption happens per material
 * (D29 step 3) and one line requires several materials, so the outcome is on
 * {@link OrderConsumptionDocMaterialResponse}.
 */
@Getter
@Builder
public class OrderConsumptionDocLineResponse {

    private final Long id;
    private final Long orderId;
    private final Long createdBy;
    /** SALE or WASTE (D20). Every line of a WASTE doc is a WASTE line. */
    private final OrderLineType lineType;
    /** The cooked stage the dish was binned at; null on a sale line. */
    private final CancellationStage wasteStage;
}
