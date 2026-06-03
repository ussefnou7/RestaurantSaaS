package com.smart.restaurant_saas.rbac.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

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
            String nameEn,
            String nameAr,
            String description,
            String descriptionEn,
            String descriptionAr,
            String type,
            Boolean active,
            Boolean selected
    ) {

        public PermissionSelectionResponse(
                Long id,
                String code,
                String module,
                String name,
                String description,
                String type,
                Boolean active,
                Boolean selected
        ) {
            this(id, code, module, name, name, null, description, description, null, type, active, selected);
        }

        public static PermissionSelectionResponse from(Permission permission, boolean selected) {
            String nameEn = englishOrLegacy(permission.getNameEn(), permission.getNameAr(), permission.getName());
            String descriptionEn = englishOrLegacy(
                    permission.getDescriptionEn(),
                    permission.getDescriptionAr(),
                    permission.getDescription()
            );
            return new PermissionSelectionResponse(
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
                    permission.getActive(),
                    selected
            );
        }
    }
}
