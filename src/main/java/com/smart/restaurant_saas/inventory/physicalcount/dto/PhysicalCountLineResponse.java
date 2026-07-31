package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;

@Getter
@Builder
public class PhysicalCountLineResponse {

    private final Long id;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;
    private final Long uomId;
    private final String uomSymbol;
    private final BigDecimal expectedQuantity;
    private final BigDecimal adjustedExpectedQuantity;

    /** True when the adjusted expectation uses the read time because this line is not counted yet. */
    private final Boolean adjustedExpectedQuantityProvisional;
    private final BigDecimal countedQuantity;
    private final BigDecimal variance;

    /**
     * |variance| × {@link #unitCostAtFreeze} — an estimate, not the amount the ledger records.
     * The ledger values the same movement differently and correctly: a shortage is FIFO-consumed at
     * the real cost of the open batches it eats, and a surplus enters at the average cost as it
     * stands when reconcile runs. Both can diverge from the freeze-time average this figure uses.
     * Reports read the ledger; this field exists so the review screen can show a number before
     * anything is posted. See {@link #varianceValueIsEstimate}.
     */
    private final BigDecimal varianceValue;

    /**
     * True whenever {@link #varianceValue} carries a figure, marking it as a freeze-cost estimate
     * so the UI can label it as such. It never becomes the recorded amount, including after
     * reconcile — the posted value lives on the ledger transaction.
     */
    private final Boolean varianceValueIsEstimate;

    private final BigDecimal unitCostAtFreeze;
    private final CountLineAction actionTaken;
    private final Long adjustmentTransactionId;
    private final LocalDateTime countedAt;
    private final String notes;
}
