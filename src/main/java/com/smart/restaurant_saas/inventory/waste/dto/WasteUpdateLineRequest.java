package com.smart.restaurant_saas.inventory.waste.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Updates an existing waste line. The material cannot change — delete and re-add the line to
 * write off a different material. When {@code uomId} is omitted the material's stock UOM is used.
 */
@Getter
@Setter
public class WasteUpdateLineRequest {

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    private BigDecimal quantity;

    private Long uomId;

    private String notes;
}
