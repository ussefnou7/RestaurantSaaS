package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.normalizeCode;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.mapper.MaterialCategoryMapper;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final CurrentTenantProvider currentTenantProvider;
    private final MaterialCategoryRepository categoryRepository;
    private final MaterialCategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<MaterialCategoryResponse> listCategories(String search, Boolean active) {
        return categoryRepository.findByFilters(searchPattern(search), active).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MaterialCategoryResponse> listAccessibleCategories(String search, Boolean active) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return categoryRepository.findAccessibleByFilters(tenantId, searchPattern(search), active).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialCategoryResponse getCategory(Long id) {
        return categoryMapper.toResponse(findCategory(id));
    }

    @Transactional(readOnly = true)
    public MaterialCategoryResponse getAccessibleCategory(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return categoryMapper.toResponse(findAccessibleCategory(tenantId, id));
    }

    @Transactional
    public MaterialCategoryResponse createCategory(CreateMaterialCategoryRequest request) {
        String code = normalizeCode(request.code(), "code");
        if (categoryRepository.existsByTenantIdIsNullAndCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material category code already exists: " + code);
        }

        MaterialCategory category = new MaterialCategory();
        applyCreateFields(category, request, code);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public MaterialCategoryResponse createTenantCategory(CreateMaterialCategoryRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        String code = normalizeCode(request.code(), "code");
        if (categoryRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material category code already exists for tenant: " + code);
        }

        MaterialCategory category = new MaterialCategory();
        applyCreateFields(category, request, code);
        category.setTenantId(tenantId);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public MaterialCategoryResponse updateCategory(Long id, UpdateMaterialCategoryRequest request) {
        MaterialCategory category = findCategory(id);
        String code = normalizeCode(request.code(), "code");
        if (!category.getCode().equals(code)
                && categoryRepository.existsByTenantIdIsNullAndCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material category code already exists: " + code);
        }

        applyUpdateFields(category, request, code);

        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public MaterialCategoryResponse updateTenantCategory(Long id, UpdateMaterialCategoryRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        MaterialCategory category = findTenantCategory(tenantId, id);
        String code = normalizeCode(request.code(), "code");
        if (!category.getCode().equals(code)
                && categoryRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Material category code already exists for tenant: " + code);
        }

        applyUpdateFields(category, request, code);

        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public MaterialCategoryResponse activateCategory(Long id) {
        MaterialCategory category = findCategory(id);
        category.setActive(true);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public MaterialCategoryResponse activateTenantCategory(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        MaterialCategory category = findTenantCategory(tenantId, id);
        category.setActive(true);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public MaterialCategoryResponse deactivateCategory(Long id) {
        MaterialCategory category = findCategory(id);
        category.setActive(false);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public MaterialCategoryResponse deactivateTenantCategory(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        MaterialCategory category = findTenantCategory(tenantId, id);
        category.setActive(false);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    MaterialCategory findCategory(Long id) {
        return categoryRepository.findByIdAndTenantIdIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material category not found: " + id));
    }

    private MaterialCategory findAccessibleCategory(Long tenantId, Long id) {
        return categoryRepository.findAccessibleById(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material category not found: " + id));
    }

    private MaterialCategory findTenantCategory(Long tenantId, Long id) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant material category not found: " + id));
    }

    private void applyCreateFields(
            MaterialCategory category,
            CreateMaterialCategoryRequest request,
            String code
    ) {
        category.setTenantId(null);
        category.setCode(code);
        category.setName(trimRequired(request.name(), "name"));
        category.setNameAr(trimToNull(request.nameAr()));
        category.setActive(request.active() == null || request.active());
        category.setSortOrder(request.sortOrder());
    }

    private void applyUpdateFields(
            MaterialCategory category,
            UpdateMaterialCategoryRequest request,
            String code
    ) {
        category.setCode(code);
        category.setName(trimRequired(request.name(), "name"));
        category.setNameAr(trimToNull(request.nameAr()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        category.setSortOrder(request.sortOrder());
    }
}
