package com.smart.restaurant_saas.inventory.physicalcount.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCountedQuantityRequest {

    @NotNull(message = "lineId is required")
    private Long lineId;

    @NotNull(message = "countedQuantity is required")
    @PositiveOrZero(message = "countedQuantity must be greater than or equal to 0")
    private BigDecimal countedQuantity;

    private String notes;
}
