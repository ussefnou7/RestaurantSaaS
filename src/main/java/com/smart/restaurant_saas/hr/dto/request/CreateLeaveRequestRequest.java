package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLeaveRequestRequest(
        @NotNull Long leaveTypeId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        String reason,
        String notes,
        Long employeeId,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal daysCount
) {
    public CreateLeaveRequestRequest(
            Long employeeId,
            Long leaveTypeId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal daysCount,
            String reason
    ) {
        this(leaveTypeId, fromDate, toDate, reason, null, employeeId, daysCount);
    }
}
