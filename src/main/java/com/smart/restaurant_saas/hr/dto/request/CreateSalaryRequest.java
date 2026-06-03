package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSalaryRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal salaryAmount,
        @NotNull LocalDate effectiveFrom,
        String notes
) {
}
