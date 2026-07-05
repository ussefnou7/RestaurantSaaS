package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Updates an existing return line. The original invoice line (and therefore the material
 * and inherited unit cost) cannot change — delete and re-add the line to point at a
 * different original line.
 */
@Getter
@Setter
public class PurchaseReturnUpdateLineRequest {

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull(message = "uomId is required")
    private Long uomId;

    private String notes;
}
