package com.smart.restaurant_saas.assets.maintenance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetMaintenanceResponse {

    private final Long id;
    private final Long assetId;
    private final Long assetLineId;
    private final BigDecimal cost;
    private final LocalDate maintenanceDate;
    private final String description;
    private final String vendor;
    private final Long createdBy;
    private final LocalDateTime createdAt;
}
