package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.JobTitle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        Long branchId,
        String branchName,
        String branchCode,
        Long jobTitleId,
        String jobTitleName,
        String jobTitleCode,
        Long appUserId,
        String employeeCode,
        String fullName,
        String phone,
        String email,
        String nationalId,
        String address,
        LocalDate hireDate,
        BigDecimal salary,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EmployeeResponse from(Employee employee, Branch branch, JobTitle jobTitle) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getBranchId(),
                branch == null ? null : branch.getName(),
                branch == null ? null : branch.getCode(),
                employee.getJobTitleId(),
                jobTitle == null ? null : jobTitle.getName(),
                jobTitle == null ? null : jobTitle.getCode(),
                employee.getAppUserId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getNationalId(),
                employee.getAddress(),
                employee.getHireDate(),
                employee.getSalary(),
                employee.getActive(),
                employee.getNotes(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
