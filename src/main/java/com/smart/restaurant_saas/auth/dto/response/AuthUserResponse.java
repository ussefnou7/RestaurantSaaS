package com.smart.restaurant_saas.auth.dto.response;

import java.util.List;

public record AuthUserResponse(
        Long id,
        Long tenantId,
        String fullName,
        String username,
        String email,
        String phone,
        String roleCode,
        List<String> permissions
) {
}
