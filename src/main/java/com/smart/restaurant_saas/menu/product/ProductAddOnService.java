package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.product.dto.ProductAddOnResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductAddOnService {

    private final ProductAddOnRepository addOnRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductAddOnResponse> findByProduct(Long productId, Long tenantId) {
        loadOwned(productId, tenantId);
        List<ProductAddOn> links = addOnRepository.findByTenantIdAndProductId(tenantId, productId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> addOnProducts = productRepository
            .findAllById(links.stream().map(ProductAddOn::getAddOnProductId).toList())
            .stream()
            .filter(p -> tenantId.equals(p.getTenantId()))
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        return links.stream().map(link -> toResponse(link, addOnProducts.get(link.getAddOnProductId()))).toList();
    }

    @Transactional
    public ProductAddOnResponse create(Long productId, Long addOnProductId, Long tenantId, Long userId) {
        Product host = loadOwned(productId, tenantId);
        if (productId.equals(addOnProductId)) {
            throw new BusinessException(MenuErrorCode.ADDON_CANNOT_BE_SELF,
                "A product cannot be its own add-on: " + productId,
                ErrorParams.of("productId", productId));
        }
        // Add-ons attach to parent-eligible hosts only — a variant child cannot host add-ons.
        if (host.getParentProductId() != null) {
            throw new BusinessException(MenuErrorCode.ADDON_HOST_MUST_BE_PARENT_ELIGIBLE,
                "Add-on host must be a parent-eligible (top-level) product: " + productId,
                ErrorParams.of("productId", productId, "parentProductId", host.getParentProductId()));
        }
        Product addOn = loadOwned(addOnProductId, tenantId);
        if (addOnRepository.existsByTenantIdAndProductIdAndAddOnProductId(tenantId, productId, addOnProductId)) {
            throw new BusinessException(MenuErrorCode.DUPLICATE_ADD_ON,
                "Add-on link already exists: " + productId + " -> " + addOnProductId,
                ErrorParams.of("productId", productId, "addOnProductId", addOnProductId));
        }

        ProductAddOn link = new ProductAddOn();
        link.setTenantId(tenantId);
        link.setProductId(productId);
        link.setAddOnProductId(addOnProductId);
        link.setCreatedBy(userId);
        return toResponse(addOnRepository.save(link), addOn);
    }

    @Transactional
    public void delete(Long productId, Long addOnProductId, Long tenantId) {
        loadOwned(productId, tenantId);
        ProductAddOn link = addOnRepository
            .findByTenantIdAndProductIdAndAddOnProductId(tenantId, productId, addOnProductId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Add-on link not found: " + productId + " -> " + addOnProductId,
                ErrorParams.of("productId", productId, "addOnProductId", addOnProductId)));
        addOnRepository.delete(link);
    }

    private Product loadOwned(Long id, Long tenantId) {
        return productRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + id,
                ErrorParams.of("entityType", "Product", "entityId", id)));
    }

    private ProductAddOnResponse toResponse(ProductAddOn link, Product addOnProduct) {
        return ProductAddOnResponse.builder()
            .id(link.getId())
            .productId(link.getProductId())
            .addOnProductId(link.getAddOnProductId())
            .addOnProductName(addOnProduct == null ? null : addOnProduct.getName())
            .addOnSellingPrice(addOnProduct == null ? null : addOnProduct.getSellingPrice())
            .build();
    }
}
