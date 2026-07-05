package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;

@Getter
@Builder
public class PhysicalCountSummaryResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    private final String code;
    private final LocalDate scheduledDate;
    private final PhysicalCountStatus status;
    private final Boolean hasLargeVariance;
    private final BigDecimal largeVarianceValue;
    private final Integer lineCount;
    private final Integer varianceCount;
    private final LocalDateTime createdAt;
}
