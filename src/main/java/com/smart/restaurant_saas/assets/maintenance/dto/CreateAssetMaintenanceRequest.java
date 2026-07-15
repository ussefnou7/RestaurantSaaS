package com.smart.restaurant_saas.assets.maintenance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAssetMaintenanceRequest {

    @NotNull(message = "assetId is required")
    private Long assetId;

    @NotNull(message = "assetLineId is required")
    private Long assetLineId;

    @NotNull(message = "cost is required")
    @PositiveOrZero(message = "cost must be zero or positive")
    private BigDecimal cost;

    @NotNull(message = "maintenanceDate is required")
    private LocalDate maintenanceDate;

    private String description;

    private String vendor;
}
