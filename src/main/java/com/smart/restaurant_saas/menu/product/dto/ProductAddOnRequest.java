package com.smart.restaurant_saas.menu.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductAddOnRequest {

    @NotNull(message = "addOnProductId is required")
    private Long addOnProductId;
}
