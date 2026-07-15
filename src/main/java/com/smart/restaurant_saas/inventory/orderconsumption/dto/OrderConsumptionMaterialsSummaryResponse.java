package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionMaterialsSummaryResponse {

    private final Long docId;
    private final List<OrderConsumptionMaterialSummaryResponse> materials;
}
