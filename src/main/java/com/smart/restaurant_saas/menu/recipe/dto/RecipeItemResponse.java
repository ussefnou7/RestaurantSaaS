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
    /** The unit only; the client resolves its name from the UOM lookup cache (D111 phase 3). */
    private final Long uomId;
}
