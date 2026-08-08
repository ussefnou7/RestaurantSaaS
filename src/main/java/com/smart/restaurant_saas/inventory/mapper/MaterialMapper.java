package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.material.MaterialCatalog;
import com.smart.restaurant_saas.inventory.material.dto.MaterialResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Component
public class MaterialMapper {

    public MaterialResponse toResponse(Material m) {
        MaterialCategory category = m.getCategory();
        Uom stockUom = m.getStockUom();
        Uom displayUom = m.getDisplayUom();
        MaterialCatalog catalog = m.getCatalog();

        return MaterialResponse.builder()
            .id(m.getId())
            .code(m.getCode())
            .name(m.getName())
            .nameAr(m.getNameAr())
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
            // defaultUom mirrors the stock UOM
            .defaultUomId(stockUom != null ? stockUom.getId() : null)
            .defaultUomName(stockUom != null ? stockUom.getName() : null)
            .defaultUomCode(stockUom != null ? stockUom.getCode() : null)
            .defaultUomSymbol(stockUom != null ? stockUom.getSymbol() : null)
            .catalogId(catalog != null ? catalog.getId() : null)
            .active(m.getActive())
            .notes(m.getNotes())
            .createdAt(m.getCreatedAt())
            .updatedAt(m.getUpdatedAt())
            .build();
    }
}
