package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.job.repository.JobRepository;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrValidationService {

    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final BranchRepository branchRepository;
    private final JobRepository jobRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public Branch findActiveBranch(Long tenantId, Long branchId) {
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Invalid branch: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                    "Branch is inactive: " + branchId,
                    ErrorParams.of("entityType", "Branch", "entityId", branchId));
        }
        return branch;
    }

    public Branch findBranch(Long tenantId, Long branchId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Invalid branch: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    public Job findActiveJob(Long tenantId, Long jobId) {
        // Split not-found from inactive so each gets a distinct code (404 vs 400) rather than
        // conflating them behind a single "invalid or inactive" message.
        Job job = jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Job not found: " + jobId,
                        ErrorParams.of("entityType", "Job", "entityId", jobId)));
        if (!Boolean.TRUE.equals(job.getActive())) {
            throw new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                    "Job is inactive: " + jobId,
                    ErrorParams.of("entityType", "Job", "entityId", jobId));
        }
        return job;
    }

    public Job findJob(Long tenantId, Long jobId) {
        return jobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
    }

    public Employee findActiveEmployee(Long tenantId, Long employeeId) {
        // Split not-found from inactive (see findActiveJob).
        Employee employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Employee not found: " + employeeId,
                        ErrorParams.of("entityType", "Employee", "entityId", employeeId)));
        if (!Boolean.TRUE.equals(employee.getActive())) {
            throw new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                    "Employee is inactive: " + employeeId,
                    ErrorParams.of("entityType", "Employee", "entityId", employeeId));
        }
        ensureCanAccessBranch(employee.getBranchId());
        return employee;
    }

    public void ensureCanAccessBranch(Long branchId) {
        currentUserScopeProvider.ensureCanAccessBranch(branchId);
    }

    public void validateOptionalUser(Long tenantId, Long userId, Long currentEmployeeId) {
        if (userId == null) {
            return;
        }

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Invalid user: " + userId,
                        ErrorParams.of("entityType", "User", "entityId", userId)));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HrErrorCode.INACTIVE_REFERENCE,
                    "User is not active: " + userId,
                    ErrorParams.of("entityType", "User", "entityId", userId));
        }

        Role role = roleRepository.findById(user.getRoleId())
                .orElseThrow(() -> new BusinessException(HrErrorCode.VALIDATION_FAILED,
                        "User role is invalid: " + userId,
                        ErrorParams.of("field", "userRole", "entityId", userId)));
        if (role.getCode() == RoleCode.SYS_ADMIN) {
            throw new AuthorizationException(HrErrorCode.NOT_ALLOWED_FOR_ROLE,
                    "SYS_ADMIN users cannot be linked to employees",
                    ErrorParams.of("roleCode", "SYS_ADMIN"));
        }

        boolean linkedToActiveEmployee = currentEmployeeId == null
                ? employeeRepository.existsByTenantIdAndUserIdAndActiveTrue(tenantId, userId)
                : employeeRepository.existsByTenantIdAndUserIdAndActiveTrueAndIdNot(tenantId, userId, currentEmployeeId);
        if (linkedToActiveEmployee) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "User is already linked to an active employee",
                    ErrorParams.of("entityType", "User", "context", "activeEmployeeLink"));
        }
    }
}
