package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserScopeProvider {

    private final CurrentTenantProvider currentTenantProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public boolean isTenantScoped() {
        if (currentTenantProvider.isSysAdmin()) {
            return true;
        }
        User user = getCurrentUser();
        Role role = getRole(user);
        return !Boolean.TRUE.equals(role.getBranchScoped());
    }

    public Optional<Long> getCurrentBranchId() {
        if (currentTenantProvider.isSysAdmin()) {
            return Optional.empty();
        }
        User user = getCurrentUser();
        Role role = getRole(user);
        if (Boolean.TRUE.equals(role.getBranchScoped())) {
            return Optional.ofNullable(user.getBranchId());
        }
        return Optional.empty();
    }

    public void ensureCanAccessBranch(Long branchId) {
        if (isTenantScoped()) {
            return;
        }
        Long currentBranchId = getCurrentBranchId()
                .orElseThrow(() -> new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                        "Branch scope is required",
                        ErrorParams.of("field", "branchId")));
        if (!currentBranchId.equals(branchId)) {
            throw new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                    "Access to this branch is forbidden",
                    ErrorParams.of("branchId", branchId));
        }
    }

    private User getCurrentUser() {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Long userId = currentTenantProvider.getActorUserId();
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                        "Current user is not assigned",
                        ErrorParams.of("entityType", "User", "entityId", userId)));
    }

    private Role getRole(User user) {
        return roleRepository.findById(user.getRoleId())
                .orElseThrow(() -> new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                        "Current user role is not assigned",
                        ErrorParams.of("entityType", "Role", "entityId", user.getRoleId())));
    }
}
