package com.smart.restaurant_saas.inventory.material.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpeningBalanceRequest {

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @NotNull(message = "materialId is required")
    private Long materialId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

    /**
     * UOM used for the quantity. If null, the material's displayUom is assumed.
     */
    private Long uomId;

    /**
     * Unit cost in the entered UOM. Optional — leave null if cost is unknown.
     */
    @PositiveOrZero(message = "unitCost must be non-negative")
    private BigDecimal unitCost;

    private String notes;
}
