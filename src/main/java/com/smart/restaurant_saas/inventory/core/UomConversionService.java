package com.smart.restaurant_saas.inventory.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;

/**
 * Converts quantities between units of measure.
 *
 * Resolution order:
 *   1. Identity (from == to)
 *   2. Same base uom (physical conversion via factorToBase)
 *
 * Cross-type conversions (different base uom) are not supported and raise a
 * {@link UomConversionException}. UOMs are managed through the Admin Panel.
 *
 * All math uses BigDecimal with scale=6, RoundingMode.HALF_UP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UomConversionService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Convert a quantity from one Uom to another.
     *
     * @param quantity the value to convert
     * @param fromUom  source Uom
     * @param toUom    target Uom
     * @param material optional material context (used for error reporting)
     * @param tenantId tenant scope
     * @return the converted quantity (scale=6)
     * @throws UomConversionException when no conversion path can be resolved
     */
    public BigDecimal convert(BigDecimal quantity, Uom fromUom, Uom toUom,
                              Material material, Long tenantId) {

        if (quantity == null) {
            throw new UomConversionException("Quantity must not be null");
        }
        if (fromUom == null || toUom == null) {
            throw new UomConversionException("Both fromUom and toUom must be non-null");
        }

        // Case 1: identity
        if (fromUom.getId().equals(toUom.getId())) {
            return scale(quantity);
        }

        // Case 2: same physical type (same base uom)
        if (sameBaseUom(fromUom, toUom)) {
            return physicalConvert(quantity, fromUom, toUom);
        }

        // Cross-type conversions are not supported.
        if (material == null) {
            throw UomConversionException.missingMaterialContext(
                fromUom.getCode(), toUom.getCode());
        }
        throw UomConversionException.noConversionFound(
            fromUom.getCode(), toUom.getCode(), material.getCode());
    }

    /**
     * Convert a quantity from any Uom to the material's stock Uom.
     * This is the entry point used by the ledger.
     */
    public BigDecimal convertToStockUom(BigDecimal quantity, Uom fromUom,
                                         Material material, Long tenantId) {
        return convert(quantity, fromUom, material.getStockUom(), material, tenantId);
    }

    /**
     * Check whether a conversion path exists.
     */
    public boolean areConvertible(Uom fromUom, Uom toUom, Material material, Long tenantId) {
        try {
            convert(BigDecimal.ONE, fromUom, toUom, material, tenantId);
            return true;
        } catch (UomConversionException ex) {
            return false;
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private boolean sameBaseUom(Uom a, Uom b) {
        Long aBase = baseUomId(a);
        Long bBase = baseUomId(b);
        return aBase != null && aBase.equals(bBase);
    }

    /** Returns the id of the base uom for `u` — which is `u.id` itself if `u` has no parent. */
    private Long baseUomId(Uom u) {
        return u.getBaseUom() == null ? u.getId() : u.getBaseUom().getId();
    }

    /** Physical conversion via factorToBase. Caller must verify same base. */
    private BigDecimal physicalConvert(BigDecimal quantity, Uom from, Uom to) {
        // quantity (in from) -> base = quantity * from.factorToBase
        // base -> to = base / to.factorToBase
        BigDecimal inBase = quantity.multiply(from.getFactorToBase());
        return scale(inBase.divide(to.getFactorToBase(), SCALE, ROUNDING));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }
}
