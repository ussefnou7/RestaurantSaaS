package com.smart.restaurant_saas.inventory.dto.request;

import com.smart.restaurant_saas.inventory.enums.UomType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateUomRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nameAr,
        @NotBlank @Size(max = 50) String symbol,
        @NotNull UomType type,
        @NotBlank @Size(max = 100) String baseCode,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal factorToBase,
        Boolean active,
        Integer sortOrder
) {
}
