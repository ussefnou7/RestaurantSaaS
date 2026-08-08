package com.smart.restaurant_saas.inventory.service.setup;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.category.dto.MaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.category.dto.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.mapper.MaterialCategoryMapper;
import com.smart.restaurant_saas.inventory.repository.GlobalMaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final MaterialCategoryRepository categoryRepository;
    private final GlobalMaterialCategoryRepository globalCategoryRepository;
    private final MaterialCategoryMapper mapper;
    private final TenantSequenceService tenantSequenceService;

    @Transactional(readOnly = true)
    public List<MaterialCategoryResponse> findAll(Long tenantId, String search, Boolean active) {
        return categoryRepository.findByFilters(tenantId, blankToNull(search), active)
            .stream().map(mapper::toResponse).toList();
    }

    /** Global (system-level) categories only — for the catalog import filter dropdown. */
    @Transactional(readOnly = true)
    public List<MaterialCategoryResponse> findAllGlobal(Boolean active) {
        List<MaterialCategory> categories = active == null
            ? globalCategoryRepository.findByTenantIdIsNullOrderBySortOrderAscNameAsc()
            : globalCategoryRepository.findByTenantIdIsNullAndActiveOrderBySortOrderAscNameAsc(active);
        return categories.stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public MaterialCategoryResponse create(MaterialCategoryRequest request, Long tenantId) {
        MaterialCategory c = new MaterialCategory();
        c.setTenantId(tenantId);
        c.setCode(generateUniqueCode(tenantId));
        c.setName(request.getName());
        c.setNameAr(request.getNameAr());
        c.setActive(request.getActive() == null || request.getActive());
        c.setSortOrder(request.getSortOrder());
        MaterialCategory saved = categoryRepository.save(c);
        log.info("Created material category id={} code={} tenant={}", saved.getId(), saved.getCode(), tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public MaterialCategoryResponse update(Long id, MaterialCategoryRequest request, Long tenantId) {
        MaterialCategory c = loadTenantOwned(id, tenantId);
        c.setName(request.getName());
        c.setNameAr(request.getNameAr());
        c.setActive(request.getActive() == null || request.getActive());
        c.setSortOrder(request.getSortOrder());
        return mapper.toResponse(categoryRepository.save(c));
    }

    @Transactional
    public MaterialCategoryResponse activate(Long id, Long tenantId) {
        return setActive(id, tenantId, true);
    }

    @Transactional
    public MaterialCategoryResponse deactivate(Long id, Long tenantId) {
        return setActive(id, tenantId, false);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private MaterialCategoryResponse setActive(Long id, Long tenantId, boolean active) {
        MaterialCategory c = loadTenantOwned(id, tenantId);
        c.setActive(active);
        return mapper.toResponse(categoryRepository.save(c));
    }

    /**
     * Loads a tenant-owned category. Global categories (tenantId IS NULL) and
     * categories owned by other tenants are rejected with 403.
     */
    private MaterialCategory loadTenantOwned(Long id, Long tenantId) {
        MaterialCategory c = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material category not found: " + id,
                ErrorParams.of("entityType", "MaterialCategory", "entityId", id)));
        if (c.getTenantId() == null) {
            throw new AccessDeniedException("Global categories are read-only");
        }
        if (!c.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("You can only modify your own categories");
        }
        return c;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String generateUniqueCode(Long tenantId) {
        String code;
        do {
            code = tenantSequenceService.generateEntityCode(tenantId, TenantEntityPrefix.CAT);
        } while (categoryRepository.existsByTenantIdAndCode(tenantId, code));
        return code;
    }
}
