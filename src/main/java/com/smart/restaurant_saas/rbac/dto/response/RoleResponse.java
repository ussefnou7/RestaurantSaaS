package com.smart.restaurant_saas.rbac.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

import com.smart.restaurant_saas.rbac.entity.Role;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String nameEn,
        String nameAr,
        String description,
        String descriptionEn,
        String descriptionAr,
        Boolean active,
        Boolean branchScoped
) {

    public static RoleResponse from(Role role) {
        String nameEn = englishOrLegacy(role.getNameEn(), role.getNameAr(), role.getName());
        String descriptionEn = englishOrLegacy(role.getDescriptionEn(), role.getDescriptionAr(), role.getDescription());
        return new RoleResponse(
                role.getId(),
                role.getCode().name(),
                firstNonBlank(role.getName(), nameEn, role.getNameAr()),
                nameEn,
                role.getNameAr(),
                firstNonBlank(role.getDescription(), descriptionEn, role.getDescriptionAr()),
                descriptionEn,
                role.getDescriptionAr(),
                role.getActive(),
                role.getBranchScoped()
        );
    }
}
