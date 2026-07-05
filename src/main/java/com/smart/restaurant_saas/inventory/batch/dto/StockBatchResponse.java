package com.smart.restaurant_saas.inventory.batch.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;

/**
 * One stock batch of a balance, for the expandable sub-row in the warehouse stock view.
 * Quantities and {@code unitCost} are per the balance's display UOM ({@code uomSymbol}).
 */
@Getter
@Builder
public class StockBatchResponse {

    private final Long id;
    private final BigDecimal originalQuantity;
    private final BigDecimal remainingQuantity;
    private final BigDecimal unitCost;
    private final LocalDateTime movementDate;
    private final StockBatchStatus status;

    /** Display UOM symbol of the parent balance (e.g. "kg"), so the row can render units. */
    private final String uomSymbol;

    /** Set when the batch came from a purchase; null otherwise. FE shows "Invoice #X". */
    private final Long sourceInvoiceId;

    /**
     * Coarse origin label derived from {@code sourceInvoiceId}: PURCHASE when an invoice is
     * present, otherwise OTHER (opening balance or transfer-in). Opening-balance vs
     * transfer-in is not distinguished here (would require loading each source transaction).
     */
    private final String sourceType;
}
