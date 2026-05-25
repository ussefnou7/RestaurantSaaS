package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.response.EmployeeResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.JobTitle;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final HrValidationService hrValidationService;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        List<Employee> employees = currentUserScopeProvider.isTenantScoped()
                ? employeeRepository.findByTenantIdOrderByIdDesc(tenantId)
                : employeeRepository.findByTenantIdAndBranchIdOrderByIdDesc(
                        tenantId,
                        currentUserScopeProvider.getCurrentBranchId()
                                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Branch scope is required"))
                );
        return employees.stream()
                .map(employee -> toResponse(tenantId, employee))
                .toList();
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Branch branch = hrValidationService.findActiveBranch(tenantId, request.branchId());
        hrValidationService.ensureCanAccessBranch(branch.getId());
        JobTitle jobTitle = hrValidationService.findActiveJobTitle(tenantId, request.jobTitleId());
        hrValidationService.validateOptionalAppUser(tenantId, request.appUserId(), null);

        String employeeCode = normalizeCode(request.employeeCode());
        if (employeeRepository.existsByTenantIdAndEmployeeCode(tenantId, employeeCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists for tenant: " + employeeCode);
        }

        Employee employee = new Employee();
        employee.setTenantId(tenantId);
        employee.setBranchId(branch.getId());
        employee.setJobTitleId(jobTitle.getId());
        employee.setAppUserId(request.appUserId());
        employee.setEmployeeCode(employeeCode);
        employee.setFullName(request.fullName().trim());
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(normalizeEmail(request.email()));
        employee.setNationalId(trimToNull(request.nationalId()));
        employee.setAddress(trimToNull(request.address()));
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
        employee.setActive(request.active() == null || request.active());
        employee.setNotes(trimToNull(request.notes()));
        employee.setCreatedBy(currentTenantProvider.getActorUserId());

        return EmployeeResponse.from(employeeRepository.save(employee), branch, jobTitle);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = findEmployee(tenantId, id);
        hrValidationService.ensureCanAccessBranch(employee.getBranchId());
        return toResponse(tenantId, employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = findEmployee(tenantId, id);
        hrValidationService.ensureCanAccessBranch(employee.getBranchId());

        Branch branch = hrValidationService.findActiveBranch(tenantId, request.branchId());
        hrValidationService.ensureCanAccessBranch(branch.getId());
        JobTitle jobTitle = hrValidationService.findActiveJobTitle(tenantId, request.jobTitleId());
        hrValidationService.validateOptionalAppUser(tenantId, request.appUserId(), id);

        String employeeCode = normalizeCode(request.employeeCode());
        if (!employee.getEmployeeCode().equals(employeeCode)
                && employeeRepository.existsByTenantIdAndEmployeeCodeAndIdNot(tenantId, employeeCode, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists for tenant: " + employeeCode);
        }

        employee.setBranchId(branch.getId());
        employee.setJobTitleId(jobTitle.getId());
        employee.setAppUserId(request.appUserId());
        employee.setEmployeeCode(employeeCode);
        employee.setFullName(request.fullName().trim());
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(normalizeEmail(request.email()));
        employee.setNationalId(trimToNull(request.nationalId()));
        employee.setAddress(trimToNull(request.address()));
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        employee.setNotes(trimToNull(request.notes()));
        employee.setUpdatedBy(currentTenantProvider.getActorUserId());

        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee), branch, jobTitle);
    }

    @Transactional
    public EmployeeResponse updateEmployeeStatus(Long id, UpdateActiveStatusRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Employee employee = findEmployee(tenantId, id);
        hrValidationService.ensureCanAccessBranch(employee.getBranchId());
        employee.setActive(request.active());
        employee.setUpdatedBy(currentTenantProvider.getActorUserId());

        return toResponse(tenantId, employeeRepository.saveAndFlush(employee));
    }

    private Employee findEmployee(Long tenantId, Long id) {
        return employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found: " + id));
    }

    private EmployeeResponse toResponse(Long tenantId, Employee employee) {
        Branch branch = hrValidationService.findBranch(tenantId, employee.getBranchId());
        JobTitle jobTitle = hrValidationService.findJobTitle(tenantId, employee.getJobTitleId());
        return EmployeeResponse.from(employee, branch, jobTitle);
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
