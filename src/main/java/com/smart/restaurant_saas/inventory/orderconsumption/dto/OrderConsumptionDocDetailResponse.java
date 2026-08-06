package com.smart.restaurant_saas.inventory.orderconsumption.dto;

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

    /**
     * One row per material the doc requires, with its consumption outcome. Empty while the doc is
     * PENDING — the aggregation that produces these rows has not run yet.
     */
    private final List<OrderConsumptionDocMaterialResponse> materials;

    private final List<OrderConsumptionDocLineResponse> lines;
}
