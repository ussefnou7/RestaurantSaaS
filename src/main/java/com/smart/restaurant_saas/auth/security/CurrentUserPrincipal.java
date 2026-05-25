package com.smart.restaurant_saas.auth.security;

public record CurrentUserPrincipal(
        Long userId,
        Long tenantId,
        String username,
        String roleCode
) {
}
