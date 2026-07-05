package com.smart.restaurant_saas.menu.recipe.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeItemResponse {

    private final Long id;
    private final Long productId;
    private final Long materialId;
    private final String materialName;
    private final String materialNameAr;
    private final BigDecimal quantity;
    private final Long uomId;
    private final String uomName;
    private final String uomNameAr;
    private final String uomCode;
    private final String uomSymbol;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
