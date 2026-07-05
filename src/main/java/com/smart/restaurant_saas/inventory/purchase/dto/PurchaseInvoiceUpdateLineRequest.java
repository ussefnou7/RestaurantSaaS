package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseInvoiceUpdateLineRequest {

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull(message = "uomId is required")
    private Long uomId;

    @NotNull(message = "unitCost is required")
    @Positive(message = "unitCost must be greater than 0")
    private BigDecimal unitCost;

    @DecimalMin(value = "0", message = "discountPercent must be at least 0")
    @DecimalMax(value = "100", message = "discountPercent must be at most 100")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "discountAmount must be at least 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private String notes;
}
