package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.category.dto.MaterialCategoryResponse;

@Component
public class MaterialCategoryMapper {

    public MaterialCategoryResponse toResponse(MaterialCategory c) {
        return MaterialCategoryResponse.builder()
            .id(c.getId())
            .code(c.getCode())
            .name(c.getName())
            .nameAr(c.getNameAr())
            .global(c.getTenantId() == null)
            .active(c.getActive())
            .sortOrder(c.getSortOrder())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }
}
