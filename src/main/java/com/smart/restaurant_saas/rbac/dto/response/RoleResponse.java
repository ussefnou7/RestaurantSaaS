package com.smart.restaurant_saas.rbac.dto.response;

import com.smart.restaurant_saas.rbac.entity.Role;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        Boolean active
) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getCode().name(),
                role.getName(),
                role.getDescription(),
                role.getActive()
        );
    }
}
