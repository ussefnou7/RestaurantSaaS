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
            .materialId(material.getId())
            .materialName(material.getName())
            .quantity(item.getQuantity())
            .uomId(uom.getId())
            .uomName(uom.getName())
            .build();
    }
}
