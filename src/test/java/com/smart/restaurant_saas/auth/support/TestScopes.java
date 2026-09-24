package com.smart.restaurant_saas.auth.support;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Branch scopes for unit tests (D135).
 *
 * <p>These stub the <em>principal</em> and leave {@link CurrentUserScopeProvider}'s real logic
 * running, so a suite using {@link #branch(Long)} exercises the same resolve-and-refuse code the
 * application does. Stubbing the scope provider itself would assert only that the test's own stub
 * was called.
 */
public final class TestScopes {

    private TestScopes() {}

    /**
     * Installs an unscoped caller in the security context, for a test that calls a service
     * directly rather than through MockMvc.
     *
     * <p>Such a test needs this because a branch-filtered read now resolves its scope from the
     * principal: with no authentication there is no scope, and the service refuses rather than
     * assuming tenant-wide visibility (D135). Pair with {@link #clearAuthentication()}.
     */
    public static void authenticateTenantWide(Long tenantId) {
        authenticate(tenantId, false, null);
    }

    /** Installs a caller confined to one branch. */
    public static void authenticateBranch(Long tenantId, Long branchId) {
        authenticate(tenantId, true, branchId);
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Long tenantId, boolean branchScoped, Long branchId) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                1L, tenantId, "test-user", RoleCode.OWNER.name(), null, branchScoped, branchId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority(RoleCode.OWNER.name()))));
    }

    /** Sees every branch — the visibility every suite predating D135 was written under. */
    public static CurrentUserScopeProvider tenantWide() {
        return new CurrentUserScopeProvider(principal(false, null));
    }

    /** Confined to one branch. */
    public static CurrentUserScopeProvider branch(Long branchId) {
        return new CurrentUserScopeProvider(principal(true, branchId));
    }

    private static CurrentTenantProvider principal(boolean branchScoped, Long branchId) {
        return new CurrentTenantProvider(null, null) {
            @Override
            public boolean isSysAdmin() {
                return false;
            }

            @Override
            public boolean isBranchScoped() {
                return branchScoped;
            }

            @Override
            public Long getBranchId() {
                return branchId;
            }
        };
    }
}
