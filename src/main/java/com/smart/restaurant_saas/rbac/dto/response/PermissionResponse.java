package com.smart.restaurant_saas.rbac.dto.response;

import com.smart.restaurant_saas.rbac.entity.Permission;

public record PermissionResponse(
        Long id,
        String code,
        String module,
        String name,
        String description,
        String type,
        Boolean active
) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                permission.getName(),
                permission.getDescription(),
                permission.getType().name(),
                permission.getActive()
        );
    }
}
