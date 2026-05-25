package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.PermissionScope;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserScopeProvider {

    private final CurrentTenantProvider currentTenantProvider;
    private final UserRoleRepository userRoleRepository;

    public boolean isTenantScoped() {
        if (currentTenantProvider.isSysAdmin()) {
            return true;
        }
        UserRole userRole = getCurrentUserRole();
        return userRole.getScope() == PermissionScope.TENANT;
    }

    public Optional<Long> getCurrentBranchId() {
        if (currentTenantProvider.isSysAdmin()) {
            return Optional.empty();
        }
        UserRole userRole = getCurrentUserRole();
        if (userRole.getScope() == PermissionScope.BRANCH) {
            return Optional.ofNullable(userRole.getBranchId());
        }
        return Optional.empty();
    }

    public void ensureCanAccessBranch(Long branchId) {
        if (isTenantScoped()) {
            return;
        }
        Long currentBranchId = getCurrentBranchId()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Branch scope is required"));
        if (!currentBranchId.equals(branchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Access to this branch is forbidden");
        }
    }

    private UserRole getCurrentUserRole() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Long userId = currentTenantProvider.getActorUserId();
        return userRoleRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Current user role scope is not assigned"));
    }
}
