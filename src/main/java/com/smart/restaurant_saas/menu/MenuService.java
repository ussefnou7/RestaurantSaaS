package com.smart.restaurant_saas.menu;

import com.smart.restaurant_saas.menu.dto.MenuAddOnResponse;
import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import com.smart.restaurant_saas.menu.dto.MenuItemType;
import com.smart.restaurant_saas.menu.dto.MenuVariantResponse;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductAddOn;
import com.smart.restaurant_saas.menu.product.ProductAddOnRepository;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final ProductRepository productRepository;
    private final ProductAddOnRepository addOnRepository;

    /**
     * Builds the cashier projection with exactly two repository calls: one product catalog query
     * (including categories) and one add-on-link query. All nesting is then performed in memory.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> findMenu(Long tenantId) {
        List<Product> products = productRepository.findMenuCatalog(tenantId);
        List<ProductAddOn> addOnLinks =
            addOnRepository.findByTenantIdOrderByProductIdAscAddOnProductIdAsc(tenantId);

        Map<Long, Product> productsById = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, List<Product>> variantsByParent = products.stream()
            .filter(product -> product.getParentProductId() != null)
            .collect(Collectors.groupingBy(
                Product::getParentProductId,
                LinkedHashMap::new,
                Collectors.toList()));
        Map<Long, List<ProductAddOn>> addOnsByProduct = addOnLinks.stream()
            .collect(Collectors.groupingBy(
                ProductAddOn::getProductId,
                LinkedHashMap::new,
                Collectors.toList()));

        return products.stream()
            .filter(product -> product.getParentProductId() == null)
            .filter(product -> Boolean.TRUE.equals(product.getIsMenu()))
            .map(product -> toMenuItem(
                product,
                variantsByParent.getOrDefault(product.getId(), Collections.emptyList()),
                addOnsByProduct.getOrDefault(product.getId(), Collections.emptyList()),
                productsById))
            .toList();
    }

    private MenuItemResponse toMenuItem(Product product, List<Product> variants,
                                        List<ProductAddOn> addOnLinks,
                                        Map<Long, Product> productsById) {
        boolean parent = !variants.isEmpty();
        List<MenuVariantResponse> variantResponses = variants.stream()
            .map(this::toVariant)
            .toList();
        List<MenuAddOnResponse> addOnResponses = addOnLinks.stream()
            .map(link -> productsById.get(link.getAddOnProductId()))
            .filter(java.util.Objects::nonNull)
            .map(this::toAddOn)
            .toList();

        MenuItemResponse.MenuItemResponseBuilder response = MenuItemResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .type(parent ? MenuItemType.PARENT : MenuItemType.STANDALONE)
            .menuCategoryId(product.getMenuCategory().getId())
            .menuCategoryName(product.getMenuCategory().getName())
            .variants(variantResponses)
            .addOns(addOnResponses);

        if (parent) {
            response.minPrice(variants.stream()
                .map(Product::getSellingPrice)
                .min(BigDecimal::compareTo)
                .orElse(null));
            response.maxPrice(variants.stream()
                .map(Product::getSellingPrice)
                .max(BigDecimal::compareTo)
                .orElse(null));
        } else {
            response.sellingPrice(product.getSellingPrice());
        }
        return response.build();
    }

    private MenuVariantResponse toVariant(Product product) {
        return MenuVariantResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .variantLabel(product.getVariantLabel())
            .variantLabelAr(product.getVariantLabelAr())
            .sellingPrice(product.getSellingPrice())
            .build();
    }

    private MenuAddOnResponse toAddOn(Product product) {
        return MenuAddOnResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .sellingPrice(product.getSellingPrice())
            .build();
    }
}
