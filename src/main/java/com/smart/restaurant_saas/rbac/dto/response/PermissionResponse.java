package com.smart.restaurant_saas.rbac.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.rbac.entity.Permission;

public record PermissionResponse(
        Long id,
        String code,
        String module,
        String name,
        String nameEn,
        String nameAr,
        String description,
        String descriptionEn,
        String descriptionAr,
        String type,
        Boolean active
) {

    public PermissionResponse(
            Long id,
            String code,
            String module,
            String name,
            String description,
            String type,
            Boolean active
    ) {
        this(id, code, module, name, name, null, description, description, null, type, active);
    }

    public static PermissionResponse from(Permission permission) {
        String nameEn = englishOrLegacy(permission.getNameEn(), permission.getNameAr(), permission.getName());
        String descriptionEn = englishOrLegacy(
                permission.getDescriptionEn(),
                permission.getDescriptionAr(),
                permission.getDescription()
        );
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                firstNonBlank(permission.getName(), nameEn, permission.getNameAr()),
                nameEn,
                permission.getNameAr(),
                firstNonBlank(permission.getDescription(), descriptionEn, permission.getDescriptionAr()),
                descriptionEn,
                permission.getDescriptionAr(),
                permission.getType().name(),
                permission.getActive()
        );
    }
}
