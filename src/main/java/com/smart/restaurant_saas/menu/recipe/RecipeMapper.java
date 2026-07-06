package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.menu.recipe.dto.RecipeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipeMapper {

    private final RecipeItemMapper recipeItemMapper;

    public RecipeResponse toResponse(Recipe recipe, List<RecipeItem> items) {
        return RecipeResponse.builder()
            .id(recipe.getId())
            .isActive(recipe.isActive())
            .createdAt(recipe.getCreatedAt())
            .items(items.stream().map(recipeItemMapper::toResponse).toList())
            .build();
    }
}
