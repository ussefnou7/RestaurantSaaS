package com.smart.restaurant_saas.rbac.dto.response;

import com.smart.restaurant_saas.rbac.entity.Permission;
import java.util.List;
import java.util.Set;

public record UserPermissionsResponse(
        Long tenantId,
        Long userId,
        List<PermissionSelectionResponse> permissions
) {

    public static UserPermissionsResponse from(
            Long tenantId,
            Long userId,
            List<Permission> permissions,
            Set<Long> selectedPermissionIds
    ) {
        return new UserPermissionsResponse(
                tenantId,
                userId,
                permissions.stream()
                        .map(permission -> PermissionSelectionResponse.from(
                                permission,
                                selectedPermissionIds.contains(permission.getId())
                        ))
                        .toList()
        );
    }

    public record PermissionSelectionResponse(
            Long id,
            String code,
            String module,
            String name,
            String description,
            String type,
            Boolean active,
            Boolean selected
    ) {

        public static PermissionSelectionResponse from(Permission permission, boolean selected) {
            return new PermissionSelectionResponse(
                    permission.getId(),
                    permission.getCode(),
                    permission.getModule(),
                    permission.getName(),
                    permission.getDescription(),
                    permission.getType().name(),
                    permission.getActive(),
                    selected
            );
        }
    }
}
