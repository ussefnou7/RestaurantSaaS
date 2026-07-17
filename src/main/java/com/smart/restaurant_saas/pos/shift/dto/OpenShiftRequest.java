package com.smart.restaurant_saas.pos.shift.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OpenShiftRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal openingCash
) {
}
