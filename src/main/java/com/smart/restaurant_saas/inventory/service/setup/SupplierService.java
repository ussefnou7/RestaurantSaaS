package com.smart.restaurant_saas.inventory.service.setup;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.sequence.TenantSequenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.mapper.SupplierMapper;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.SupplierRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.SupplierResponse;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper mapper;
    private final TenantSequenceService tenantSequenceService;

    @Transactional(readOnly = true)
    public List<SupplierResponse> findAll(Long tenantId, String search, Boolean active) {
        return supplierRepository.findByFilters(tenantId, blankToNull(search), active)
            .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, Long tenantId) {
        Supplier s = new Supplier();
        s.setTenantId(tenantId);
        s.setCode(generateUniqueCode(tenantId));
        applyEditableFields(s, request);
        Supplier saved = supplierRepository.save(s);
        log.info("Created supplier id={} code={} tenant={}", saved.getId(), saved.getCode(), tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request, Long tenantId) {
        Supplier s = loadOwned(id, tenantId);
        applyEditableFields(s, request);
        return mapper.toResponse(supplierRepository.save(s));
    }

    @Transactional
    public SupplierResponse activate(Long id, Long tenantId) {
        return setActive(id, tenantId, true);
    }

    @Transactional
    public SupplierResponse deactivate(Long id, Long tenantId) {
        return setActive(id, tenantId, false);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private SupplierResponse setActive(Long id, Long tenantId, boolean active) {
        Supplier s = loadOwned(id, tenantId);
        s.setActive(active);
        return mapper.toResponse(supplierRepository.save(s));
    }

    private void applyEditableFields(Supplier s, SupplierRequest request) {
        s.setName(request.getName());
        s.setNameAr(request.getNameAr());
        s.setPhone(request.getPhone());
        s.setEmail(request.getEmail());
        s.setAddress(request.getAddress());
        s.setTaxNumber(request.getTaxNumber());
        s.setActive(request.getActive() == null || request.getActive());
        s.setNotes(request.getNotes());
    }

    private Supplier loadOwned(Long id, Long tenantId) {
        return supplierRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Supplier not found: " + id,
                ErrorParams.of("entityType", "Supplier", "entityId", id)));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String generateUniqueCode(Long tenantId) {
        String code;
        do {
            code = tenantSequenceService.generateEntityCode(tenantId, TenantEntityPrefix.SUP);
        } while (supplierRepository.existsByTenantIdAndCode(tenantId, code));
        return code;
    }
}
