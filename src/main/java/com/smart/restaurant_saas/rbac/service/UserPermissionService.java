package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.ReplaceUserPermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.UserPermissionsResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserPermission;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.PermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

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
        Long tenantId = getTenantId();
        findTargetUser(tenantId, userId);
        validateEditableTargetUser(tenantId, userId);

        List<Permission> selectedPermissions = findSelectedPermissions(request);

        replaceUserPermissionEntities(tenantId, userId, selectedPermissions);

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

    private List<Permission> findSelectedPermissions(ReplaceUserPermissionsRequest request) {
        return permissionService.findActivePermissionsByCodes(request.permissionCodes());
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private void validateEditableTargetUser(Long tenantId, Long userId) {
        if (userId.equals(currentTenantProvider.getActorUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot edit the currently authenticated user's permissions");
        }

        RoleCode roleCode = findTargetRoleCode(tenantId, userId);
        if (roleCode == RoleCode.SYS_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot edit SYS_ADMIN user permissions");
        }
        if (roleCode == RoleCode.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot edit OWNER user permissions in MVP");
        }
    }

    private RoleCode findTargetRoleCode(Long tenantId, Long userId) {
        return userRoleRepository.findByTenantIdAndUserId(tenantId, userId)
                .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                .map(Role::getCode)
                .orElse(null);
    }
}
