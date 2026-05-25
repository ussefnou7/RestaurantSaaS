package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final CurrentTenantProvider currentTenantProvider;

    public Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public Long getCurrentTenantId() {
        return currentTenantProvider.getCurrentTenantId();
    }

    public String getCurrentUsername() {
        return getCurrentUser().username();
    }

    public String getCurrentRoleCode() {
        return getCurrentUser().roleCode();
    }

    public CurrentUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException("Authentication is required");
        }
        if (!(authentication.getPrincipal() instanceof CurrentUserPrincipal currentUser)) {
            throw new ApiException("Invalid authenticated user");
        }
        return currentUser;
    }
}
