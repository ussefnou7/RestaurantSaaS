package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.rbac.dto.request.ReplaceUserPermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.UserPermissionsResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserPermission;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.PermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RolePermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.smart.restaurant_saas.rbac.RbacErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final CurrentTenantProvider currentTenantProvider;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionService permissionService;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public UserPermissionsResponse getUserPermissions(Long userId) {
        Long tenantId = getTenantId();
        findTargetUser(tenantId, userId);
        return buildUserPermissionsResponse(tenantId, userId);
    }

    @Transactional
    public UserPermissionsResponse replaceUserPermissions(
            Long userId,
            ReplaceUserPermissionsRequest request
    ) {
        return replaceUserPermissions(userId, request.permissionCodes());
    }

    @Transactional
    public UserPermissionsResponse replaceUserPermissions(Long userId, List<String> permissionCodes) {
        Long tenantId = getTenantId();
        findTargetUser(tenantId, userId);
        validateEditableTargetUser(tenantId, userId);

        List<Permission> selectedPermissions = permissionService.findActivePermissionsByCodes(permissionCodes);

        replaceUserPermissionEntities(tenantId, userId, selectedPermissions);

        return buildUserPermissionsResponse(tenantId, userId);
    }

    @Transactional
    public UserPermissionsResponse resetUserPermissionsToRoleDefaults(Long userId) {
        Long tenantId = getTenantId();
        User user = findTargetUser(tenantId, userId);
        validateEditableTargetUser(user);

        List<UserPermission> userPermissions = rolePermissionRepository.findPermissionIdsByRoleId(user.getRoleId())
                .stream()
                .map(permissionId -> toUserPermission(tenantId, userId, permissionId))
                .toList();
        userPermissionRepository.deleteByTenantIdAndUserId(tenantId, userId);
        userPermissionRepository.saveAll(userPermissions);

        return buildUserPermissionsResponse(tenantId, userId);
    }

    @Transactional
    public void replaceUserPermissionEntities(
            Long tenantId,
            Long userId,
            List<Permission> selectedPermissions
    ) {
        userPermissionRepository.deleteByTenantIdAndUserId(tenantId, userId);

        List<UserPermission> userPermissions = selectedPermissions.stream()
                .map(permission -> toUserPermission(tenantId, userId, permission.getId()))
                .toList();
        userPermissionRepository.saveAll(userPermissions);
    }

    private UserPermissionsResponse buildUserPermissionsResponse(Long tenantId, Long userId) {
        List<Permission> activePermissions = permissionRepository.findByActiveTrueOrderByModuleAscCodeAsc();
        Set<Long> selectedPermissionIds = new HashSet<>(
                userPermissionRepository.findPermissionIdsByTenantIdAndUserId(tenantId, userId)
        );

        return UserPermissionsResponse.from(tenantId, userId, activePermissions, selectedPermissionIds);
    }

    private Long getTenantId() {
        return currentTenantProvider.getCurrentTenantId();
    }

    private UserPermission toUserPermission(Long tenantId, Long userId, Long permissionId) {
        UserPermission userPermission = new UserPermission();
        userPermission.setTenantId(tenantId);
        userPermission.setUserId(userId);
        userPermission.setPermissionId(permissionId);
        return userPermission;
    }

    private User findTargetUser(Long tenantId, Long userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(RbacErrorCode.RESOURCE_NOT_FOUND,
                        "User not found: " + userId,
                        ErrorParams.of("entityType", "User", "entityId", userId)));
    }

    private void validateEditableTargetUser(Long tenantId, Long userId) {
        validateEditableTargetUser(findTargetUser(tenantId, userId));
    }

    private void validateEditableTargetUser(User user) {
        Long userId = user.getId();
        if (userId.equals(currentTenantProvider.getActorUserId())) {
            throw new AuthorizationException(RbacErrorCode.SELF_ACTION_BLOCKED,
                    "Cannot edit the currently authenticated user's permissions",
                    ErrorParams.of("action", "editPermissions"));
        }

        RoleCode roleCode = findTargetRoleCode(user);
        if (roleCode == RoleCode.SYS_ADMIN) {
            throw new AuthorizationException(RbacErrorCode.NOT_ALLOWED_FOR_ROLE,
                    "Cannot edit SYS_ADMIN user permissions",
                    ErrorParams.of("roleCode", "SYS_ADMIN"));
        }
        if (roleCode == RoleCode.OWNER) {
            throw new AuthorizationException(RbacErrorCode.NOT_ALLOWED_FOR_ROLE,
                    "Cannot edit OWNER user permissions in MVP",
                    ErrorParams.of("roleCode", "OWNER"));
        }
    }

    private RoleCode findTargetRoleCode(User user) {
        return roleRepository.findById(user.getRoleId())
                .map(Role::getCode)
                .orElse(null);
    }
}
