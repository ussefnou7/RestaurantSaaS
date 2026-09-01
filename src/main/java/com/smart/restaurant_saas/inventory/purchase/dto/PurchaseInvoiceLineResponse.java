package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    /** The unit only; the client resolves its name from the UOM lookup cache (D111 phase 3). */
    private final Long uomId;
    private final BigDecimal unitCost;
    private final BigDecimal lineTotal;
    private final BigDecimal discountPercent;
    private final BigDecimal discountAmount;
    private final BigDecimal lineSubtotal;
    private final BigDecimal lineNetTotal;
    private final LocalDate expiryDate;
    private final String notes;
}
