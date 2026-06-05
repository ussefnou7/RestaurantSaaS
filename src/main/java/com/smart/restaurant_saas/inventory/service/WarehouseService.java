package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateWarehouseRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateWarehouseRequest;
import com.smart.restaurant_saas.inventory.dto.response.WarehouseResponse;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.mapper.WarehouseMapper;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantCodeService.ValidatedCode;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantCodeService tenantCodeService;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses(
            String search,
            Long branchId,
            String type,
            Boolean active
    ) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        WarehouseType warehouseType = parseType(type);
        return warehouseRepository.findByTenantIdAndFilters(
                        tenantId,
                        searchPattern(search),
                        branchId,
                        warehouseType,
                        active
                ).stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouse(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return warehouseMapper.toResponse(findWarehouse(tenantId, id));
    }

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.WH
        );
        Long tenantId = validatedCode.tenantId();
        String code = validatedCode.code();
        if (warehouseRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Warehouse code already exists for tenant: " + code);
        }

        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        applyCreateFields(tenantId, warehouse, request, code);

        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.WH
        );
        Long tenantId = validatedCode.tenantId();
        Warehouse warehouse = findWarehouse(tenantId, id);
        String code = validatedCode.code();
        if (!warehouse.getCode().equals(code)
                && warehouseRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Warehouse code already exists for tenant: " + code);
        }

        applyUpdateFields(tenantId, warehouse, request, code);

        return warehouseMapper.toResponse(warehouseRepository.saveAndFlush(warehouse));
    }

    @Transactional
    public WarehouseResponse activateWarehouse(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Warehouse warehouse = findWarehouse(tenantId, id);
        warehouse.setActive(true);
        return warehouseMapper.toResponse(warehouseRepository.saveAndFlush(warehouse));
    }

    @Transactional
    public WarehouseResponse deactivateWarehouse(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Warehouse warehouse = findWarehouse(tenantId, id);
        warehouse.setActive(false);
        return warehouseMapper.toResponse(warehouseRepository.saveAndFlush(warehouse));
    }

    private Warehouse findWarehouse(Long tenantId, Long id) {
        return warehouseRepository.findDetailedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Warehouse not found: " + id));
    }

    private void applyCreateFields(
            Long tenantId,
            Warehouse warehouse,
            CreateWarehouseRequest request,
            String code
    ) {
        warehouse.setBranch(findBranch(tenantId, request.branchId()));
        warehouse.setCode(code);
        warehouse.setName(trimRequired(request.name(), "name"));
        warehouse.setNameAr(trimToNull(request.nameAr()));
        warehouse.setType(request.type());
        warehouse.setActive(request.active() == null || request.active());
        warehouse.setNotes(trimToNull(request.notes()));
    }

    private void applyUpdateFields(
            Long tenantId,
            Warehouse warehouse,
            UpdateWarehouseRequest request,
            String code
    ) {
        warehouse.setBranch(findBranch(tenantId, request.branchId()));
        warehouse.setCode(code);
        warehouse.setName(trimRequired(request.name(), "name"));
        warehouse.setNameAr(trimToNull(request.nameAr()));
        warehouse.setType(request.type());
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }
        warehouse.setNotes(trimToNull(request.notes()));
    }

    private Branch findBranch(Long tenantId, Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid branch: " + branchId));
    }

    private WarehouseType parseType(String type) {
        String normalizedType = trimToNull(type);
        if (normalizedType == null) {
            return null;
        }
        try {
            return WarehouseType.valueOf(normalizedType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid warehouse type: " + type
                    + ". Allowed values: " + Arrays.toString(WarehouseType.values()));
        }
    }
}
