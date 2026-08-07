package com.smart.restaurant_saas.inventory.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One row of the waste analysis report: a material's net write-off for <b>one reason</b> over the
 * requested window.
 *
 * <p>Structurally identical to {@link ShrinkageRow} plus {@link #reasonCode} — see that class for
 * the sign, decimal-as-String, and null-quantity conventions, which are the same here.
 *
 * <p><b>Flat, not nested.</b> The grouping key is (material, reason), so a material wasted for two
 * reasons produces two rows — "80 kg wasted, 60 of it expired" is two rows, not a nested breakdown.
 * Nesting would define O22's {@code grouped} renderer archetype for the whole report family, which
 * is a decision that belongs to a report that genuinely cannot be flattened (P&L, by-supplier), not
 * to this one.
 *
 * <p>{@link #reasonCode} is never null: a ledger row with no reason groups under
 * {@code UNSPECIFIED} so the rendered rows always sum to the real total.
 */
@Getter
@Builder
public class WasteAnalysisRow {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;

    /** A {@code WasteReasonCode} name, or {@code UNSPECIFIED}. Never null. */
    private final String reasonCode;

    /** Net write-off in the material's <b>display</b> UOM (D88), signed. Null when unconvertible. */
    private final String netQuantity;

    /** Display UOM of {@link #netQuantity}. Null when unconvertible. */
    private final Long uomId;

    /** Symbol of {@link #uomId}. Null when unconvertible. */
    private final String uomSymbol;

    /** Signed net value. Always present — money needs no conversion. */
    private final String netValue;

    /** How many ledger rows contributed to this (material, reason) figure. */
    private final Long movementCount;
}
