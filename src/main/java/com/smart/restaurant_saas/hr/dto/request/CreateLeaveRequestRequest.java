package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLeaveRequestRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal daysCount,
        String reason
) {
}
