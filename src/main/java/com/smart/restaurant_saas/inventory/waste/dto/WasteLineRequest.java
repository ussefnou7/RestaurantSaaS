package com.smart.restaurant_saas.inventory.waste.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Adds a line to a waste document. The line records a material and the quantity written off,
 * in the given UOM. No cost is supplied — it is computed at POST via FIFO depletion.
 * When {@code uomId} is omitted the material's stock UOM is used.
 */
@Getter
@Setter
public class WasteLineRequest {

    @NotNull(message = "materialId is required")
    private Long materialId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    private BigDecimal quantity;

    private Long uomId;

    private String notes;
}
