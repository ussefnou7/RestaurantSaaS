package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import org.springframework.stereotype.Component;

@Component
public class MaterialCategoryMapper {

    public MaterialCategoryResponse toResponse(MaterialCategory category) {
        return new MaterialCategoryResponse(
                category.getId(),
                category.getTenantId(),
                category.getCode(),
                category.getName(),
                category.getNameAr(),
                category.getActive(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
