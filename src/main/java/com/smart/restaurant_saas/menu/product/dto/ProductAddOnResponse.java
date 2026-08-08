package com.smart.restaurant_saas.menu.product.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductAddOnResponse {

    private final Long id;
    private final Long productId;
    private final Long addOnProductId;
    private final String addOnProductName;
    private final BigDecimal addOnSellingPrice;
}
