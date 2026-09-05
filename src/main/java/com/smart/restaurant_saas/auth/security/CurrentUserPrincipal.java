package com.smart.restaurant_saas.auth.security;

public record CurrentUserPrincipal(
        Long userId,
        Long tenantId,
        String username,
        String roleCode,
        Long deviceId
) {

    public CurrentUserPrincipal(Long userId, Long tenantId, String username, String roleCode) {
        this(userId, tenantId, username, roleCode, null);
    }
}
