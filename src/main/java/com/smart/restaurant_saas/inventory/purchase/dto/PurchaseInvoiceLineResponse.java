package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PurchaseInvoiceLineResponse {

    private final Long id;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final Long uomId;
    /**
     * Held back from the D111 phase-3 cut. The Flutter app reads this straight off
     * {@code GET /inventory/purchase-invoices} and renders it beside every line quantity; it parses
     * no {@code uomId} and has no lookup cache, so removing this would silently turn every mobile
     * invoice line into a bare number. Remove only once mobile can resolve ids. See O42.
     */
    private final String uomSymbol;
    private final BigDecimal unitCost;
    private final BigDecimal lineTotal;
    private final BigDecimal discountPercent;
    private final BigDecimal discountAmount;
    private final BigDecimal lineSubtotal;
    private final BigDecimal lineNetTotal;
    private final String notes;
}
