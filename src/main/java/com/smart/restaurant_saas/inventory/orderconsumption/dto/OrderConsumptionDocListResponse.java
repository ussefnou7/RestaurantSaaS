package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionStatus;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionDocListResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    /** ORDINARY or WASTE (D20): without it the two are indistinguishable in the list. */
    private final OrderConsumptionType type;
    private final OrderConsumptionStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime processedAt;
    private final long lineCount;
}
