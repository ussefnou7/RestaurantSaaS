package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.material.MaterialCatalog;
import com.smart.restaurant_saas.inventory.material.dto.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Component
public class MaterialCatalogMapper {

    public MaterialCatalogResponse toResponse(MaterialCatalog c, Long importedMaterialId) {
        MaterialCategory category = c.getCategory();
        Uom stockUom = c.getDefaultStockUom();
        Uom displayUom = c.getDefaultDisplayUom();

        return MaterialCatalogResponse.builder()
            .id(c.getId())
            .code(c.getCode())
            .name(c.getName())
            .nameAr(c.getNameAr())
            .categoryId(category != null ? category.getId() : null)
            .categoryName(category != null ? category.getName() : null)
            .stockUomId(stockUom != null ? stockUom.getId() : null)
            .stockUomName(stockUom != null ? stockUom.getName() : null)
            .stockUomCode(stockUom != null ? stockUom.getCode() : null)
            .stockUomSymbol(stockUom != null ? stockUom.getSymbol() : null)
            .displayUomId(displayUom != null ? displayUom.getId() : null)
            .displayUomName(displayUom != null ? displayUom.getName() : null)
            .displayUomCode(displayUom != null ? displayUom.getCode() : null)
            .displayUomSymbol(displayUom != null ? displayUom.getSymbol() : null)
            .defaultUomId(stockUom != null ? stockUom.getId() : null)
            .defaultUomName(stockUom != null ? stockUom.getName() : null)
            .defaultUomCode(stockUom != null ? stockUom.getCode() : null)
            .defaultUomSymbol(stockUom != null ? stockUom.getSymbol() : null)
            .active(c.getActive())
            .alreadyImported(importedMaterialId != null)
            .importedMaterialId(importedMaterialId)
            .build();
    }
}
