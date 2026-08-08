package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /**
     * Maps a full product list, computing derived parenthood in-memory (a product is a parent iff
     * any other product in the list references it via parentProductId) to avoid N+1 repo lookups.
     */
    public List<ProductResponse> toResponseList(List<Product> products) {
        Set<Long> parentIds = products.stream()
            .map(Product::getParentProductId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        return products.stream()
            .map(product -> toResponse(product, parentIds.contains(product.getId())))
            .toList();
    }

    public ProductResponse toResponse(Product product, boolean isParent) {
        MenuCategory category = product.getMenuCategory();
        return ProductResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .descriptionAr(product.getDescriptionAr())
            .sellingPrice(product.getSellingPrice())
            .active(product.isActive())
            .menuCategoryId(category.getId())
            .menuCategoryName(category.getName())
            .parentProductId(product.getParentProductId())
            .variantLabel(product.getVariantLabel())
            .variantLabelAr(product.getVariantLabelAr())
            .isMenu(product.getIsMenu())
            .isParent(isParent)
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }
}
