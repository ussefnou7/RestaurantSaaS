package com.smart.restaurant_saas.user.dto.response;

import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import java.time.LocalDateTime;

public record TenantUserResponse(
        Long id,
        Long tenantId,
        String fullName,
        String username,
        String email,
        String phone,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TenantUserResponse from(User user) {
        return new TenantUserResponse(
                user.getId(),
                user.getTenantId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
