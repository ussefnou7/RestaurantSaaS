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
 */
@Getter
@Builder
public class ShrinkageRow {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;

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
