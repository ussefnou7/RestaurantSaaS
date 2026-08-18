package com.smart.restaurant_saas.menu.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeResponse {

    private final Long id;
    @JsonProperty("isActive")
    private final Boolean isActive;
    private final LocalDateTime createdAt;
    private final List<RecipeItemResponse> items;
}
