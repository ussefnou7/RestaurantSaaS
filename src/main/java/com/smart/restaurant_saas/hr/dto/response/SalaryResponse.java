package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.Salary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryResponse(
        Long id,
        Long tenantId,
        Long employeeId,
        Long branchId,
        BigDecimal salaryAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SalaryResponse from(Salary salary) {
        return new SalaryResponse(
                salary.getId(),
                salary.getTenantId(),
                salary.getEmployeeId(),
                salary.getBranchId(),
                salary.getSalaryAmount(),
                salary.getEffectiveFrom(),
                salary.getEffectiveTo(),
                salary.getActive(),
                salary.getNotes(),
                salary.getCreatedAt(),
                salary.getUpdatedAt()
        );
    }
}
