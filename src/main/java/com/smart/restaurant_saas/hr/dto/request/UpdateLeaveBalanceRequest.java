package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateLeaveBalanceRequest(
        @NotNull @DecimalMin(value = "0.0") BigDecimal openingBalance,
        @NotNull @DecimalMin(value = "0.0") BigDecimal assignedDays,
        Boolean active,
        String notes
) {
}
