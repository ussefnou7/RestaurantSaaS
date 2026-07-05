package com.smart.restaurant_saas.inventory.service.setup;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.mapper.WarehouseMapper;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.warehouse.dto.WarehouseRequest;
import com.smart.restaurant_saas.inventory.warehouse.dto.WarehouseResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BranchRepository branchRepository;
    private final WarehouseMapper mapper;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll(Long tenantId, String search, Long branchId,
                                           WarehouseType type, Boolean active) {
        return warehouseRepository.findByFilters(tenantId, blankToNull(search), branchId, type, active)
            .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request, Long tenantId) {
        if (warehouseRepository.existsByTenantIdAndCode(tenantId, request.getCode())) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_CODE,
                "A warehouse with code '" + request.getCode() + "' already exists",
                ErrorParams.of("entityType", "Warehouse", "code", request.getCode()));
        }
        Warehouse w = new Warehouse();
        w.setTenantId(tenantId);
        w.setCode(request.getCode());
        applyEditableFields(w, request, tenantId);
        Warehouse saved = warehouseRepository.save(w);
        log.info("Created warehouse id={} code={} tenant={}", saved.getId(), saved.getCode(), tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request, Long tenantId) {
        Warehouse w = loadOwned(id, tenantId);
        if (!w.getCode().equals(request.getCode())) {
            throw new BusinessException(InventoryErrorCode.CODE_IMMUTABLE,
                "Code cannot be changed",
                ErrorParams.of("entityType", "Warehouse"));
        }
        applyEditableFields(w, request, tenantId);
        return mapper.toResponse(warehouseRepository.save(w));
    }

    @Transactional
    public WarehouseResponse activate(Long id, Long tenantId) {
        return setActive(id, tenantId, true);
    }

    @Transactional
    public WarehouseResponse deactivate(Long id, Long tenantId) {
        return setActive(id, tenantId, false);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private WarehouseResponse setActive(Long id, Long tenantId, boolean active) {
        Warehouse w = loadOwned(id, tenantId);
        w.setActive(active);
        return mapper.toResponse(warehouseRepository.save(w));
    }

    private void applyEditableFields(Warehouse w, WarehouseRequest request, Long tenantId) {
        w.setName(request.getName());
        w.setNameAr(request.getNameAr());
        w.setType(request.getType());
        w.setActive(request.getActive() == null || request.getActive());
        w.setNotes(request.getNotes());
        w.setBranch(resolveBranch(request.getBranchId(), tenantId));
    }

    private Branch resolveBranch(Long branchId, Long tenantId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Branch not found: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private Warehouse loadOwned(Long id, Long tenantId) {
        return warehouseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + id,
                ErrorParams.of("entityType", "Warehouse", "entityId", id)));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
