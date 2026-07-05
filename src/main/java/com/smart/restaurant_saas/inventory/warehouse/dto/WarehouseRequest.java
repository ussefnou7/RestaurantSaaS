package com.smart.restaurant_saas.inventory.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;

@Getter
@Setter
public class WarehouseRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    @NotNull(message = "type is required")
    private WarehouseType type;

    private Long branchId;

    @NotNull(message = "active is required")
    private Boolean active = true;

    private String notes;
}
