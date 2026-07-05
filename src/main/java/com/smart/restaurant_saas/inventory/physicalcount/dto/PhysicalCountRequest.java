package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhysicalCountRequest {

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @NotNull(message = "scheduledDate is required")
    private LocalDate scheduledDate;

    private String notes;

    @NotEmpty(message = "at least one material is required")
    private List<Long> materialIds;
}
