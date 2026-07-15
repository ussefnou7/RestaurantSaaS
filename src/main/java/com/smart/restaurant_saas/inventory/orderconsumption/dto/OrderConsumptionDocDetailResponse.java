package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionErrorDetail;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsumptionDocDetailResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    private final OrderConsumptionStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime processedAt;
    private final List<OrderConsumptionErrorDetail> errorDetails;
    private final List<OrderConsumptionDocLineResponse> lines;
}
