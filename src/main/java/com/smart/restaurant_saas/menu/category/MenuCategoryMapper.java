package com.smart.restaurant_saas.menu.category;

import com.smart.restaurant_saas.menu.category.dto.MenuCategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class MenuCategoryMapper {

    public MenuCategoryResponse toResponse(MenuCategory category) {
        return MenuCategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .sortOrder(category.getSortOrder())
            .isActive(category.isActive())
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .build();
    }
}
