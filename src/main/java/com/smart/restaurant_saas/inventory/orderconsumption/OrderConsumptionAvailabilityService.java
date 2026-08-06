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

/**
 * D43/D94: how much of the warehouse's stock is already sold but not yet posted to the ledger, per
 * material and in display UOM (D87 layer 2), so the stock screen can show what is really left.
 *
 * <p>The figure comes from two sources, one per doc state, because a doc's requirement is only
 * known at the material grain once it has been aggregated:
 * <ul>
 *   <li><b>PENDING</b> — no material rows exist yet, so the requirement is re-derived on the fly
 *       from the lines' recipes.</li>
 *   <li><b>PARTIAL</b> — the material rows exist; the outstanding ones are read directly. This is
 *       the grain that matters: a line whose chicken consumed and whose bread did not leaves only
 *       bread outstanding. Re-deriving from the line would put chicken back and subtract it a
 *       second time from a balance it had already left.</li>
 *   <li><b>POSTED</b> — nothing outstanding.</li>
 * </ul>
 *
 * <p>The two sources agree at the transition, because the material rows are written from the same
 * aggregation the PENDING path performs. {@code OrderConsumptionMultiMaterialIntegrationTest
 * .availabilityIsUnchangedByTheMoveFromRecipeExpansionToMaterialRows} pins that equality — it is
 * the property that makes the split safe.
 *
 * <p>IN_PROGRESS and CONFLICT are excluded, unchanged from before this split.
 */
@Service
@RequiredArgsConstructor
public class OrderConsumptionAvailabilityService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderConsumptionLineRepository lineRepository;
    private final OrderConsumptionMaterialRepository materialRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final UomConversionService uomConversionService;

    public Map<Long, BigDecimal> findOutstandingDisplayQuantitiesByMaterial(Long tenantId, Long warehouseId) {
        Map<Long, BigDecimal> quantitiesByMaterialId = pendingDisplayQuantities(tenantId, warehouseId);
        for (MaterialQuantity outstanding : materialRepository.sumUnconsumedRequiredQuantitiesByWarehouse(
                tenantId, warehouseId, OrderConsumptionStatus.PARTIAL)) {
            merge(quantitiesByMaterialId, outstanding.getMaterialId(), outstanding.getQuantity());
        }
        return quantitiesByMaterialId;
    }

    /**
     * The PENDING half: the same fold {@code OrderConsumptionService} performs when it writes the
     * material rows — recipe totals expanded through their items, converted into each material's
     * display UOM, summed per material.
     */
    private Map<Long, BigDecimal> pendingDisplayQuantities(Long tenantId, Long warehouseId) {
        List<RecipeQuantity> recipeQuantities = lineRepository.sumPendingRecipeQuantitiesByWarehouse(
            tenantId, warehouseId, OrderConsumptionStatus.PENDING);
        Map<Long, BigDecimal> quantitiesByMaterialId = new HashMap<>();
        if (recipeQuantities.isEmpty()) {
            return quantitiesByMaterialId;
        }

        Map<Long, List<RecipeItem>> itemsByRecipeId = loadItemsByRecipeId(recipeQuantities, tenantId);
        for (RecipeQuantity recipeQuantity : recipeQuantities) {
            List<RecipeItem> items = itemsByRecipeId.getOrDefault(recipeQuantity.getRecipeId(), List.of());
            for (RecipeItem item : items) {
                Material material = item.getMaterial();
                BigDecimal rawQuantity = item.getQuantity()
                    .multiply(recipeQuantity.getQuantity())
                    .setScale(SCALE, ROUNDING);
                BigDecimal displayQuantity = uomConversionService.convert(
                    rawQuantity, item.getUom(), material.getDisplayUom(), material, tenantId);
                merge(quantitiesByMaterialId, material.getId(), displayQuantity);
            }
        }
        return quantitiesByMaterialId;
    }

    private void merge(Map<Long, BigDecimal> quantitiesByMaterialId, Long materialId, BigDecimal quantity) {
        quantitiesByMaterialId.merge(
            materialId,
            quantity.setScale(SCALE, ROUNDING),
            (left, right) -> left.add(right).setScale(SCALE, ROUNDING));
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
