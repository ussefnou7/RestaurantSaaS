package com.smart.restaurant_saas.menu.category;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
import com.smart.restaurant_saas.menu.category.dto.MenuCategoryRequest;
import com.smart.restaurant_saas.menu.category.dto.MenuCategoryResponse;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuCategoryService {

    private final MenuCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MenuCategoryMapper mapper;

    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> findAll(Long tenantId) {
        return categoryRepository.findByTenantIdOrderBySortOrderAscIdAsc(tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public MenuCategoryResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public MenuCategoryResponse create(MenuCategoryRequest request, Long tenantId, Long userId) {
        MenuCategory category = new MenuCategory();
        category.setTenantId(tenantId);
        category.setCreatedBy(userId);
        applyFields(category, request);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public MenuCategoryResponse update(Long id, MenuCategoryRequest request,
                                       Long tenantId, Long userId) {
        MenuCategory category = loadOwned(id, tenantId);
        applyFields(category, request);
        category.setUpdatedBy(userId);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        MenuCategory category = loadOwned(id, tenantId);
        if (productRepository.existsByMenuCategoryIdAndTenantId(id, tenantId)) {
            throw new BusinessException(MenuErrorCode.CATEGORY_HAS_PRODUCTS,
                "Menu category is linked to products: " + id,
                ErrorParams.of("categoryId", id, "categoryName", category.getName()));
        }
        categoryRepository.delete(category);
    }

    private void applyFields(MenuCategory category, MenuCategoryRequest request) {
        category.setName(request.getName());
        category.setSortOrder(request.getSortOrder());
        category.setActive(request.getActive());
    }

    private MenuCategory loadOwned(Long id, Long tenantId) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(MenuErrorCode.CATEGORY_NOT_FOUND,
                "Menu category not found: " + id,
                ErrorParams.of("entityType", "MenuCategory", "entityId", id)));
    }
}
