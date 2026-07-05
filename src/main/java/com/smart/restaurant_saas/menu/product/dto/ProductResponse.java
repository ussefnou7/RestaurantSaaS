package com.smart.restaurant_saas.menu.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal sellingPrice;
    private final Boolean active;
    private final Long menuCategoryId;
    private final String menuCategoryName;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
