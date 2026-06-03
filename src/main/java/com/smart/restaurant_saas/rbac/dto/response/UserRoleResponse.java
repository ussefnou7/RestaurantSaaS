package com.smart.restaurant_saas.rbac.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import java.util.List;

public record UserRoleResponse(
        Long id,
        Long tenantId,
        Long userId,
        String roleCode,
        String roleName,
        String roleNameEn,
        String roleNameAr,
        String scope,
        Long branchId,
        List<PermissionResponse> permissions
) {

    public static UserRoleResponse from(UserRole userRole, Role role, List<PermissionResponse> permissions) {
        String roleNameEn = englishOrLegacy(role.getNameEn(), role.getNameAr(), role.getName());
        return new UserRoleResponse(
                userRole.getId(),
                userRole.getTenantId(),
                userRole.getUserId(),
                role.getCode().name(),
                firstNonBlank(role.getName(), roleNameEn, role.getNameAr()),
                roleNameEn,
                role.getNameAr(),
                userRole.getScope().name(),
                userRole.getBranchId(),
                permissions
        );
    }
}
