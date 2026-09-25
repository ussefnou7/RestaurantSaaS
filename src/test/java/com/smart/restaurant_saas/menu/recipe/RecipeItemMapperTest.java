package com.smart.restaurant_saas.menu.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RecipeItemMapperTest {

    @Test
    void mapsTheMaterialArabicNameForLocalizedRecipeViews() {
        Material material = new Material();
        material.setId(11L);
        material.setName("Flour");
        material.setNameAr("طحين");

        Uom uom = new Uom();
        uom.setId(22L);

        RecipeItem item = new RecipeItem();
        item.setMaterial(material);
        item.setUom(uom);
        item.setQuantity(new BigDecimal("40"));

        var response = new RecipeItemMapper().toResponse(item);

        assertThat(response.getMaterialName()).isEqualTo("Flour");
        assertThat(response.getMaterialNameAr()).isEqualTo("طحين");
    }
}
