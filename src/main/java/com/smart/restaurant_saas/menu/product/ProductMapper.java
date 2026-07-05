package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        MenuCategory category = product.getMenuCategory();
        return ProductResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .sellingPrice(product.getSellingPrice())
            .active(product.isActive())
            .menuCategoryId(category.getId())
            .menuCategoryName(category.getName())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }
}
