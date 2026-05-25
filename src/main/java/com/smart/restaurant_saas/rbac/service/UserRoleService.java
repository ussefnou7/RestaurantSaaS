package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.AssignUserRoleRequest;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.UserRoleResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.PermissionScope;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final TenantRepository tenantRepository;
    private final UserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleService roleService;
    private final UserPermissionService userPermissionService;

    private static final long SYSTEM_TENANT_ID = 0L;

    @Transactional
    public UserRoleResponse assignUserRole(Long tenantId, Long userId, AssignUserRoleRequest request) {
        validateTenantAndUser(tenantId, userId);

        Role role = roleService.findActiveRole(request.roleCode());
        PermissionScope scope = parseScope(request.scope());
        validateSystemRoleTenant(role, tenantId);
        validateScopeBranch(scope, request.branchId());

        UserRole userRole = userRoleRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> newUserRole(tenantId, userId));
        userRole.setRoleId(role.getId());
        userRole.setScope(scope);
        userRole.setBranchId(request.branchId());

        UserRole savedUserRole = userRoleRepository.saveAndFlush(userRole);

        List<Permission> copiedPermissions = roleService.getActiveRolePermissionEntities(role.getId());
        userPermissionService.replaceUserPermissionEntities(tenantId, userId, copiedPermissions);

        return UserRoleResponse.from(
                savedUserRole,
                role,
                copiedPermissions.stream()
                        .map(PermissionResponse::from)
                        .toList()
        );
    }

    private UserRole newUserRole(Long tenantId, Long userId) {
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(userId);
        return userRole;
    }

    private void validateTenantAndUser(Long tenantId, Long userId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException("Tenant not found: " + tenantId);
        }
        if (appUserRepository.findByIdAndTenantId(userId, tenantId).isEmpty()) {
            throw new ApiException("User not found for tenant: " + userId);
        }
    }

    private PermissionScope parseScope(String scope) {
        if (scope == null) {
            throw new ApiException("scope is required");
        }

        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if (normalizedScope.isEmpty()) {
            throw new ApiException("scope must not be blank");
        }

        try {
            return PermissionScope.valueOf(normalizedScope);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid scope: " + scope
                    + ". Allowed values: " + Arrays.toString(PermissionScope.values()));
        }
    }

    private void validateScopeBranch(PermissionScope scope, Long branchId) {
        if (scope == PermissionScope.TENANT && branchId != null) {
            throw new ApiException("branchId must be null when scope is TENANT");
        }

        if (scope == PermissionScope.BRANCH || scope == PermissionScope.OWN) {
            // TODO: Validate branchId against the tenant branches table when branch-scoped auth is implemented.
        }
    }

    private void validateSystemRoleTenant(Role role, Long tenantId) {
        if (role.getCode() == RoleCode.SYS_ADMIN && tenantId != SYSTEM_TENANT_ID) {
            throw new ApiException("SYS_ADMIN role can only be assigned to the system tenant");
        }
        if (role.getCode() != RoleCode.SYS_ADMIN && tenantId == SYSTEM_TENANT_ID) {
            throw new ApiException("System tenant users must use SYS_ADMIN role");
        }
    }
}
