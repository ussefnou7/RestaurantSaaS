package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSalaryAdditionRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 255) String title,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotNull LocalDate salaryMonth,
        String notes,
        Boolean active
) {
}
