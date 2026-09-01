package com.smart.restaurant_saas.inventory.stock.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStockSettingsRequest {

    @PositiveOrZero(message = "minimumQuantity must be non-negative")
    private BigDecimal minimumQuantity;

    @PositiveOrZero(message = "maxAgeDays must be non-negative")
    private Integer maxAgeDays;

    /** Optional. Nullable — no maximum is enforced when omitted. */
    @PositiveOrZero(message = "maximumQuantity must be non-negative")
    private BigDecimal maximumQuantity;
}
