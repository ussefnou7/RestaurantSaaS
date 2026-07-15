package com.smart.restaurant_saas.order.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderLineResponse {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final Long recipeId;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
