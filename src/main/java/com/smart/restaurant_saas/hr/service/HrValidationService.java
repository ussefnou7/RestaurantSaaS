package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.job.entity.Job;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.job.repository.JobRepository;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrValidationService {

    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final BranchRepository branchRepository;
    private final JobRepository jobRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public Branch findActiveBranch(Long tenantId, Long branchId) {
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid branch: " + branchId));
        if (!Boolean.TRUE.equals(branch.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch is inactive: " + branchId);
        }
        return branch;
    }

    public Branch findBranch(Long tenantId, Long branchId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid branch: " + branchId));
    }

    public Job findActiveJob(Long tenantId, Long jobId) {
        return jobRepository.findByIdAndTenantIdAndActiveTrue(jobId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or inactive job: " + jobId));
    }

    public Job findJob(Long tenantId, Long jobId) {
        return jobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
    }

    public Employee findActiveEmployee(Long tenantId, Long employeeId) {
        Employee employee = employeeRepository.findByIdAndTenantIdAndActiveTrue(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or inactive employee: " + employeeId));
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
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid user: " + userId));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User is not active: " + userId);
        }

        UserRole userRole = userRoleRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "User role is not assigned: " + userId));
        Role role = roleRepository.findById(userRole.getRoleId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "User role is invalid: " + userId));
        if (role.getCode() == RoleCode.SYS_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SYS_ADMIN users cannot be linked to employees");
        }

        boolean linkedToActiveEmployee = currentEmployeeId == null
                ? employeeRepository.existsByTenantIdAndUserIdAndActiveTrue(tenantId, userId)
                : employeeRepository.existsByTenantIdAndUserIdAndActiveTrueAndIdNot(tenantId, userId, currentEmployeeId);
        if (linkedToActiveEmployee) {
            throw new ApiException(HttpStatus.CONFLICT, "User is already linked to an active employee");
        }
    }
}
