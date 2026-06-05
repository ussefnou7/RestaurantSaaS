package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimRequired;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.request.CreateSupplierRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateSupplierRequest;
import com.smart.restaurant_saas.inventory.dto.response.SupplierResponse;
import com.smart.restaurant_saas.inventory.entity.Supplier;
import com.smart.restaurant_saas.inventory.mapper.SupplierMapper;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantCodeService.ValidatedCode;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantCodeService tenantCodeService;
    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers(String search, Boolean active) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return supplierRepository.findByTenantIdAndFilters(tenantId, searchPattern(search), active).stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return supplierMapper.toResponse(findSupplier(tenantId, id));
    }

    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.SUP
        );
        Long tenantId = validatedCode.tenantId();
        String code = validatedCode.code();
        if (supplierRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Supplier code already exists for tenant: " + code);
        }

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        applyCreateFields(supplier, request, code);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, UpdateSupplierRequest request) {
        ValidatedCode validatedCode = tenantCodeService.validateAndNormalizeCode(
                request.code(),
                TenantEntityPrefix.SUP
        );
        Long tenantId = validatedCode.tenantId();
        Supplier supplier = findSupplier(tenantId, id);
        String code = validatedCode.code();
        if (!supplier.getCode().equals(code)
                && supplierRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Supplier code already exists for tenant: " + code);
        }

        applyUpdateFields(supplier, request, code);

        return supplierMapper.toResponse(supplierRepository.saveAndFlush(supplier));
    }

    @Transactional
    public SupplierResponse activateSupplier(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Supplier supplier = findSupplier(tenantId, id);
        supplier.setActive(true);
        return supplierMapper.toResponse(supplierRepository.saveAndFlush(supplier));
    }

    @Transactional
    public SupplierResponse deactivateSupplier(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Supplier supplier = findSupplier(tenantId, id);
        supplier.setActive(false);
        return supplierMapper.toResponse(supplierRepository.saveAndFlush(supplier));
    }

    private Supplier findSupplier(Long tenantId, Long id) {
        return supplierRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Supplier not found: " + id));
    }

    private void applyCreateFields(Supplier supplier, CreateSupplierRequest request, String code) {
        supplier.setCode(code);
        supplier.setName(trimRequired(request.name(), "name"));
        supplier.setNameAr(trimToNull(request.nameAr()));
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setEmail(normalizeEmail(request.email()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setTaxNumber(trimToNull(request.taxNumber()));
        supplier.setActive(request.active() == null || request.active());
        supplier.setNotes(trimToNull(request.notes()));
    }

    private void applyUpdateFields(Supplier supplier, UpdateSupplierRequest request, String code) {
        supplier.setCode(code);
        supplier.setName(trimRequired(request.name(), "name"));
        supplier.setNameAr(trimToNull(request.nameAr()));
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setEmail(normalizeEmail(request.email()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setTaxNumber(trimToNull(request.taxNumber()));
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
        supplier.setNotes(trimToNull(request.notes()));
    }

    private String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
