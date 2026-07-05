package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseReturnLineRequest {

    @NotNull(message = "originalLineId is required")
    private Long originalLineId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull(message = "uomId is required")
    private Long uomId;

    private String notes;
}
