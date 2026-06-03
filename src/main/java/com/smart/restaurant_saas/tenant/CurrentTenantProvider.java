package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
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

    public Long getCurrentTenantId() {
        CurrentUserPrincipal currentUser = getCurrentUser();
        Long requestedTenantId = parseRequestedTenantId();

        if (isSysAdmin(currentUser)) {
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

        if (requestedTenantId != null && !Objects.equals(requestedTenantId, authenticatedTenantId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Forbidden tenant override: authenticated tenant is " + authenticatedTenantId
                            + " but " + TenantHeaders.X_TENANT_ID + " is " + requestedTenantId
            );
        }

        return validateActiveTenant(authenticatedTenantId);
    }

    public Long getActorUserId() {
        return getCurrentUser().userId();
    }

    public String getCurrentRoleCode() {
        return getCurrentUser().roleCode();
    }

    public boolean isSysAdmin() {
        return isSysAdmin(getCurrentUser());
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
