package com.smart.restaurant_saas.hr.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotNull Long branchId,
        @NotNull Long jobId,
        @JsonAlias("appUserId") Long userId,
        @Size(max = 255) String fullNameEn,
        @Size(max = 255) String fullNameAr,
        @Size(max = 50) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String nationalId,
        String addressEn,
        String addressAr,
        @NotNull LocalDate hireDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal salary,
        Boolean active,
        @Size(max = 255) String fullName,
        String address,
        String notes
) {
    public CreateEmployeeRequest(
            Long branchId,
            Long jobId,
            Long userId,
            String fullName,
            String phone,
            String email,
            String nationalId,
            String address,
            LocalDate hireDate,
            BigDecimal salary,
            Boolean active,
            String notes
    ) {
        this(
                branchId,
                jobId,
                userId,
                null,
                null,
                phone,
                email,
                nationalId,
                null,
                null,
                hireDate,
                salary,
                active,
                fullName,
                address,
                notes
        );
    }
}
