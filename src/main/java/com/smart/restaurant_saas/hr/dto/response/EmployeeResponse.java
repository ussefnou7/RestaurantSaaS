package com.smart.restaurant_saas.hr.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.job.entity.Job;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        Long branchId,
        String branchName,
        String branchNameEn,
        String branchNameAr,
        String branchCode,
        Long jobId,
        String jobName,
        String jobNameEn,
        String jobNameAr,
        String jobCode,
        Long userId,
        String code,
        String fullName,
        String fullNameEn,
        String fullNameAr,
        String phone,
        String email,
        String nationalId,
        String address,
        String addressEn,
        String addressAr,
        LocalDate hireDate,
        BigDecimal salary,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EmployeeResponse from(Employee employee, Branch branch, Job job) {
        String branchNameEn = branch == null ? null : englishOrLegacy(branch.getNameEn(), branch.getNameAr(), branch.getName());
        String jobNameEn = job == null ? null : englishOrLegacy(job.getNameEn(), job.getNameAr(), job.getName());
        String fullNameEn = englishOrLegacy(employee.getFullNameEn(), employee.getFullNameAr(), employee.getFullName());
        String addressEn = englishOrLegacy(employee.getAddressEn(), employee.getAddressAr(), employee.getAddress());
        return new EmployeeResponse(
                employee.getId(),
                employee.getBranchId(),
                branch == null ? null : firstNonBlank(branch.getName(), branchNameEn, branch.getNameAr()),
                branchNameEn,
                branch == null ? null : branch.getNameAr(),
                branch == null ? null : branch.getCode(),
                employee.getJobId(),
                job == null ? null : firstNonBlank(job.getName(), jobNameEn, job.getNameAr()),
                jobNameEn,
                job == null ? null : job.getNameAr(),
                job == null ? null : job.getCode(),
                employee.getUserId(),
                employee.getCode(),
                firstNonBlank(employee.getFullName(), fullNameEn, employee.getFullNameAr()),
                fullNameEn,
                employee.getFullNameAr(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getNationalId(),
                firstNonBlank(employee.getAddress(), addressEn, employee.getAddressAr()),
                addressEn,
                employee.getAddressAr(),
                employee.getHireDate(),
                employee.getSalary(),
                employee.getActive(),
                employee.getNotes(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
