package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import com.smart.restaurant_saas.auth.dto.response.AuthUserResponse;
import com.smart.restaurant_saas.auth.dto.response.LoginResponse;
import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.tenant.TenantStatus;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long SYSTEM_TENANT_ID = 0L;
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());

        if (isBlank(request.tenantCode())) {
            return loginSystemAdmin(username, request.password());
        }

        return loginTenantUser(normalizeTenantCode(request.tenantCode()), username, request.password());
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        CurrentUserPrincipal currentUser = currentUserService.getCurrentUser();

        User user = userRepository.findByIdAndTenantId(currentUser.userId(), currentUser.tenantId())
                .orElseThrow(() -> new ApiException("User not found for tenant: " + currentUser.userId()));

        return buildAuthUserResponse(user);
    }

    private LoginResponse loginSystemAdmin(String username, String password) {
        User user = userRepository.findByTenantIdAndUsername(SYSTEM_TENANT_ID, username)
                .orElseThrow(() -> invalidCredentials());
        ensureActiveUserOrFail(user);
        ensurePasswordMatchesOrFail(password, user);

        Role role = findUserRoleOrFail(user);
        if (role.getCode() != RoleCode.SYS_ADMIN) {
            throw invalidCredentials();
        }

        return buildLoginResponse(user, role);
    }

    private LoginResponse loginTenantUser(String tenantCode, String username, String password) {
        Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> invalidCredentials());
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw invalidCredentials();
        }

        User user = userRepository.findByTenantIdAndUsername(tenant.getId(), username)
                .orElseThrow(() -> invalidCredentials());
        ensureActiveUserOrFail(user);
        ensurePasswordMatchesOrFail(password, user);

        Role role = findUserRoleOrFail(user);
        if (role.getCode() == RoleCode.SYS_ADMIN) {
            throw invalidCredentials();
        }

        return buildLoginResponse(user, role);
    }

    private LoginResponse buildLoginResponse(User user, Role role) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                role.getCode().name()
        );
        return new LoginResponse(accessToken, buildAuthUserResponse(user, role));
    }

    private AuthUserResponse buildAuthUserResponse(User user) {
        UserRole userRole = userRoleRepository.findByTenantIdAndUserId(user.getTenantId(), user.getId())
                .orElseThrow(() -> new ApiException("User role not assigned: " + user.getId()));
        Role role = roleRepository.findById(userRole.getRoleId())
                .orElseThrow(() -> new ApiException("Role not found for user: " + user.getId()));
        return buildAuthUserResponse(user, role);
    }

    private AuthUserResponse buildAuthUserResponse(User user, Role role) {
        List<String> permissionCodes = userPermissionRepository
                .findActivePermissionsByTenantIdAndUserId(user.getTenantId(), user.getId())
                .stream()
                .map(Permission::getCode)
                .toList();

        return new AuthUserResponse(
                user.getId(),
                user.getTenantId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                role.getCode().name(),
                permissionCodes
        );
    }

    private Role findUserRoleOrFail(User user) {
        UserRole userRole = userRoleRepository.findByTenantIdAndUserId(user.getTenantId(), user.getId())
                .orElseThrow(() -> invalidCredentials());
        return roleRepository.findById(userRole.getRoleId())
                .orElseThrow(() -> invalidCredentials());
    }

    private void ensureActiveUserOrFail(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }
    }

    private void ensurePasswordMatchesOrFail(String rawPassword, User user) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(INVALID_CREDENTIALS);
    }

    private String normalizeTenantCode(String tenantCode) {
        return tenantCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
