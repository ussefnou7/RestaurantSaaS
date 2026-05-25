package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotNull Long branchId,
        @NotNull Long jobTitleId,
        Long appUserId,
        @NotBlank @Size(max = 100) String employeeCode,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 50) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String nationalId,
        String address,
        @NotNull LocalDate hireDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal salary,
        Boolean active,
        String notes
) {
}
