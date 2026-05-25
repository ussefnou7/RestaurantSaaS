package com.smart.restaurant_saas.rbac.dto.response;

import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import java.util.List;

public record UserRoleResponse(
        Long id,
        Long tenantId,
        Long userId,
        String roleCode,
        String roleName,
        String scope,
        Long branchId,
        List<PermissionResponse> permissions
) {

    public static UserRoleResponse from(UserRole userRole, Role role, List<PermissionResponse> permissions) {
        return new UserRoleResponse(
                userRole.getId(),
                userRole.getTenantId(),
                userRole.getUserId(),
                role.getCode().name(),
                role.getName(),
                userRole.getScope().name(),
                userRole.getBranchId(),
                permissions
        );
    }
}
