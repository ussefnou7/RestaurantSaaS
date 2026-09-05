package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The bean behind every {@code @PreAuthorize} SpEL gate.
 *
 * <p><strong>The role helpers below are live reads, despite looking like claim reads.</strong>
 * They resolve {@code CurrentUserPrincipal.roleCode()}, and {@code JwtAuthenticationFilter} stamps
 * that field with the role code it fetched from the database for this request — not the token's
 * {@code roleCode} claim. Deactivating or reassigning a role therefore takes effect on the next
 * request rather than whenever the token happens to expire.
 *
 * <p>Do not "optimise" the principal back to the token claim. That was the original defect:
 * {@code isSysAdmin()} is the one path in the system that grants everything with no repository
 * call, and it was the one path answered entirely from a login-time snapshot.
 *
 * <p>Permissions are unrelated to the role and always were — {@code hasPermission} queries a
 * direct user→permission grant. The role is a gate, not a source; it grants nothing.
 */
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
        return userPermissionRepository.existsPermissionByTenantIdAndUserIdAndCode(
                currentTenantProvider.getCurrentTenantId(),
                currentTenantProvider.getActorUserId(),
                normalizedPermissionCode
        );
    }
}
