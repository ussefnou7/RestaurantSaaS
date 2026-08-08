package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.rbac.RbacErrorCode;
import com.smart.restaurant_saas.rbac.dto.request.UpdateRolePermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.RoleResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.RolePermission;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RolePermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.LocalDateTime;
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
    private final TenantTimeZoneService tenantTimeZoneService;

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
                .orElseThrow(() -> new ResourceNotFoundException(RbacErrorCode.RESOURCE_NOT_FOUND,
                        "Role not found or inactive: " + normalizedCode.name(),
                        ErrorParams.of("entityType", "Role", "roleCode", normalizedCode.name())));
    }

    @Transactional(readOnly = true)
    public List<Permission> getActiveRolePermissionEntities(Long roleId) {
        return rolePermissionRepository.findActivePermissionsByRoleId(roleId);
    }

    private RolePermission toRolePermission(Long roleId, Long permissionId) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        // Stamped here rather than in a @PrePersist hook: role_permissions is a global link row
        // with no tenant, so TenantTimestampListener does not apply to it (D101).
        rolePermission.setCreatedAt(LocalDateTime.now(tenantTimeZoneService.systemZone()));
        return rolePermission;
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) {
            throw new ValidationException(RbacErrorCode.VALIDATION_FAILED,
                    "roleCode is required",
                    ErrorParams.of("field", "roleCode"));
        }

        String normalizedRoleCode = roleCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedRoleCode.isEmpty()) {
            throw new ValidationException(RbacErrorCode.VALIDATION_FAILED,
                    "roleCode must not be blank",
                    ErrorParams.of("field", "roleCode"));
        }

        try {
            return RoleCode.valueOf(normalizedRoleCode);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(RbacErrorCode.VALIDATION_FAILED,
                    "Invalid roleCode: " + roleCode
                            + ". Allowed values: " + Arrays.toString(RoleCode.values()),
                    ErrorParams.of("field", "roleCode", "rejectedValue", roleCode,
                            "allowedValues", Arrays.toString(RoleCode.values())));
        }
    }
}
