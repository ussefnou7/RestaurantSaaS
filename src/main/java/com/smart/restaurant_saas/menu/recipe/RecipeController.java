package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemRequest;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu/products/{productId}/recipe")
@RequiredArgsConstructor
@Tag(name = "Menu - Recipes", description = "Direct product recipe management")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get a product recipe")
    public List<RecipeItemResponse> getRecipe(
            @PathVariable Long productId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return recipeService.getRecipeForProduct(productId, tenantId);
    }

    @PutMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(
        summary = "Replace a product recipe",
        description = "Validates the complete request, then atomically replaces all recipe items."
    )
    public List<RecipeItemResponse> replaceRecipe(
            @PathVariable Long productId,
            @Valid @RequestBody List<@Valid RecipeItemRequest> requests,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return recipeService.replaceRecipe(productId, requests, tenantId, userId);
    }
}
