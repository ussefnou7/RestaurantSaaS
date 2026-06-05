package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMaterialRequest(
        @NotNull Long categoryId,
        @NotNull Long stockUomId,
        @NotNull Long displayUomId,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nameAr,
        @DecimalMin(value = "0.0") BigDecimal minimumStockLevel,
        Boolean active,
        String notes
) {
}
