package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A stock-UOM ledger aggregate rendered into the display layer, or the degraded form when no
 * conversion path exists.
 *
 * <p>Shared by both date-ranged ledger reports (D13 — two real callers) because the degrade rule is
 * a contract rather than a formatting detail: the quantity and its unit must go null <i>together</i>
 * or not at all, and a report that returned the unconverted number instead would put two units in
 * one column.
 */
record ReportQuantity(String text, Long uomId, String uomSymbol) {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final ReportQuantity UNAVAILABLE = new ReportQuantity(null, null, null);

    /**
     * Converts a signed stock-UOM net into {@code material.displayUom} (D88).
     *
     * <p><b>The target is the material's current display UOM, not a frozen one.</b> The post-freeze
     * count endpoint converts to {@code line.uom} because a document froze that unit; these reports
     * have no document context, so there is nothing frozen to honour.
     *
     * <p><b>Converted once, on the already-summed net.</b> Converting per ledger row and then
     * summing would round at scale 6 on every row and accumulate real drift.
     *
     * <p><b>A missing path degrades the row; it does not fail the report.</b> D88 requires a loud
     * 400 on the physical-count detail read, where a wrong number could drive an irreversible
     * reconcile. Nothing rides on this read, and one misconfigured material would blank a report
     * covering hundreds — which is useless. The value survives (money does not convert), so a
     * degraded row still sorts and still totals correctly; only the quantity and its unit go null,
     * together, so no consumer can misread a bare number. Probed with {@code areConvertible} rather
     * than catching {@code UomConversionException} in a loop.
     */
    static ReportQuantity of(UomConversionService uomConversionService,
                             BigDecimal netStockQuantity, Material material, Long tenantId) {
        if (material == null || netStockQuantity == null) {
            return UNAVAILABLE;
        }
        Uom stockUom = material.getStockUom();
        Uom displayUom = material.getDisplayUom();
        if (!uomConversionService.areConvertible(stockUom, displayUom, material, tenantId)) {
            return UNAVAILABLE;
        }
        BigDecimal converted = uomConversionService
            .convert(netStockQuantity, stockUom, displayUom, material, tenantId)
            .setScale(SCALE, ROUNDING);
        return new ReportQuantity(
            converted.toPlainString(), displayUom.getId(), displayUom.getSymbol());
    }
}
