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
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeItemRepository recipeItemRepository;
    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final UomRepository uomRepository;
    private final RecipeItemMapper mapper;

    @Transactional(readOnly = true)
    public List<RecipeItemResponse> getRecipeForProduct(Long productId, Long tenantId) {
        loadProduct(productId, tenantId);
        return recipeItemRepository.findByProductId(productId, tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public List<RecipeItemResponse> replaceRecipe(Long productId,
                                                  List<RecipeItemRequest> requests,
                                                  Long tenantId,
                                                  Long userId) {
        Product product = loadProduct(productId, tenantId);
        Set<Long> materialIds = new HashSet<>();
        List<RecipeItem> replacement = new ArrayList<>(requests.size());

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

            RecipeItem item = new RecipeItem();
            item.setTenantId(tenantId);
            item.setProduct(product);
            item.setMaterial(material);
            item.setQuantity(request.getQuantity());
            item.setUom(uom);
            item.setCreatedBy(userId);
            replacement.add(item);
        }

        recipeItemRepository.deleteByProductId(productId, tenantId);
        return recipeItemRepository.saveAll(replacement).stream()
            .map(mapper::toResponse)
            .toList();
    }

    private Product loadProduct(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + productId,
                ErrorParams.of("entityType", "Product", "entityId", productId)));
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
}
