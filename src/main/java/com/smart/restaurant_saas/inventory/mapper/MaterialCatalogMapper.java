package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Uom;
import org.springframework.stereotype.Component;

@Component
public class MaterialCatalogMapper {

    public MaterialCatalogResponse toResponse(MaterialCatalog material) {
        MaterialCategory category = material.getCategory();
        Uom defaultStockUom = material.getDefaultStockUom();
        Uom defaultDisplayUom = material.getDefaultDisplayUom();
        return new MaterialCatalogResponse(
                material.getId(),
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getNameAr(),
                defaultStockUom.getId(),
                defaultStockUom.getCode(),
                defaultStockUom.getName(),
                defaultStockUom.getNameAr(),
                defaultStockUom.getSymbol(),
                defaultDisplayUom.getId(),
                defaultDisplayUom.getCode(),
                defaultDisplayUom.getName(),
                defaultDisplayUom.getNameAr(),
                defaultDisplayUom.getSymbol(),
                material.getCode(),
                material.getName(),
                material.getNameAr(),
                material.getActive(),
                material.getSortOrder(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
