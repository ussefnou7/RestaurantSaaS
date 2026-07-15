package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import com.smart.restaurant_saas.auth.dto.response.AuthUserResponse;
import com.smart.restaurant_saas.auth.dto.response.LoginResponse;
import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.common.AuthenticationException;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
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
    private static final String POS_LOGIN_PERMISSION = "SHIFTS_OPEN";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());

        if (isBlank(request.tenantCode())) {
            return loginSystemAdmin(username, request.password(), request.deviceId());
        }

        return loginTenantUser(normalizeTenantCode(request.tenantCode()), username, request.password(), request.deviceId());
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        CurrentUserPrincipal currentUser = currentUserService.getCurrentUser();

        User user = userRepository.findByIdAndTenantId(currentUser.userId(), currentUser.tenantId())
                .orElseThrow(() -> new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                        "User not found for tenant: " + currentUser.userId(),
                        ErrorParams.of("entityType", "User", "entityId", currentUser.userId())));

        return buildAuthUserResponse(user);
    }

    private LoginResponse loginSystemAdmin(String username, String password, Long deviceId) {
        User user = userRepository.findByTenantIdAndUsername(SYSTEM_TENANT_ID, username)
                .orElseThrow(() -> invalidCredentials());
        ensureActiveUserOrFail(user);
        ensurePasswordMatchesOrFail(password, user);

        Role role = findRoleOrFail(user);
        if (role.getCode() != RoleCode.SYS_ADMIN) {
            throw invalidCredentials();
        }
        validateDeviceLoginIfRequested(user, role, deviceId);

        return buildLoginResponse(user, role);
    }

    private LoginResponse loginTenantUser(String tenantCode, String username, String password, Long deviceId) {
        Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> invalidCredentials());
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw invalidCredentials();
        }

        User user = userRepository.findByTenantIdAndUsername(tenant.getId(), username)
                .orElseThrow(() -> invalidCredentials());
        ensureActiveUserOrFail(user);
        ensurePasswordMatchesOrFail(password, user);

        Role role = findRoleOrFail(user);
        if (role.getCode() == RoleCode.SYS_ADMIN) {
            throw invalidCredentials();
        }
        validateDeviceLoginIfRequested(user, role, deviceId);

        return buildLoginResponse(user, role);
    }

    private void validateDeviceLoginIfRequested(User user, Role role, Long deviceId) {
        if (deviceId == null) {
            return;
        }

        if (!hasPermission(user, role, POS_LOGIN_PERMISSION)) {
            throw new AuthorizationException(AuthErrorCode.POS_LOGIN_NOT_PERMITTED,
                    "User is not permitted to login through a POS device",
                    ErrorParams.of("permissionCode", POS_LOGIN_PERMISSION));
        }

        Device device = deviceRepository.findByIdAndTenantId(deviceId, user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(AuthErrorCode.DEVICE_NOT_FOUND,
                        "Device not found: " + deviceId,
                        ErrorParams.of("entityType", "Device", "entityId", deviceId)));

        Long deviceBranchId = device.getBranch().getId();
        if (user.getBranchId() == null || !user.getBranchId().equals(deviceBranchId)) {
            throw new AuthorizationException(AuthErrorCode.DEVICE_BRANCH_MISMATCH,
                    "User branch does not match device branch",
                    ErrorParams.of("deviceId", deviceId,
                            "userBranchId", user.getBranchId(),
                            "deviceBranchId", deviceBranchId));
        }
    }

    private boolean hasPermission(User user, Role role, String permissionCode) {
        if (role.getCode() == RoleCode.SYS_ADMIN) {
            return true;
        }
        return userPermissionRepository.existsPermissionByTenantIdAndUserIdAndCode(
                user.getTenantId(),
                user.getId(),
                permissionCode
        );
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
        Role role = roleRepository.findById(user.getRoleId())
                .orElseThrow(() -> new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                        "Role not found for user: " + user.getId(),
                        ErrorParams.of("entityType", "Role", "entityId", user.getRoleId())));
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

    private Role findRoleOrFail(User user) {
        return roleRepository.findById(user.getRoleId())
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

    private AuthenticationException invalidCredentials() {
        return new AuthenticationException(AuthErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
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
