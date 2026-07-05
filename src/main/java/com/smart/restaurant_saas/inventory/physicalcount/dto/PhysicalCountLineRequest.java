package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhysicalCountLineRequest {

    @NotNull(message = "materialId is required")
    private Long materialId;
}
