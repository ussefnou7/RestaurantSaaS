package com.smart.restaurant_saas.inventory.orderconsumption.dto;

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
}
