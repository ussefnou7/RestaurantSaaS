package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.category.MenuCategoryRepository;
import com.smart.restaurant_saas.menu.product.dto.ProductRequest;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MenuCategoryRepository categoryRepository;
    private final ProductMapper mapper;

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long tenantId, Long menuCategoryId) {
        List<Product> products = menuCategoryId == null
            ? productRepository.findByTenantIdOrderByNameAsc(tenantId)
            : productRepository.findByMenuCategoryId(menuCategoryId, tenantId);
        return products.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public ProductResponse create(ProductRequest request, Long tenantId, Long userId) {
        assertNameAvailable(request.getName(), tenantId);
        MenuCategory category = loadCategory(request.getMenuCategoryId(), tenantId);
        assertCategoryActive(category);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setCreatedBy(userId);
        product.setActive(true);
        applyFields(product, request, category);
        return mapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, Long tenantId, Long userId) {
        Product product = loadOwned(id, tenantId);
        if (!product.getName().equals(request.getName())) {
            assertNameAvailable(request.getName(), tenantId);
        }
        MenuCategory category = loadCategory(request.getMenuCategoryId(), tenantId);
        if (!product.getMenuCategory().getId().equals(category.getId())) {
            assertCategoryActive(category);
        }
        applyFields(product, request, category);
        product.setUpdatedBy(userId);
        return mapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse toggleActive(Long id, Long tenantId, Long userId) {
        Product product = loadOwned(id, tenantId);
        product.setActive(!product.isActive());
        product.setUpdatedBy(userId);
        return mapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        productRepository.delete(loadOwned(id, tenantId));
    }

    private void applyFields(Product product, ProductRequest request, MenuCategory category) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSellingPrice(request.getSellingPrice());
        product.setMenuCategory(category);
    }

    private void assertNameAvailable(String name, Long tenantId) {
        if (productRepository.existsByNameAndTenantId(name, tenantId)) {
            throw new BusinessException(MenuErrorCode.DUPLICATE_PRODUCT_NAME,
                "Product name already exists for tenant: " + name,
                ErrorParams.of("entityType", "Product", "name", name));
        }
    }

    private MenuCategory loadCategory(Long id, Long tenantId) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.CATEGORY_NOT_FOUND,
                "Menu category not found: " + id,
                ErrorParams.of("entityType", "MenuCategory", "entityId", id)));
    }

    private void assertCategoryActive(MenuCategory category) {
        if (!category.isActive()) {
            throw new BusinessException(MenuErrorCode.INACTIVE_CATEGORY,
                "Cannot assign a product to an inactive menu category: " + category.getId(),
                ErrorParams.of("categoryId", category.getId(), "categoryName", category.getName()));
        }
    }

    private Product loadOwned(Long id, Long tenantId) {
        return productRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
                "Product not found: " + id,
                ErrorParams.of("entityType", "Product", "entityId", id)));
    }
}
