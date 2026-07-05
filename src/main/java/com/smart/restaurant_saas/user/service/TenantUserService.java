package com.smart.restaurant_saas.user.service;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.service.HrErrorCode;
import com.smart.restaurant_saas.hr.repository.EmployeeRepository;
import com.smart.restaurant_saas.rbac.dto.request.AssignUserRoleRequest;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.PermissionScope;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.rbac.service.UserRoleService;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.dto.request.CreateUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.UserResponse;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantUserService {

    private static final long SYSTEM_TENANT_ID = 0L;

    private final CurrentTenantProvider currentTenantProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        Long tenantId = getTenantId();
        return userRepository.findByTenantIdAndStatusNotOrderByIdDesc(tenantId, UserStatus.DELETED).stream()
                .map(user -> toResponse(tenantId, user))
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Long tenantId = getTenantId();
        ensureTenantUserEndpointTenant(tenantId);

        String username = normalizeUsername(request.username());
        if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Username already exists for tenant: " + username,
                    ErrorParams.of("entityType", "User", "username", username));
        }

        Role role = findAllowedTenantRole(request.roleCode());
        Branch branch = validateAssignableBranch(tenantId, request.branchId());

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(toStatus(request.active()));

        User savedUser = userRepository.save(user);
        assignTenantRole(tenantId, savedUser.getId(), role.getCode(), branch);

        return UserResponse.from(savedUser, role, branch);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        Long tenantId = getTenantId();
        User user = findManagedUser(tenantId, userId);
        return toResponse(tenantId, user);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        Long tenantId = getTenantId();
        User user = findManagedUser(tenantId, userId);
        Role role = findAllowedTenantRole(request.roleCode());
        Branch branch = validateAssignableBranch(tenantId, request.branchId());

        if (Boolean.FALSE.equals(request.active())) {
            ensureNotCurrentActor(userId, "disable");
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(trimToNull(request.phone()));
        if (request.active() != null) {
            user.setStatus(toStatus(request.active()));
        }

        User savedUser = userRepository.saveAndFlush(user);
        assignTenantRole(tenantId, savedUser.getId(), role.getCode(), branch);

        return UserResponse.from(savedUser, role, branch);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        Long tenantId = getTenantId();
        User user = findManagedUser(tenantId, userId);

        if (Boolean.FALSE.equals(request.active())) {
            ensureNotCurrentActor(userId, "disable");
        }

        user.setStatus(toStatus(request.active()));
        User savedUser = userRepository.saveAndFlush(user);

        return toResponse(tenantId, savedUser);
    }

    @Transactional
    public void deleteUser(Long userId) {
        Long tenantId = getTenantId();
        User user = findManagedUser(tenantId, userId);
        ensureNotCurrentActor(userId, "delete");

        user.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(user);
    }

    private Long getTenantId() {
        return currentTenantProvider.getCurrentTenantId();
    }

    private void ensureTenantUserEndpointTenant(Long tenantId) {
        if (tenantId == null || tenantId == SYSTEM_TENANT_ID) {
            throw new AuthorizationException(HrErrorCode.TENANT_CONTEXT_REQUIRED,
                    "Tenant user management requires a tenant context");
        }
    }

    private User findManagedUser(Long tenantId, Long userId) {
        return userRepository.findByIdAndTenantIdAndStatusNot(userId, tenantId, UserStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "User not found: " + userId,
                        ErrorParams.of("entityType", "User", "entityId", userId)));
    }

    private Role findAllowedTenantRole(String roleCode) {
        RoleCode normalizedRoleCode = parseRoleCode(roleCode);
        if (normalizedRoleCode == RoleCode.SYS_ADMIN) {
            throw new AuthorizationException(HrErrorCode.NOT_ALLOWED_FOR_ROLE,
                    "SYS_ADMIN role cannot be assigned from tenant user APIs",
                    ErrorParams.of("roleCode", "SYS_ADMIN"));
        }

        return roleRepository.findByCodeAndActiveTrue(normalizedRoleCode)
                .orElseThrow(() -> new ValidationException(HrErrorCode.VALIDATION_FAILED,
                        "Invalid role: " + normalizedRoleCode.name(),
                        ErrorParams.of("field", "roleCode")));
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "roleCode is required",
                    ErrorParams.of("field", "roleCode"));
        }

        String normalizedRoleCode = roleCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedRoleCode.isEmpty()) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "roleCode must not be blank",
                    ErrorParams.of("field", "roleCode"));
        }

        try {
            return RoleCode.valueOf(normalizedRoleCode);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "Invalid roleCode: " + roleCode
                            + ". Allowed values: " + Arrays.toString(RoleCode.values()),
                    ErrorParams.of("field", "roleCode", "rejectedValue", roleCode,
                            "allowedValues", Arrays.toString(RoleCode.values())));
        }
    }

    private void assignTenantRole(Long tenantId, Long userId, RoleCode roleCode, Branch branch) {
        userRoleService.assignUserRole(
                tenantId,
                userId,
                new AssignUserRoleRequest(
                        roleCode.name(),
                        branch == null ? PermissionScope.TENANT.name() : PermissionScope.BRANCH.name(),
                        branch == null ? null : branch.getId()
                )
        );
    }

    private UserResponse toResponse(Long tenantId, User user) {
        UserRole userRole = userRoleRepository.findByTenantIdAndUserId(tenantId, user.getId()).orElse(null);
        Role role = userRole == null ? null : roleRepository.findById(userRole.getRoleId()).orElse(null);
        Branch branch = userRole == null || userRole.getBranchId() == null
                ? null
                : branchRepository.findByIdAndTenantId(userRole.getBranchId(), tenantId).orElse(null);
        return UserResponse.from(user, role, branch);
    }

    private Branch validateAssignableBranch(Long tenantId, Long branchId) {
        if (branchId == null) {
            return null;
        }

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

    private void ensureNotCurrentActor(Long userId, String action) {
        if (userId.equals(currentTenantProvider.getActorUserId())) {
            throw new AuthorizationException(HrErrorCode.SELF_ACTION_BLOCKED,
                    "Cannot " + action + " the currently authenticated user",
                    ErrorParams.of("action", action));
        }
    }

    private UserStatus toStatus(Boolean active) {
        return Boolean.FALSE.equals(active) ? UserStatus.INACTIVE : UserStatus.ACTIVE;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
