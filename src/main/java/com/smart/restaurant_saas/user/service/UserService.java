package com.smart.restaurant_saas.user.service;

import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.hr.service.HrErrorCode;
import com.smart.restaurant_saas.rbac.RbacErrorCode;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.service.RoleService;
import com.smart.restaurant_saas.rbac.service.UserPermissionService;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.dto.request.CreateTenantOwnerRequest;
import com.smart.restaurant_saas.user.dto.request.CreateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.TenantUserResponse;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final long SYSTEM_TENANT_ID = 0L;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final UserPermissionService userPermissionService;

    @Transactional
    public TenantUserResponse createOwner(Long tenantId, CreateTenantOwnerRequest request) {
        Tenant tenant = findTenant(tenantId);
        if (tenant.getId() == SYSTEM_TENANT_ID) {
            throw new AuthorizationException(HrErrorCode.SYSTEM_TENANT_RESTRICTED,
                    "Cannot create an owner for the system tenant",
                    ErrorParams.of("action", "createOwner"));
        }

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Username already exists for tenant: " + username,
                    ErrorParams.of("entityType", "User", "username", username));
        }
        if (email != null && userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Email already exists for tenant: " + email,
                    ErrorParams.of("entityType", "User", "email", email));
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        Role ownerRole = findActiveRole(RoleCode.OWNER);
        user.setRoleId(ownerRole.getId());

        User savedUser = userRepository.save(user);
        copyRolePermissionsToUser(tenantId, savedUser.getId(), ownerRole);

        return TenantUserResponse.from(savedUser);
    }

    @Transactional
    public TenantUserResponse createUser(Long tenantId, CreateTenantUserRequest request) {
        validateTenantExists(tenantId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Username already exists for tenant: " + username,
                    ErrorParams.of("entityType", "User", "username", username));
        }
        if (email != null && userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Email already exists for tenant: " + email,
                    ErrorParams.of("entityType", "User", "email", email));
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        Role role = findActiveRole(parseRoleCode(request.roleCode()));
        validateRoleBranch(role, request.branchId());
        user.setRoleId(role.getId());
        user.setBranchId(request.branchId());

        User savedUser = userRepository.save(user);
        copyRolePermissionsToUser(tenantId, savedUser.getId(), role);

        return TenantUserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public List<TenantUserResponse> listUsers(Long tenantId) {
        validateTenantExists(tenantId);
        return userRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(TenantUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantUserResponse getUser(Long tenantId, Long userId) {
        validateTenantExists(tenantId);
        return TenantUserResponse.from(findUser(tenantId, userId));
    }

    @Transactional
    public TenantUserResponse updateUser(Long tenantId, Long userId, UpdateTenantUserRequest request) {
        validateTenantExists(tenantId);
        User user = findUser(tenantId, userId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (!user.getUsername().equals(username)
                && userRepository.existsByTenantIdAndUsernameAndIdNot(tenantId, username, userId)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Username already exists for tenant: " + username,
                    ErrorParams.of("entityType", "User", "username", username));
        }
        if (email != null
                && !Objects.equals(user.getEmail(), email)
                && userRepository.existsByTenantIdAndEmailAndIdNot(tenantId, email, userId)) {
            throw new BusinessException(HrErrorCode.DUPLICATE_OPERATION,
                    "Email already exists for tenant: " + email,
                    ErrorParams.of("entityType", "User", "email", email));
        }

        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));

        return TenantUserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public TenantUserResponse updateUserStatus(Long tenantId, Long userId, UpdateTenantUserStatusRequest request) {
        validateTenantExists(tenantId);
        User user = findUser(tenantId, userId);
        user.setStatus(parseStatus(request.status()));

        return TenantUserResponse.from(userRepository.saveAndFlush(user));
    }

    private void validateTenantExists(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                    "Tenant not found: " + tenantId,
                    ErrorParams.of("entityType", "Tenant", "entityId", tenantId));
        }
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Tenant not found: " + tenantId,
                        ErrorParams.of("entityType", "Tenant", "entityId", tenantId)));
    }

    private User findUser(Long tenantId, Long userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "User not found for tenant: " + userId,
                        ErrorParams.of("entityType", "User", "entityId", userId)));
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
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

    private UserStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return UserStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(HrErrorCode.VALIDATION_FAILED,
                    "Invalid user status: " + status
                            + ". Allowed values: " + Arrays.toString(UserStatus.values()),
                    ErrorParams.of("field", "status", "rejectedValue", status,
                            "allowedValues", Arrays.toString(UserStatus.values())));
        }
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

    private Role findActiveRole(RoleCode roleCode) {
        validateSystemRoleTenant(roleCode);
        return roleRepository.findByCodeAndActiveTrue(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException(HrErrorCode.RESOURCE_NOT_FOUND,
                        "Role not found or inactive: " + roleCode.name(),
                        ErrorParams.of("entityType", "Role", "roleCode", roleCode.name())));
    }

    private void validateSystemRoleTenant(RoleCode roleCode) {
        if (roleCode == RoleCode.SYS_ADMIN) {
            throw new AuthorizationException(HrErrorCode.NOT_ALLOWED_FOR_ROLE,
                    "SYS_ADMIN role cannot be assigned from tenant admin APIs",
                    ErrorParams.of("roleCode", "SYS_ADMIN"));
        }
    }

    private void copyRolePermissionsToUser(Long tenantId, Long userId, Role role) {
        userPermissionService.replaceUserPermissionEntities(
                tenantId,
                userId,
                roleService.getActiveRolePermissionEntities(role.getId())
        );
    }

    private void validateRoleBranch(Role role, Long branchId) {
        if (Boolean.TRUE.equals(role.getBranchScoped()) && branchId == null) {
            throw new ValidationException(RbacErrorCode.BRANCH_REQUIRED_FOR_ROLE,
                    "Branch is required for role: " + role.getName(),
                    ErrorParams.of("roleName", role.getName()));
        }
        if (!Boolean.TRUE.equals(role.getBranchScoped()) && branchId != null) {
            throw new ValidationException(RbacErrorCode.BRANCH_NOT_ALLOWED_FOR_ROLE,
                    "Branch is not allowed for role: " + role.getName(),
                    ErrorParams.of("roleName", role.getName()));
        }
    }
}
