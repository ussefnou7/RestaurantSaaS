package com.smart.restaurant_saas.menu.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "sellingPrice is required")
    @DecimalMin(value = "0.00", message = "sellingPrice must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "sellingPrice must have at most 2 decimal places")
    private BigDecimal sellingPrice;

    @NotNull(message = "menuCategoryId is required")
    private Long menuCategoryId;
}
