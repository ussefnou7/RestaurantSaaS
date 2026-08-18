package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.category.MenuCategory;
import com.smart.restaurant_saas.menu.category.MenuCategoryRepository;
import com.smart.restaurant_saas.menu.product.dto.ProductRequest;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import com.smart.restaurant_saas.menu.recipe.RecipeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MenuCategoryRepository categoryRepository;
    private final RecipeRepository recipeRepository;
    private final ProductMapper mapper;

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long tenantId, Long menuCategoryId,
                                         boolean parentEligible, Long excludeProductId) {
        List<Product> products;
        if (parentEligible) {
            products = productRepository.findParentEligible(tenantId, excludeProductId);
        } else {
            products = menuCategoryId == null
                ? productRepository.findByTenantIdOrderByNameAsc(tenantId)
                : productRepository.findByMenuCategoryId(menuCategoryId, tenantId);
        }
        // Derived parenthood is computed in-memory over the loaded list to avoid N+1.
        return mapper.toResponseList(products);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id, Long tenantId) {
        Product product = loadOwned(id, tenantId);
        return mapper.toResponse(product,
            productRepository.existsByParentProductIdAndTenantId(product.getId(), tenantId));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findVariants(Long parentProductId, Long tenantId) {
        loadOwned(parentProductId, tenantId);
        return mapper.toResponseList(
            productRepository.findByParentProductIdAndTenantId(parentProductId, tenantId));
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
        applyFields(product, request, category, tenantId);
        Product saved = productRepository.save(product);
        return mapper.toResponse(saved,
            productRepository.existsByParentProductIdAndTenantId(saved.getId(), tenantId));
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
        applyFields(product, request, category, tenantId);
        product.setUpdatedBy(userId);
        Product saved = productRepository.save(product);
        return mapper.toResponse(saved,
            productRepository.existsByParentProductIdAndTenantId(saved.getId(), tenantId));
    }

    @Transactional
    public ProductResponse toggleActive(Long id, Long tenantId, Long userId) {
        Product product = loadOwned(id, tenantId);
        product.setActive(!product.isActive());
        product.setUpdatedBy(userId);
        Product saved = productRepository.save(product);
        return mapper.toResponse(saved,
            productRepository.existsByParentProductIdAndTenantId(saved.getId(), tenantId));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        deleteProduct(tenantId, id);
    }

    @Transactional
    public void deleteProduct(Long tenantId, Long productId) {
        Product product = loadOwned(productId, tenantId);
        if (productRepository.existsByParentProductIdAndTenantId(productId, tenantId)) {
            throw new BusinessException(MenuErrorCode.PRODUCT_HAS_VARIANTS,
                "Cannot delete a parent product while variant children exist. Unlink all variants first.",
                ErrorParams.of("productId", productId));
        }
        productRepository.delete(product);
    }

    private void applyFields(Product product, ProductRequest request, MenuCategory category,
                             Long tenantId) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setDescriptionAr(request.getDescriptionAr());
        product.setSellingPrice(request.getSellingPrice());
        product.setMenuCategory(category);
        applyParentAndMenu(product, request, tenantId);
    }

    /**
     * Resolves the variant/menu relationship and enforces the hard invariants:
     * a variant child (parentProductId != null) is never a menu item, and a product that
     * already carries an active recipe cannot become a parent shell.
     */
    private void applyParentAndMenu(Product product, ProductRequest request, Long tenantId) {
        Long parentProductId = request.getParentProductId();
        if (parentProductId == null) {
            product.setParentProductId(null);
            product.setVariantLabel(null);
            product.setVariantLabelAr(null);
            // Standalone/parent products default to menu-visible when unspecified.
            product.setIsMenu(request.getIsMenu() == null ? Boolean.TRUE : request.getIsMenu());
            return;
        }

        String variantLabel = normalizedLabel(request.getVariantLabel());
        String variantLabelAr = normalizedLabel(request.getVariantLabelAr());
        if (variantLabel == null || variantLabelAr == null) {
            throw new BusinessException(MenuErrorCode.VARIANT_LABEL_REQUIRED,
                "Both variant labels are required when parentProductId is set: " + parentProductId,
                ErrorParams.of("parentProductId", parentProductId));
        }

        if (Boolean.TRUE.equals(request.getIsMenu())) {
            throw new BusinessException(MenuErrorCode.VARIANT_CANNOT_BE_MENU_ITEM,
                "A variant child cannot be a menu item: parentProductId=" + parentProductId,
                ErrorParams.of("parentProductId", parentProductId));
        }
        if (product.getId() != null && product.getId().equals(parentProductId)) {
            throw new BusinessException(MenuErrorCode.PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT,
                "A product cannot be its own parent: " + parentProductId,
                ErrorParams.of("productId", product.getId(), "parentProductId", parentProductId));
        }

        // Parent must exist within the tenant and must not already carry an active recipe:
        // a product with a recipe cannot become a parent shell.
        loadOwned(parentProductId, tenantId);
        if (recipeRepository.findByProductIdAndTenantIdAndActiveTrue(parentProductId, tenantId).isPresent()) {
            throw new BusinessException(MenuErrorCode.PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT,
                "Parent product already has an active recipe: " + parentProductId,
                ErrorParams.of("parentProductId", parentProductId));
        }
        if (productRepository.existsSiblingWithVariantLabel(
                tenantId, parentProductId, variantLabel, variantLabelAr, product.getId())) {
            throw new BusinessException(MenuErrorCode.DUPLICATE_VARIANT_LABEL,
                "A sibling variant already uses one of the requested labels: " + parentProductId,
                ErrorParams.of(
                    "parentProductId", parentProductId,
                    "variantLabel", variantLabel,
                    "variantLabelAr", variantLabelAr));
        }

        product.setParentProductId(parentProductId);
        product.setVariantLabel(variantLabel);
        product.setVariantLabelAr(variantLabelAr);
        // If parentProductId != null, the correct value is always false.
        product.setIsMenu(Boolean.FALSE);
    }

    /**
     * Guards that a product may be turned into an order line. A parent/variant-group shell groups
     * variants and is never orderable directly — the caller must pick a specific variant instead.
     *
     * <p>TODO(order): wire this into the order-line build path when the Order module consumes menu
     * products. It lives here (not in the Order module) so this task does not modify OrderLine.
     */
    @Transactional(readOnly = true)
    public void assertOrderable(Long productId, Long tenantId) {
        loadOwned(productId, tenantId);
        if (productRepository.existsByParentProductIdAndTenantId(productId, tenantId)) {
            throw new BusinessException(MenuErrorCode.PARENT_PRODUCT_NOT_ORDERABLE,
                "Variant-group product is not orderable directly: " + productId,
                ErrorParams.of("productId", productId));
        }
    }

    private void assertNameAvailable(String name, Long tenantId) {
        if (productRepository.existsByNameAndTenantId(name, tenantId)) {
            throw new BusinessException(MenuErrorCode.DUPLICATE_PRODUCT_NAME,
                "Product name already exists for tenant: " + name,
                ErrorParams.of("entityType", "Product", "name", name));
        }
    }

    private String normalizedLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
