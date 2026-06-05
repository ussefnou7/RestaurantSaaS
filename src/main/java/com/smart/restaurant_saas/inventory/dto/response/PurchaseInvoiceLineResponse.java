package com.smart.restaurant_saas.inventory.dto.response;

import java.math.BigDecimal;

public record PurchaseInvoiceLineResponse(
        Long id,
        Long materialId,
        String materialCode,
        String materialName,
        String materialNameAr,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String categoryNameAr,
        BigDecimal quantity,
        Long uomId,
        String uomCode,
        String uomName,
        String uomNameAr,
        String uomSymbol,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        String notes
) {
}
