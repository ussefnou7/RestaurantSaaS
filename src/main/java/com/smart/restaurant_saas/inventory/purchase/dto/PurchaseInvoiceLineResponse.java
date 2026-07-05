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
    private final String uomSymbol;
    private final BigDecimal unitCost;
    private final BigDecimal lineTotal;
    private final BigDecimal discountPercent;
    private final BigDecimal discountAmount;
    private final BigDecimal lineSubtotal;
    private final BigDecimal lineNetTotal;
    private final String notes;
}
