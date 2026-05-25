package com.smart.restaurant_saas.user.dto.response;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        RoleResponse role,
        Long branchId,
        String branchName,
        String branchCode,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(User user, Role role) {
        return from(user, role, null);
    }

    public static UserResponse from(User user, Role role, Branch branch) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPhone(),
                role == null ? null : RoleResponse.from(role),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                branch == null ? null : branch.getCode(),
                user.getStatus() == UserStatus.ACTIVE,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public record RoleResponse(
            Long id,
            String code,
            String name
    ) {

        public static RoleResponse from(Role role) {
            return new RoleResponse(
                    role.getId(),
                    role.getCode().name(),
                    role.getName()
            );
        }
    }
}
