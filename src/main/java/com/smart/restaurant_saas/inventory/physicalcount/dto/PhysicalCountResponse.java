package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;

@Getter
@Builder
public class PhysicalCountResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    private final String warehouseNameAr;
    private final String code;
    private final LocalDate scheduledDate;
    private final PhysicalCountStatus status;
    private final String notes;
    private final Boolean hasLargeVariance;
    private final BigDecimal largeVarianceValue;

    /** Sum of ABS(line variance x cost). Drives hasLargeVariance. Null for pre-V51 counts. */
    private final BigDecimal grossVarianceValue;
    private final LocalDateTime frozenAt;
    private final LocalDateTime reconciledAt;
    private final List<PhysicalCountLineResponse> lines;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
