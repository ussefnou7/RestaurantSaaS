package com.smart.restaurant_saas.inventory.orderconsumption;

import java.math.BigDecimal;

public interface RecipeQuantity {

    Long getRecipeId();

    BigDecimal getQuantity();
}
