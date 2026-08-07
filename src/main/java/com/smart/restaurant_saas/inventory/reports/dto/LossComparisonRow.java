package com.smart.restaurant_saas.inventory.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One row of the loss comparison report: a material's waste and its physical-count variance over
 * the requested window, side by side.
 *
 * <p><b>The ratio is the diagnosis.</b> High waste with near-zero shrinkage is a storage or
 * purchasing problem — recorded, understood, fixable by ordering differently. High shrinkage with
 * near-zero waste is a control problem: stock is leaving without a document. Both high means both,
 * and the waste figure is probably masking part of the shrinkage. Neither existing report shows
 * this; side by side it reads at a glance.
 *
 * <p><b>⚠ The two sides use different sign conventions, in the same row.</b> This is deliberate and
 * is the one thing a consumer must not get wrong:
 * <ul>
 *   <li><b>{@code wasteQuantity} / {@code wasteValue} are positive magnitudes.</b> Waste is always
 *       an outflow, so a minus sign on every row would carry no information. Bigger means worse.</li>
 *   <li><b>{@code shrinkageQuantity} / {@code shrinkageValue} are signed.</b> Negative is a
 *       shortage; <b>positive is a surplus</b>, which usually reveals a wrong recipe or a rushed
 *       count and is worth surfacing rather than hiding.</li>
 * </ul>
 * A renderer must not apply one formatter to all four. In particular, showing
 * {@code shrinkageValue} as a magnitude would turn a surplus into a loss.
 *
 * <p>{@code totalValue} is the combined loss and is <b>loss-positive</b>:
 * {@code wasteValue - shrinkageValue}. A shrinkage surplus therefore reduces it, and a material
 * that netted out ahead reports a negative total.
 *
 * <p>Decimal fields are {@code String} for the same reason as {@link StockValuationRow} — the
 * scale-6 value survives JSON transport exactly.
 *
 * <p><b>Materials with no waste and no shrinkage are included, sorted last.</b> "Nothing happened"
 * is a reassuring answer, not noise. They are partitioned to the end rather than left to fall
 * wherever a value sort puts them (which would be the middle, between the negatives and the
 * positives).
 *
 * <p>Quantity fields and the UOM pair go null together when the material has no conversion path
 * from its stock UOM to its display UOM; both values stay intact, since money does not convert.
 * Historical report, so no {@code active} filter — see {@link #materialActive}.
 */
@Getter
@Builder
public class LossComparisonRow {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;

    /** <b>Positive magnitude</b>, display UOM (D88). Null when unconvertible. */
    private final String wasteQuantity;

    /** <b>Positive magnitude.</b> Always present — money needs no conversion. */
    private final String wasteValue;

    /** <b>Signed</b>: negative is a shortage, positive a surplus. Display UOM. Null when unconvertible. */
    private final String shrinkageQuantity;

    /** <b>Signed</b>: negative is a shortage, positive a surplus. Always present. */
    private final String shrinkageValue;

    /** Combined loss, loss-positive: {@code wasteValue - shrinkageValue}. Always present. */
    private final String totalValue;

    /** Display UOM shared by both quantity columns. Null when unconvertible. */
    private final Long uomId;

    /** Symbol of {@link #uomId}. Null when unconvertible. */
    private final String uomSymbol;

    /** False when the material has since been deactivated; its history is reported regardless. */
    private final Boolean materialActive;
}
