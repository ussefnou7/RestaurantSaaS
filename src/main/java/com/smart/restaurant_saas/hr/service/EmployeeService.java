package com.smart.restaurant_saas.hr.service;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.trimToNull;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.dto.request.CreateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateActiveStatusRequest;
import com.smart.restaurant_saas.hr.dto.request.UpdateEmployeeRequest;
import com.smart.restaurant_saas.hr.dto.response.EmployeeResponse;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantEntityPrefix;
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
    private final TenantCodeService tenantCodeService;
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
        Job job = hrValidationService.findActiveJob(tenantId, request.jobId());
        hrValidationService.validateOptionalUser(tenantId, request.userId(), null);

        String code = tenantCodeService
                .validateAndNormalizeCode(request.code(), TenantEntityPrefix.EMP)
                .code();
        if (employeeRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists for tenant: " + code);
        }

        Employee employee = new Employee();
        employee.setTenantId(tenantId);
        employee.setBranchId(branch.getId());
        employee.setJobId(job.getId());
        employee.setUserId(request.userId());
        employee.setCode(code);
        applyBilingualFields(employee, request.fullNameEn(), request.fullNameAr(), request.fullName(),
                request.addressEn(), request.addressAr(), request.address(),
                request.notes());
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(normalizeEmail(request.email()));
        employee.setNationalId(trimToNull(request.nationalId()));
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
        employee.setActive(request.active() == null || request.active());
        employee.setCreatedBy(currentTenantProvider.getActorUserId());

        return EmployeeResponse.from(employeeRepository.save(employee), branch, job);
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
        Job job = hrValidationService.findActiveJob(tenantId, request.jobId());
        hrValidationService.validateOptionalUser(tenantId, request.userId(), id);

        String code = tenantCodeService
                .validateAndNormalizeCode(request.code(), TenantEntityPrefix.EMP)
                .code();
        if (!employee.getCode().equals(code)
                && employeeRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists for tenant: " + code);
        }

        employee.setBranchId(branch.getId());
        employee.setJobId(job.getId());
        employee.setUserId(request.userId());
        employee.setCode(code);
        applyBilingualFields(employee, request.fullNameEn(), request.fullNameAr(), request.fullName(),
                request.addressEn(), request.addressAr(), request.address(),
                request.notes());
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(normalizeEmail(request.email()));
        employee.setNationalId(trimToNull(request.nationalId()));
        employee.setHireDate(request.hireDate());
        employee.setSalary(request.salary());
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        employee.setUpdatedBy(currentTenantProvider.getActorUserId());

        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee), branch, job);
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
        Job job = hrValidationService.findJob(tenantId, employee.getJobId());
        return EmployeeResponse.from(employee, branch, job);
    }

    private String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private void applyBilingualFields(
            Employee employee,
            String requestedFullNameEn,
            String requestedFullNameAr,
            String legacyFullName,
            String requestedAddressEn,
            String requestedAddressAr,
            String legacyAddress,
            String legacyNotes
    ) {
        String fullNameEn = firstNonBlank(requestedFullNameEn, legacyFullName);
        String fullNameAr = trimToNull(requestedFullNameAr);
        String displayName = firstNonBlank(fullNameEn, fullNameAr);
        if (displayName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one of fullNameEn or fullNameAr is required");
        }

        String addressEn = firstNonBlank(requestedAddressEn, legacyAddress);
        String addressAr = trimToNull(requestedAddressAr);

        employee.setFullName(displayName);
        employee.setFullNameEn(fullNameEn);
        employee.setFullNameAr(fullNameAr);
        employee.setAddress(firstNonBlank(addressEn, addressAr));
        employee.setAddressEn(addressEn);
        employee.setAddressAr(addressAr);
        employee.setNotes(trimToNull(legacyNotes));
    }
}
