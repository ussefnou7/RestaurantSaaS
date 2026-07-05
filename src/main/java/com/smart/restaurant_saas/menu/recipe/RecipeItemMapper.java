package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.menu.recipe.dto.RecipeItemResponse;
import org.springframework.stereotype.Component;

@Component
public class RecipeItemMapper {

    public RecipeItemResponse toResponse(RecipeItem item) {
        Material material = item.getMaterial();
        Uom uom = item.getUom();
        return RecipeItemResponse.builder()
            .id(item.getId())
            .productId(item.getProduct().getId())
            .materialId(material.getId())
            .materialName(material.getName())
            .materialNameAr(material.getNameAr())
            .quantity(item.getQuantity())
            .uomId(uom.getId())
            .uomName(uom.getName())
            .uomNameAr(uom.getNameAr())
            .uomCode(uom.getCode())
            .uomSymbol(uom.getSymbol())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
