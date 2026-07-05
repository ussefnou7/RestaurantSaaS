package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.mapper.UomMapper;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;

/**
 * Manages Units of Measure (UOMs).
 *
 * Two tiers exist:
 *   - Global UOMs (tenant_id = NULL): created by SysAdmin, visible to all tenants,
 *     never deleted (only deactivated).
 *   - Tenant UOMs (tenant_id = tenantId): created by a tenant for custom units,
 *     deletable when unused, otherwise deactivated.
 *
 * Conversions rely solely on {@code factorToBase}; no separate conversion table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UomService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final UomRepository uomRepository;
    private final UomMapper mapper;

    // =========================================================================
    // Queries
    // =========================================================================

    /** Global + tenant active UOMs, global first. */
    @Transactional(readOnly = true)
    public List<UomResponse> findAvailableForTenant(Long tenantId) {
        return uomRepository.findAvailableForTenant(tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    /** SysAdmin only — all global UOMs including inactive ones. */
    @Transactional(readOnly = true)
    public List<UomResponse> findAllGlobal() {
        return uomRepository.findByTenantIdIsNullOrderByNameAsc().stream()
            .map(mapper::toResponse)
            .toList();
    }

    // =========================================================================
    // Commands
    // =========================================================================

    /** Create a global UOM (tenant_id = NULL). */
    @Transactional
    public UomResponse createGlobal(UomRequest request) {
        if (uomRepository.existsByCodeAndTenantIdIsNull(request.getCode())) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_CODE,
                "A global UOM with code '" + request.getCode() + "' already exists",
                ErrorParams.of("entityType", "Uom", "code", request.getCode()));
        }
        Uom uom = buildUom(request, null);
        Uom saved = uomRepository.save(uom);
        log.info("Created global UOM id={} code={}", saved.getId(), saved.getCode());
        return mapper.toResponse(saved);
    }

    /** Create a tenant-owned UOM. */
    @Transactional
    public UomResponse createForTenant(UomRequest request, Long tenantId) {
        if (uomRepository.existsByCodeAndTenantId(request.getCode(), tenantId)) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_CODE,
                "A UOM with code '" + request.getCode() + "' already exists for this tenant",
                ErrorParams.of("entityType", "Uom", "code", request.getCode()));
        }
        Uom uom = buildUom(request, tenantId);
        Uom saved = uomRepository.save(uom);
        log.info("Created tenant UOM id={} code={} tenant={}",
            saved.getId(), saved.getCode(), tenantId);
        return mapper.toResponse(saved);
    }

    /**
     * Deactivate a UOM. Global UOMs require SysAdmin; tenant UOMs may only be
     * deactivated by their owning tenant. Historical data is never touched.
     */
    @Transactional
    public UomResponse deactivate(Long id, Long tenantId, boolean isSysAdmin) {
        Uom uom = loadUom(id);

        if (uom.getTenantId() == null) {
            if (!isSysAdmin) {
                throw new AccessDeniedException("Only SysAdmin can deactivate global UOMs");
            }
        } else if (!uom.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("You can only deactivate your own UOMs");
        }

        uom.setActive(false);
        Uom saved = uomRepository.save(uom);
        log.info("Deactivated UOM id={} code={}", saved.getId(), saved.getCode());
        return mapper.toResponse(saved);
    }

    /**
     * Permanently delete a tenant-owned UOM. Only allowed when the UOM is not
     * referenced by any material; otherwise the caller should deactivate instead.
     */
    @Transactional
    public void delete(Long id, Long tenantId) {
        Uom uom = loadUom(id);

        if (uom.getTenantId() == null) {
            throw new BusinessException(InventoryErrorCode.GLOBAL_UOM_NOT_DELETABLE,
                "Global UOMs cannot be deleted, only deactivated",
                ErrorParams.of("entityType", "Uom", "entityId", id));
        }
        if (!uom.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("You can only delete your own UOMs");
        }
        if (uomRepository.countMaterialsUsingUom(id) > 0) {
            throw new BusinessException(InventoryErrorCode.UOM_IN_USE,
                "UOM is in use and cannot be deleted",
                ErrorParams.of("entityType", "Uom", "entityId", id));
        }

        uomRepository.delete(uom);
        log.info("Deleted UOM id={} code={} tenant={}", id, uom.getCode(), tenantId);
    }

    /**
     * Convert a value between two UOMs of the same physical type.
     * result = value × from.factorToBase ÷ to.factorToBase
     */
    @Transactional(readOnly = true)
    public BigDecimal convertValue(BigDecimal value, Long fromUomId, Long toUomId) {
        Uom from = loadUom(fromUomId);
        Uom to = loadUom(toUomId);

        if (from.getType() != to.getType()) {
            throw UomConversionException.incompatibleTypes(
                from.getCode(), from.getType().name(),
                to.getCode(), to.getType().name());
        }

        return value.multiply(from.getFactorToBase())
            .divide(to.getFactorToBase(), SCALE, ROUNDING);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private Uom buildUom(UomRequest request, Long tenantId) {
        Uom uom = new Uom();
        uom.setTenantId(tenantId);
        uom.setCode(request.getCode());
        uom.setName(request.getName());
        uom.setNameAr(request.getNameAr());
        uom.setSymbol(request.getSymbol());
        uom.setType(request.getType());
        uom.setFactorToBase(request.getFactorToBase());
        uom.setActive(true);
        if (request.getBaseUom() != null) {
            uom.setBaseUom(loadUom(request.getBaseUom()));
        }
        return uom;
    }

    private Uom loadUom(Long id) {
        return uomRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "UOM not found: " + id,
                ErrorParams.of("entityType", "Uom", "entityId", id)));
    }
}
