package com.smart.restaurant_saas.menu.recipe.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeItemResponse {

    private final Long materialId;
    private final String materialName;
    private final BigDecimal quantity;
    private final Long uomId;
    private final String uomName;
}
