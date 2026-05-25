package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.ReplaceUserPermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.UserPermissionsResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.UserPermission;
import com.smart.restaurant_saas.rbac.repository.PermissionRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final TenantRepository tenantRepository;
    private final UserRepository appUserRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public UserPermissionsResponse getUserPermissions(Long tenantId, Long userId) {
        validateTenantAndUser(tenantId, userId);
        return buildUserPermissionsResponse(tenantId, userId);
    }

    @Transactional
    public UserPermissionsResponse replaceUserPermissions(
            Long tenantId,
            Long userId,
            ReplaceUserPermissionsRequest request
    ) {
        validateTenantAndUser(tenantId, userId);
        List<Permission> selectedPermissions = permissionService.findActivePermissionsByCodes(request.permissionCodes());

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

    private UserPermission toUserPermission(Long tenantId, Long userId, Long permissionId) {
        UserPermission userPermission = new UserPermission();
        userPermission.setTenantId(tenantId);
        userPermission.setUserId(userId);
        userPermission.setPermissionId(permissionId);
        return userPermission;
    }

    private void validateTenantAndUser(Long tenantId, Long userId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException("Tenant not found: " + tenantId);
        }
        if (appUserRepository.findByIdAndTenantId(userId, tenantId).isEmpty()) {
            throw new ApiException("User not found for tenant: " + userId);
        }
    }
}
