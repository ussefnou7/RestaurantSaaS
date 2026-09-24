package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentTenantProvider {

    private static final long SYSTEM_TENANT_ID = 0L;

    private final HttpServletRequest request;
    private final TenantRepository tenantRepository;

    /**
     * The single source of the effective tenant for a request.
     *
     * <p>For every principal except SYS_ADMIN the tenant comes from the signed JWT and the
     * {@code X-Tenant-Id} header is <strong>not read at all</strong> — not compared, not echoed,
     * not passed on. This is deliberate and is the whole point of the control: the previous
     * version compared the header against the JWT and rejected a mismatch, which is only a
     * defence on paths that actually reach this method. Controllers that took the raw header as
     * a {@code @RequestHeader} argument bypassed it entirely, so isolation depended on an
     * unrelated {@code @PreAuthorize} annotation happening to be present. An input that is never
     * read cannot be forged, and cannot be forgotten on one path out of 169.
     *
     * <p>SYS_ADMIN keeps header-driven tenant selection, because a platform operator has no
     * tenant of their own and must be able to name one.
     */
    public Long getCurrentTenantId() {
        CurrentUserPrincipal currentUser = getCurrentUser();

        if (isSysAdmin(currentUser)) {
            Long requestedTenantId = parseRequestedTenantId();
            if (requestedTenantId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        TenantHeaders.X_TENANT_ID + " header is required for SYS_ADMIN tenant-scoped access"
                );
            }
            return validateActiveTenant(requestedTenantId);
        }

        Long authenticatedTenantId = currentUser.tenantId();
        if (authenticatedTenantId == null || authenticatedTenantId == SYSTEM_TENANT_ID) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Authenticated tenant context is invalid");
        }

        return validateActiveTenant(authenticatedTenantId);
    }

    /**
     * The effective tenant, or {@code null} when there is no usable tenant context instead of an
     * exception. For infrastructure that runs outside a handler — filters decorating a response,
     * for example — where an unauthenticated or SYS_ADMIN-without-header request is a normal
     * condition to skip, not an error to report.
     */
    public Long getCurrentTenantIdOrNull() {
        try {
            return getCurrentTenantId();
        } catch (ApiException ex) {
            return null;
        }
    }

    public Long getActorUserId() {
        return getCurrentUser().userId();
    }

    /**
     * The role code for this request as resolved from the database by
     * {@code JwtAuthenticationFilter}, which overwrites the token's {@code roleCode} claim on the
     * principal before authenticating. Reading it here is a live read, not a claim read.
     */
    public String getCurrentRoleCode() {
        return getCurrentUser().roleCode();
    }

    /**
     * Live, for the reason given on {@link #getCurrentRoleCode()}. This matters more here than
     * anywhere else: a SYS_ADMIN answer short-circuits every permission gate in the application.
     */
    public boolean isSysAdmin() {
        return isSysAdmin(getCurrentUser());
    }

    /**
     * Whether the caller's role confines them to one branch, stamped live by
     * {@code JwtAuthenticationFilter} (D135). Live for the reason given on
     * {@link #getCurrentRoleCode()}.
     *
     * <p>This and {@link #getBranchId()} report what the principal <em>says</em>. What the caller
     * may then see is {@code CurrentUserScopeProvider}'s business, not this class's.
     */
    public boolean isBranchScoped() {
        return getCurrentUser().branchScoped();
    }

    /** The caller's branch, or null when their role is not branch-scoped. */
    public Long getBranchId() {
        return getCurrentUser().branchId();
    }

    private CurrentUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (!(authentication.getPrincipal() instanceof CurrentUserPrincipal currentUser)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
        return currentUser;
    }

    private boolean isSysAdmin(CurrentUserPrincipal currentUser) {
        return RoleCode.SYS_ADMIN.name().equals(currentUser.roleCode());
    }

    private Long parseRequestedTenantId() {
        String headerValue = request.getHeader(TenantHeaders.X_TENANT_ID);
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return null;
        }

        try {
            long tenantId = Long.parseLong(headerValue.trim());
            if (tenantId <= 0) {
                throw invalidTenantHeader();
            }
            return tenantId;
        } catch (NumberFormatException ex) {
            throw invalidTenantHeader();
        }
    }

    private Long validateActiveTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid tenant id: " + tenantId
                ));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Tenant is not active: " + tenantId
            );
        }

        return tenant.getId();
    }

    private ApiException invalidTenantHeader() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "Invalid " + TenantHeaders.X_TENANT_ID + " header: must be a positive number"
        );
    }
}
