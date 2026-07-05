package com.smart.restaurant_saas.menu.recipe.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeItemRequest {

    @NotNull(message = "materialId is required")
    private Long materialId;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.000001", message = "quantity must be positive")
    @Digits(integer = 12, fraction = 6, message = "quantity must have at most 6 decimal places")
    private BigDecimal quantity;

    @NotNull(message = "uomId is required")
    private Long uomId;
}
