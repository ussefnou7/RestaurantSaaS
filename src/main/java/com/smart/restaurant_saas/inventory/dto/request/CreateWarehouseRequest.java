package com.smart.restaurant_saas.inventory.dto.request;

import com.smart.restaurant_saas.inventory.enums.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        Long branchId,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nameAr,
        @NotNull WarehouseType type,
        Boolean active,
        String notes
) {
}
