package com.smart.restaurant_saas.inventory.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One row of the shrinkage report: a material's net physical-count variance over the requested
 * window, in one warehouse or across all of them.
 *
 * <p>Decimal fields are {@code String} for the same reason as {@link StockValuationRow} — the
 * scale-6 value survives JSON transport exactly; conversion happens in
 * {@code ShrinkageReportService}.
 *
 * <p><b>The sign is the payload.</b> {@code netQuantity} and {@code netValue} are negative for a
 * shortage and positive for a surplus, and a material that had both nets to the difference. A
 * surplus is not noise — it usually means a wrong recipe or a rushed count — so it is never
 * absolute-d into the shortage column.
 *
 * <p><b>There is no reason column, deliberately.</b> A shrinkage gap has no recorded cause by
 * definition (D89) — that is the entire point of the report. A placeholder column would imply the
 * system knows something it does not.
 *
 * <p><b>{@code netQuantity} and the UOM pair are null together</b> when the material has no
 * conversion path from its stock UOM to its display UOM. {@code netValue} stays intact — money does
 * not convert — so such a row still sorts and still totals correctly. A frontend counts these by
 * testing {@code netQuantity == null}.
 *
 * <p><b>Deactivated materials are reported and flagged, never filtered out</b> (D86, historical-
 * report amendment). This report answers "what happened", so a flag flipped today cannot retract
 * it — and a filter here would be trivially weaponisable: steal a material, deactivate it, and the
 * shortage vanishes from the one report meant to surface it. The row carries
 * {@link #materialActive} so it never silently looks like any other; a material no longer in
 * service is itself a lead. There is deliberately no parameter to include or exclude these — one
 * more optional filter is one more way to hide the evidence.
 */
@Getter
@Builder
public class ShrinkageRow {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;

    /**
     * False when the material has since been deactivated; its history is reported regardless.
     *
     * <p>There is no matching warehouse flag because a row has no single warehouse identity — rows
     * are grouped by material and span every warehouse in scope. Movements from deactivated
     * warehouses are included and fold into the material's figures like any other.
     */
    private final Boolean materialActive;

    /** Net variance in the material's <b>display</b> UOM (D88), signed. Null when unconvertible. */
    private final String netQuantity;

    /** Display UOM of {@link #netQuantity}. Null when unconvertible. */
    private final Long uomId;

    /** Symbol of {@link #uomId}. Null when unconvertible. */
    private final String uomSymbol;

    /** Signed net value. Always present — money needs no conversion. */
    private final String netValue;

    /** How many ledger rows (count events) contributed to this material's figure. */
    private final Long movementCount;
}
