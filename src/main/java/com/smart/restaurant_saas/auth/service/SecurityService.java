package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private static final Set<String> HR_MVP_ROLES = Set.of("OWNER", "BRANCH_MANAGER");

    private final CurrentTenantProvider currentTenantProvider;
    private final UserPermissionRepository userPermissionRepository;

    public boolean isSysAdmin() {
        return currentTenantProvider.isSysAdmin();
    }

    public boolean isOwnerOrBranchManager() {
        String roleCode = currentTenantProvider.getCurrentRoleCode();
        return roleCode != null && HR_MVP_ROLES.contains(roleCode.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isOwner() {
        String roleCode = currentTenantProvider.getCurrentRoleCode();
        return roleCode != null && "OWNER".equals(roleCode.trim().toUpperCase(Locale.ROOT));
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
