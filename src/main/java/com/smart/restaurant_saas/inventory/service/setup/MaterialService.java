package com.smart.restaurant_saas.inventory.service.setup;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.mapper.MaterialMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.material.dto.MaterialRequest;
import com.smart.restaurant_saas.inventory.material.dto.MaterialResponse;
import com.smart.restaurant_saas.inventory.repository.MaterialCategoryRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialCategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final MaterialMapper mapper;
    private final TenantSequenceService tenantSequenceService;

    @Transactional(readOnly = true)
    public List<MaterialResponse> findAll(Long tenantId, String search, Long categoryId,
                                          Long defaultUomId, Boolean active) {
        return materialRepository.findByFilters(tenantId, blankToNull(search), categoryId, defaultUomId, active)
            .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MaterialResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public MaterialResponse create(MaterialRequest request, Long tenantId) {
        Material m = new Material();
        m.setTenantId(tenantId);
        m.setCode(generateUniqueCode(tenantId));
        applyEditableFields(m, request, tenantId);
        Material saved = materialRepository.save(m);
        log.info("Created material id={} code={} tenant={}", saved.getId(), saved.getCode(), tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest request, Long tenantId) {
        Material m = loadOwned(id, tenantId);
        applyEditableFields(m, request, tenantId);
        return mapper.toResponse(materialRepository.save(m));
    }

    @Transactional
    public MaterialResponse activate(Long id, Long tenantId) {
        return setActive(id, tenantId, true);
    }

    @Transactional
    public MaterialResponse deactivate(Long id, Long tenantId) {
        return setActive(id, tenantId, false);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private MaterialResponse setActive(Long id, Long tenantId, boolean active) {
        Material m = loadOwned(id, tenantId);
        m.setActive(active);
        return mapper.toResponse(materialRepository.save(m));
    }

    private void applyEditableFields(Material m, MaterialRequest request, Long tenantId) {
        m.setName(request.getName());
        m.setNameAr(request.getNameAr());
        m.setCategory(resolveCategory(request.getCategoryId(), tenantId));
        // stockUom is the base/default unit; displayUom is what users see.
        m.setStockUom(resolveUom(request.getStockUomId(), tenantId));
        m.setDisplayUom(resolveUom(request.getDisplayUomId(), tenantId));
        m.setActive(request.getActive() == null || request.getActive());
        m.setNotes(request.getNotes());
    }

    private MaterialCategory resolveCategory(Long categoryId, Long tenantId) {
        MaterialCategory c = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material category not found: " + categoryId,
                ErrorParams.of("entityType", "MaterialCategory", "entityId", categoryId)));
        if (c.getTenantId() != null && !c.getTenantId().equals(tenantId)) {
            throw new ValidationException(InventoryErrorCode.RESOURCE_NOT_AVAILABLE_FOR_TENANT,
                "Category is not available to this tenant: " + categoryId,
                ErrorParams.of("entityType", "MaterialCategory", "entityId", categoryId));
        }
        return c;
    }

    private Uom resolveUom(Long uomId, Long tenantId) {
        Uom uom = uomRepository.findById(uomId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "UOM not found: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId)));
        if (uom.getTenantId() != null && !uom.getTenantId().equals(tenantId)) {
            throw new ValidationException(InventoryErrorCode.RESOURCE_NOT_AVAILABLE_FOR_TENANT,
                "UOM is not available to this tenant: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId));
        }
        return uom;
    }

    private Material loadOwned(Long id, Long tenantId) {
        return materialRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material not found: " + id,
                ErrorParams.of("entityType", "Material", "entityId", id)));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String generateUniqueCode(Long tenantId) {
        String code;
        do {
            code = tenantSequenceService.generateEntityCode(tenantId, TenantEntityPrefix.MAT);
        } while (materialRepository.existsByTenantIdAndCode(tenantId, code));
        return code;
    }
}
