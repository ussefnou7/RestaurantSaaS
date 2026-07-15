package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.menu.recipe.RecipeItem;
import com.smart.restaurant_saas.menu.recipe.RecipeItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderConsumptionAvailabilityService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderConsumptionLineRepository lineRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final UomConversionService uomConversionService;

    public Map<Long, BigDecimal> findPendingDisplayQuantitiesByMaterial(Long tenantId, Long warehouseId) {
        List<RecipeQuantity> recipeQuantities = lineRepository.sumRecipeQuantitiesByWarehouseAndStatus(
            tenantId, warehouseId, OrderConsumptionStatus.PENDING);
        if (recipeQuantities.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<RecipeItem>> itemsByRecipeId = loadItemsByRecipeId(recipeQuantities, tenantId);
        Map<Long, BigDecimal> quantitiesByMaterialId = new HashMap<>();
        for (RecipeQuantity recipeQuantity : recipeQuantities) {
            List<RecipeItem> items = itemsByRecipeId.getOrDefault(recipeQuantity.getRecipeId(), List.of());
            for (RecipeItem item : items) {
                Material material = item.getMaterial();
                BigDecimal rawQuantity = item.getQuantity()
                    .multiply(recipeQuantity.getQuantity())
                    .setScale(SCALE, ROUNDING);
                BigDecimal displayQuantity = uomConversionService.convert(
                    rawQuantity, item.getUom(), material.getDisplayUom(), material, tenantId);
                quantitiesByMaterialId.merge(
                    material.getId(),
                    displayQuantity,
                    (left, right) -> left.add(right).setScale(SCALE, ROUNDING));
            }
        }
        return quantitiesByMaterialId;
    }

    private Map<Long, List<RecipeItem>> loadItemsByRecipeId(List<RecipeQuantity> recipeQuantities, Long tenantId) {
        Set<Long> recipeIds = new HashSet<>();
        for (RecipeQuantity recipeQuantity : recipeQuantities) {
            recipeIds.add(recipeQuantity.getRecipeId());
        }
        return recipeItemRepository.findByRecipeIds(List.copyOf(recipeIds), tenantId)
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(item -> item.getRecipe().getId()));
    }
}
