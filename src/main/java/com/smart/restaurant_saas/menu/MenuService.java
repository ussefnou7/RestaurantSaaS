package com.smart.restaurant_saas.menu;

import com.smart.restaurant_saas.media.MediaService;
import com.smart.restaurant_saas.media.dto.MediaSummaryResponse;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
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
    private final MediaService mediaService;

    /**
     * Builds the cashier projection with exactly four repository calls, none of them per product:
     * one product catalog query (including categories), one add-on-link query, and the two
     * {@link MediaService#summariesForOwners} performs. All nesting is then done in memory.
     *
     * <p>Images are resolved for <em>every</em> product in the catalog, variants included, in that
     * same pair of queries — a variant is a product row and can carry its own image. Add-ons
     * deliberately carry none: they render as a name and a price beside an item, not as a tile.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> findMenu(Long tenantId) {
        return findMenu(tenantId, false);
    }

    /**
     * @param inlineImages embeds each image's {@code THUMB} bytes in the response rather than only
     *     its url. For the POS, which authenticates with a bearer token and so cannot load a
     *     permission-gated {@code <img src>} at all — the bytes have to arrive in a call it makes
     *     itself. It also leaves the terminal holding the whole catalog after one request, with
     *     nothing further to fetch per tile. {@code THUMB} is 200px on its longest edge; larger
     *     renditions must not be inlined, as base64 adds a third again to every row.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> findMenu(Long tenantId, boolean inlineImages) {
        List<Product> products = productRepository.findMenuCatalog(tenantId);
        List<ProductAddOn> addOnLinks =
            addOnRepository.findByTenantIdOrderByProductIdAscAddOnProductIdAsc(tenantId);
        Map<Long, MediaSummaryResponse> imagesByProduct = mediaService.summariesForOwners(
            tenantId, MediaPurpose.PRODUCT_IMAGE, products.stream().map(Product::getId).toList(),
            inlineImages ? MediaVariantType.THUMB : null);

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
                productsById,
                imagesByProduct))
            .toList();
    }

    private MenuItemResponse toMenuItem(Product product, List<Product> variants,
                                        List<ProductAddOn> addOnLinks,
                                        Map<Long, Product> productsById,
                                        Map<Long, MediaSummaryResponse> imagesByProduct) {
        boolean parent = !variants.isEmpty();
        List<MenuVariantResponse> variantResponses = variants.stream()
            .map(variant -> toVariant(variant, imagesByProduct.get(variant.getId())))
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
            .menuCategoryNameAr(product.getMenuCategory().getNameAr())
            .image(imagesByProduct.get(product.getId()))
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

    private MenuVariantResponse toVariant(Product product, MediaSummaryResponse image) {
        return MenuVariantResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .variantLabel(product.getVariantLabel())
            .variantLabelAr(product.getVariantLabelAr())
            .sellingPrice(product.getSellingPrice())
            .image(image)
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
