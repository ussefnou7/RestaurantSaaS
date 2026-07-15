package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionDocLineResponse {

    private final Long id;
    private final Long orderId;
    private final Long createdBy;
    private final boolean consumed;
}
