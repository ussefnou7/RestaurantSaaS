package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final CurrentTenantProvider currentTenantProvider;
    private final UserPermissionRepository userPermissionRepository;

    public boolean isSysAdmin() {
        return currentTenantProvider.isSysAdmin();
    }

    public boolean hasPermission(String permissionCode) {
        if (isSysAdmin()) {
            return true;
        }
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            return false;
        }

        String normalizedPermissionCode = permissionCode.trim().toUpperCase(Locale.ROOT);
        return userPermissionRepository.existsActivePermissionByTenantIdAndUserIdAndCode(
                currentTenantProvider.getCurrentTenantId(),
                currentTenantProvider.getActorUserId(),
                normalizedPermissionCode
        );
    }
}
