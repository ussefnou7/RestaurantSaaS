package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionStatus;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionDocResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    /** ORDINARY or WASTE (D20): same lifecycle, different ledger movement. */
    private final OrderConsumptionType type;
    private final OrderConsumptionStatus status;
    private final LocalDateTime processedAt;
}
