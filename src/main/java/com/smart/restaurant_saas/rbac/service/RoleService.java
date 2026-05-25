package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.UpdateRolePermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.RoleResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.RolePermission;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RolePermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<RoleResponse> listActiveRoles() {
        return roleRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getRolePermissions(String roleCode) {
        Role role = findActiveRole(roleCode);
        return getActiveRolePermissionEntities(role.getId()).stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional
    public List<PermissionResponse> updateRolePermissions(
            String roleCode,
            UpdateRolePermissionsRequest request
    ) {
        Role role = findActiveRole(roleCode);
        List<Permission> selectedPermissions = permissionService.findActivePermissionsByCodes(request.permissionCodes());

        rolePermissionRepository.deleteByRoleId(role.getId());
        List<RolePermission> rolePermissions = selectedPermissions.stream()
                .map(permission -> toRolePermission(role.getId(), permission.getId()))
                .toList();
        rolePermissionRepository.saveAll(rolePermissions);

        // TODO: Add an explicit action to apply changed defaults to existing users if the product needs it.
        return selectedPermissions.stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Role findActiveRole(String roleCode) {
        RoleCode normalizedCode = parseRoleCode(roleCode);
        return roleRepository.findByCodeAndActiveTrue(normalizedCode)
                .orElseThrow(() -> new ApiException("Role not found or inactive: " + normalizedCode.name()));
    }

    @Transactional(readOnly = true)
    public List<Permission> getActiveRolePermissionEntities(Long roleId) {
        return rolePermissionRepository.findActivePermissionsByRoleId(roleId);
    }

    private RolePermission toRolePermission(Long roleId, Long permissionId) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        return rolePermission;
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) {
            throw new ApiException("roleCode is required");
        }

        String normalizedRoleCode = roleCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedRoleCode.isEmpty()) {
            throw new ApiException("roleCode must not be blank");
        }

        try {
            return RoleCode.valueOf(normalizedRoleCode);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid roleCode: " + roleCode
                    + ". Allowed values: " + Arrays.toString(RoleCode.values()));
        }
    }
}
