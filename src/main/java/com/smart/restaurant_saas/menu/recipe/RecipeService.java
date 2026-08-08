package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemRequest;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final UomRepository uomRepository;
    private final RecipeMapper recipeMapper;

    @Transactional(readOnly = true)
    public List<RecipeResponse> getRecipeHistory(Long productId, Long tenantId) {
        loadProduct(productId, tenantId);
        List<Recipe> recipes = recipeRepository
            .findByProductIdAndTenantIdOrderByCreatedAtDescIdDesc(productId, tenantId);
        return mapRecipes(recipes, tenantId);
    }

    @Transactional
    public RecipeResponse createNewVersion(Long productId,
                                           List<RecipeItemRequest> requests,
                                           Long tenantId,
                                           Long userId) {
        Product product = loadProductForVersionCreation(productId, tenantId);
        // A parent shell groups variant children and is never orderable — it must not carry a recipe.
        if (productRepository.existsByParentProductId(productId)) {
            throw new BusinessException(MenuErrorCode.PARENT_PRODUCT_HAS_NO_RECIPE,
                "Product is a variant parent and cannot carry a recipe: " + productId,
                ErrorParams.of("productId", productId));
        }
        List<ResolvedRecipeItem> resolvedItems = resolveItems(productId, requests, tenantId);

        // Invariant: at most one active recipe per (tenant, product). We serialize version
        // creation with a product-row lock, then deactivate any current active recipe before insert.
        recipeRepository.findByProductIdAndTenantIdAndActiveTrue(productId, tenantId)
            .ifPresent(existing -> {
                existing.setActive(false);
                existing.setUpdatedBy(userId);
            });

        Recipe recipe = new Recipe();
        recipe.setTenantId(tenantId);
        recipe.setProduct(product);
        recipe.setActive(true);
        recipe.setCreatedBy(userId);
        Recipe savedRecipe = recipeRepository.save(recipe);

        List<RecipeItem> savedItems = recipeItemRepository
            .saveAll(buildRecipeItems(savedRecipe, resolvedItems, tenantId, userId));
        return recipeMapper.toResponse(savedRecipe, savedItems);
    }

    @Transactional(readOnly = true)
    public RecipeResponse getActiveRecipe(Long productId, Long tenantId) {
        loadProduct(productId, tenantId);
        Recipe recipe = recipeRepository.findByProductIdAndTenantIdAndActiveTrue(productId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.RECIPE_NOT_FOUND,
                "Active recipe not found for product: " + productId,
                ErrorParams.of("productId", productId)));
        return recipeMapper.toResponse(recipe, recipeItemRepository.findByRecipeId(recipe.getId(), tenantId));
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipeById(Long recipeId, Long tenantId) {
        Recipe recipe = recipeRepository.findByIdAndTenantId(recipeId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.RECIPE_VERSION_NOT_FOUND,
                "Recipe version not found: " + recipeId,
                ErrorParams.of("recipeId", recipeId)));
        return recipeMapper.toResponse(recipe, recipeItemRepository.findByRecipeId(recipeId, tenantId));
    }

    private Product loadProduct(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + productId,
                ErrorParams.of("entityType", "Product", "entityId", productId)));
    }

    private Product loadProductForVersionCreation(Long productId, Long tenantId) {
        return productRepository.findWithLockByIdAndTenantId(productId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + productId,
                ErrorParams.of("entityType", "Product", "entityId", productId)));
    }

    private List<ResolvedRecipeItem> resolveItems(Long productId,
                                                  List<RecipeItemRequest> requests,
                                                  Long tenantId) {
        Set<Long> materialIds = new HashSet<>();
        List<ResolvedRecipeItem> resolvedItems = new ArrayList<>(requests.size());

        for (RecipeItemRequest request : requests) {
            if (!materialIds.add(request.getMaterialId())) {
                throw new BusinessException(MenuErrorCode.DUPLICATE_MATERIAL_IN_RECIPE,
                    "Material appears more than once in recipe: " + request.getMaterialId(),
                    ErrorParams.of("productId", productId, "materialId", request.getMaterialId()));
            }

            Material material = materialRepository
                .findByIdAndTenantId(request.getMaterialId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.MATERIAL_NOT_FOUND,
                    "Material not found: " + request.getMaterialId(),
                    ErrorParams.of("entityType", "Material", "entityId", request.getMaterialId())));
            Uom uom = loadVisibleUom(request.getUomId(), tenantId);
            resolvedItems.add(new ResolvedRecipeItem(material, request.getQuantity(), uom));
        }

        return resolvedItems;
    }

    private List<RecipeItem> buildRecipeItems(Recipe recipe,
                                              List<ResolvedRecipeItem> resolvedItems,
                                              Long tenantId,
                                              Long userId) {
        List<RecipeItem> items = new ArrayList<>(resolvedItems.size());
        for (ResolvedRecipeItem resolvedItem : resolvedItems) {
            RecipeItem item = new RecipeItem();
            item.setTenantId(tenantId);
            item.setRecipe(recipe);
            item.setMaterial(resolvedItem.material());
            item.setQuantity(resolvedItem.quantity());
            item.setUom(resolvedItem.uom());
            item.setCreatedBy(userId);
            items.add(item);
        }
        return items;
    }

    private List<RecipeResponse> mapRecipes(List<Recipe> recipes, Long tenantId) {
        Map<Long, List<RecipeItem>> itemsByRecipeId = loadItemsByRecipeId(recipes, tenantId);
        return recipes.stream()
            .map(recipe -> recipeMapper.toResponse(
                recipe,
                itemsByRecipeId.getOrDefault(recipe.getId(), List.of())
            ))
            .toList();
    }

    private Map<Long, List<RecipeItem>> loadItemsByRecipeId(List<Recipe> recipes, Long tenantId) {
        if (recipes.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<RecipeItem>> itemsByRecipeId = new LinkedHashMap<>();
        List<Long> recipeIds = recipes.stream().map(Recipe::getId).toList();
        recipeIds.forEach(recipeId -> itemsByRecipeId.put(recipeId, new ArrayList<>()));

        for (RecipeItem item : recipeItemRepository.findByRecipeIds(recipeIds, tenantId)) {
            itemsByRecipeId.computeIfAbsent(item.getRecipe().getId(), ignored -> new ArrayList<>()).add(item);
        }

        return itemsByRecipeId;
    }

    private Uom loadVisibleUom(Long uomId, Long tenantId) {
        Uom uom = uomRepository.findById(uomId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.UOM_NOT_FOUND,
                "UOM not found: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId)));
        if (uom.getTenantId() != null && !uom.getTenantId().equals(tenantId)) {
            throw new ValidationException(MenuErrorCode.UOM_NOT_AVAILABLE_FOR_TENANT,
                "UOM is not available to tenant: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId));
        }
        return uom;
    }

    private record ResolvedRecipeItem(Material material, java.math.BigDecimal quantity, Uom uom) {}
}
