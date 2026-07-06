package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemRequest;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@Tag(name = "Menu - Recipes", description = "Versioned product recipe management")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/products/{productId}/recipes")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get recipe history for a product")
    public List<RecipeResponse> getRecipeHistory(
            @PathVariable Long productId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return recipeService.getRecipeHistory(productId, tenantId);
    }

    @GetMapping("/products/{productId}/recipes/active")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get the active recipe for a product")
    public RecipeResponse getActiveRecipe(
            @PathVariable Long productId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return recipeService.getActiveRecipe(productId, tenantId);
    }

    @GetMapping("/recipes/{recipeId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get a specific recipe version")
    public RecipeResponse getRecipeById(
            @PathVariable Long recipeId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return recipeService.getRecipeById(recipeId, tenantId);
    }

    @PostMapping("/products/{productId}/recipes")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(
        summary = "Create a new recipe version for a product",
        description = "Validates the request, deactivates the current active recipe, and stores a new immutable version."
    )
    public ResponseEntity<RecipeResponse> createNewVersion(
            @PathVariable Long productId,
            @Valid @RequestBody List<@Valid RecipeItemRequest> requests,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(recipeService.createNewVersion(productId, requests, tenantId, userId));
    }
}
