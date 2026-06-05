package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PurchaseInvoiceLineRequest(
        @NotNull Long materialId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @NotNull Long uomId,
        @NotNull @DecimalMin(value = "0.0") BigDecimal unitCost,
        String notes
) {
}
