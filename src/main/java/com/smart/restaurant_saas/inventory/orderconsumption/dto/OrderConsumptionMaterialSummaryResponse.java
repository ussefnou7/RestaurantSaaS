package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionMaterialSummaryResponse {

    private final Long materialId;
    private final String materialName;
    private final String uom;
    private final BigDecimal totalQtyConsumed;
    private final long orderCount;
}
