package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.SalaryAddition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryAdditionResponse(
        Long id,
        Long branchId,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String title,
        BigDecimal amount,
        LocalDate salaryMonth,
        String notes,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SalaryAdditionResponse from(SalaryAddition salaryAddition, Employee employee) {
        return new SalaryAdditionResponse(
                salaryAddition.getId(),
                salaryAddition.getBranchId(),
                salaryAddition.getEmployeeId(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                salaryAddition.getTitle(),
                salaryAddition.getAmount(),
                salaryAddition.getSalaryMonth(),
                salaryAddition.getNotes(),
                salaryAddition.getActive(),
                salaryAddition.getCreatedAt(),
                salaryAddition.getUpdatedAt()
        );
    }
}
