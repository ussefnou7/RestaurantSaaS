package com.smart.restaurant_saas.inventory.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "stockUomId is required")
    private Long stockUomId;

    @NotNull(message = "displayUomId is required")
    private Long displayUomId;

    /** Alias for stockUomId — accepted for UI compatibility (same value). */
    @NotNull(message = "defaultUomId is required")
    private Long defaultUomId;

    @PositiveOrZero(message = "minimumStockLevel must be non-negative")
    private BigDecimal minimumStockLevel;

    @NotNull(message = "active is required")
    private Boolean active = true;

    private String notes;
}
