package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
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
        // A null parent marks a calibration root. Roots come from migrations and
        // the SysAdmin panel; a tenant never creates one, so a missing parent
        // here is a malformed request rather than a root. Enforced in the
        // service, not as @NotNull on the DTO, because PanelUomController shares
        // that DTO and does need to create roots.
        if (request.getBaseUom() == null) {
            throw new ValidationException(InventoryErrorCode.UOM_BASE_REQUIRED,
                "A base UOM is required when creating a custom UOM",
                ErrorParams.of("entityType", "Uom", "code", request.getCode()));
        }
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

    /**
     * Builds a Uom with its four conversion values normalized onto the root of
     * the chain.
     *
     * All four — {@code baseUom}, {@code factorToBase}, {@code enteredFactor},
     * {@code enteredAgainstUom} — are computed here and nowhere else. A
     * divergence between the stored entered pair and factorToBase would be
     * worse than the bug this replaces: the screen would confidently display a
     * number the engine does not use.
     *
     * @param tenantId owning tenant, or null for a global (SysAdmin) UOM
     */
    private Uom buildUom(UomRequest request, Long tenantId) {
        Uom uom = new Uom();
        uom.setTenantId(tenantId);
        uom.setCode(request.getCode());
        uom.setName(request.getName());
        uom.setNameAr(request.getNameAr());
        uom.setSymbol(request.getSymbol());
        uom.setActive(true);

        if (request.getBaseUom() == null) {
            // A calibration root: no parent, and factorToBase is 1 by
            // definition. The request's factor is stored as-is rather than
            // forced to 1 so that a root claiming a real factor is rejected by
            // ck_uom_root_factor instead of being silently corrected. Only the
            // SysAdmin path reaches here; createForTenant rejects a null parent.
            uom.setType(request.getType());
            uom.setFactorToBase(request.getFactorToBase());
            uom.setEnteredFactor(request.getFactorToBase());
            return uom;
        }

        Uom parent = resolveParentUom(request.getBaseUom(), tenantId);

        // The parent is already root-calibrated, so its own root is this UOM's
        // root and one multiplication is the whole normalization. Picking a root
        // as the parent multiplies by 1 — the same path, not a special case.
        // The parent may itself be a tenant UOM: sack -> box -> kilogram is fine.
        Uom root = parent.getBaseUom() == null ? parent : parent.getBaseUom();

        // Derived from the parent, never read from the request: a UOM cannot be
        // a different physical type from the thing it is calibrated against. A
        // disagreeing request value is ignored rather than rejected.
        uom.setType(parent.getType());

        uom.setBaseUom(root);
        uom.setFactorToBase(
            request.getFactorToBase().multiply(parent.getFactorToBase()).setScale(SCALE, ROUNDING));
        uom.setEnteredFactor(request.getFactorToBase());
        uom.setEnteredAgainstUom(parent);
        return uom;
    }

    /**
     * Loads the parent UOM, rejecting one the tenant cannot see.
     *
     * A bare findById let tenant A reference tenant B's private UOM: the FK
     * persisted, but findAvailableForTenant never returns it, so the unit was
     * permanently unresolvable for its own owner. Mirrors
     * {@code MaterialService.resolveUom} and {@code RecipeService.loadVisibleUom}.
     *
     * Kept separate from {@link #loadUom} because that one also serves the
     * SysAdmin deactivate path, which passes a null tenantId and must still
     * load globals.
     */
    private Uom resolveParentUom(Long baseUomId, Long tenantId) {
        Uom parent = loadUom(baseUomId);
        if (parent.getTenantId() != null && !parent.getTenantId().equals(tenantId)) {
            throw new ValidationException(InventoryErrorCode.UOM_BASE_NOT_AVAILABLE,
                "Base UOM is not available to this tenant: " + baseUomId,
                ErrorParams.of("entityType", "Uom", "entityId", baseUomId));
        }
        return parent;
    }

    private Uom loadUom(Long id) {
        return uomRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "UOM not found: " + id,
                ErrorParams.of("entityType", "Uom", "entityId", id)));
    }
}
