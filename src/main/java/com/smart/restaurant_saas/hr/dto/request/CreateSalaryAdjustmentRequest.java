package com.smart.restaurant_saas.hr.dto.request;

import com.smart.restaurant_saas.hr.enums.SalaryAdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSalaryAdjustmentRequest(
        @NotNull SalaryAdjustmentType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotNull LocalDate adjustmentDate,
        String reason,
        String notes
) {
}
