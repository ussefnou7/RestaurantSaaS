package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.MaterialResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Uom;
import org.springframework.stereotype.Component;

@Component
public class MaterialMapper {

    public MaterialResponse toResponse(Material material) {
        MaterialCatalog catalog = material.getCatalog();
        MaterialCategory category = material.getCategory();
        Uom stockUom = material.getStockUom();
        Uom displayUom = material.getDisplayUom();
        return new MaterialResponse(
                material.getId(),
                material.getTenantId(),
                catalog == null ? null : catalog.getId(),
                catalog == null ? null : catalog.getCode(),
                catalog == null ? null : catalog.getName(),
                catalog == null ? null : catalog.getNameAr(),
                category.getId(),
                category.getTenantId(),
                category.getCode(),
                category.getName(),
                category.getNameAr(),
                stockUom.getId(),
                stockUom.getCode(),
                stockUom.getName(),
                stockUom.getNameAr(),
                stockUom.getSymbol(),
                displayUom.getId(),
                displayUom.getCode(),
                displayUom.getName(),
                displayUom.getNameAr(),
                displayUom.getSymbol(),
                material.getCode(),
                material.getName(),
                material.getNameAr(),
                material.getMinimumStockLevel(),
                material.getActive(),
                material.getNotes(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
