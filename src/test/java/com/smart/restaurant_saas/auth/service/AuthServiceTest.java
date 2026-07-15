package com.smart.restaurant_saas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.enums.PermissionType;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.tenant.TenantStatus;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final Map<Long, Tenant> tenants = new HashMap<>();
    private final Map<Long, User> users = new HashMap<>();
    private final Map<Long, Role> roles = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    private final Set<String> userPermissions = new HashSet<>();
    private int permissionExistsCalls;
    private int deviceFindCalls;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        tenants.put(5L, tenant(5L, "kfc"));
        roles.put(2L, role(2L, RoleCode.CASHIER));
        roles.put(4L, role(4L, RoleCode.OWNER));
        users.put(20L, user(20L, 5L, "cashier", 2L, 7L));
        users.put(30L, user(30L, 5L, "owner", 4L, null));
        devices.put(100L, device(100L, 5L, 7L));
        devices.put(101L, device(101L, 5L, 8L));
        permissionExistsCalls = 0;
        deviceFindCalls = 0;

        authService = new AuthService(
                tenantRepository(),
                userRepository(),
                roleRepository(),
                userPermissionRepository(),
                deviceRepository(),
                passwordEncoder(),
                new JwtService("01234567890123456789012345678901", 60L),
                null
        );
    }

    @Test
    void loginWithDeviceIdAndOpenShiftPermissionAndMatchingBranchSucceeds() {
        grant(5L, 20L, "SHIFTS_OPEN");

        var response = authService.login(new LoginRequest("kfc", "cashier", "secret", 100L));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(20L);
        assertThat(response.user().roleCode()).isEqualTo("CASHIER");
    }

    @Test
    void loginWithDeviceIdRejectsUserWithoutOpenShiftPermission() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("kfc", "cashier", "secret", 100L)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("POS_LOGIN_NOT_PERMITTED");
                    assertThat(ex.getParams()).containsEntry("permissionCode", "SHIFTS_OPEN");
                });
    }

    @Test
    void loginWithDeviceIdRejectsBranchMismatch() {
        grant(5L, 20L, "SHIFTS_OPEN");

        assertThatThrownBy(() -> authService.login(new LoginRequest("kfc", "cashier", "secret", 101L)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("DEVICE_BRANCH_MISMATCH");
                    assertThat(ex.getParams()).containsEntry("userBranchId", 7L);
                    assertThat(ex.getParams()).containsEntry("deviceBranchId", 8L);
                });
    }

    @Test
    void loginWithDeviceIdRejectsNonBranchScopedUserWithNullBranch() {
        grant(5L, 30L, "SHIFTS_OPEN");

        assertThatThrownBy(() -> authService.login(new LoginRequest("kfc", "owner", "secret", 100L)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode().getCode()).isEqualTo("DEVICE_BRANCH_MISMATCH");
                    assertThat(ex.getParams()).containsEntry("userBranchId", null);
                    assertThat(ex.getParams()).containsEntry("deviceBranchId", 7L);
                });
    }

    @Test
    void loginWithoutDeviceIdDoesNotRequireOpenShiftPermissionOrBranchMatch() {
        var response = authService.login(new LoginRequest("kfc", "owner", "secret", null));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(30L);
        assertThat(response.user().roleCode()).isEqualTo("OWNER");
        assertThat(permissionExistsCalls).isZero();
        assertThat(deviceFindCalls).isZero();
    }

    private TenantRepository tenantRepository() {
        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCode" -> tenants.values().stream()
                            .filter(tenant -> tenant.getCode().equals(args[0]))
                            .findFirst();
                    case "toString" -> "TenantRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdAndUsername" -> users.values().stream()
                            .filter(user -> user.getTenantId().equals(args[0]))
                            .filter(user -> user.getUsername().equals(args[1]))
                            .findFirst();
                    case "toString" -> "UserRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleRepository roleRepository() {
        return (RoleRepository) Proxy.newProxyInstance(
                RoleRepository.class.getClassLoader(),
                new Class<?>[]{RoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(roles.get(args[0]));
                    case "toString" -> "RoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserPermissionRepository userPermissionRepository() {
        return (UserPermissionRepository) Proxy.newProxyInstance(
                UserPermissionRepository.class.getClassLoader(),
                new Class<?>[]{UserPermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsPermissionByTenantIdAndUserIdAndCode" -> {
                        permissionExistsCalls++;
                        yield userPermissions.contains(permissionKey(
                                (Long) args[0],
                                (Long) args[1],
                                (String) args[2]
                        ));
                    }
                    case "findActivePermissionsByTenantIdAndUserId" -> permissionsFor((Long) args[0], (Long) args[1]);
                    case "toString" -> "UserPermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private DeviceRepository deviceRepository() {
        return (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(),
                new Class<?>[]{DeviceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndTenantId" -> {
                        deviceFindCalls++;
                        yield Optional.ofNullable(devices.get(args[0]))
                                .filter(device -> device.getTenantId().equals(args[1]));
                    }
                    case "toString" -> "DeviceRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals(encode(rawPassword));
            }
        };
    }

    private List<Permission> permissionsFor(Long tenantId, Long userId) {
        return userPermissions.stream()
                .filter(key -> key.startsWith(tenantId + ":" + userId + ":"))
                .map(key -> permission(key.substring((tenantId + ":" + userId + ":").length())))
                .toList();
    }

    private void grant(Long tenantId, Long userId, String permissionCode) {
        userPermissions.add(permissionKey(tenantId, userId, permissionCode));
    }

    private String permissionKey(Long tenantId, Long userId, String permissionCode) {
        return tenantId + ":" + userId + ":" + permissionCode;
    }

    private Tenant tenant(Long id, String code) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(code);
        tenant.setName(code);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private User user(Long id, Long tenantId, String username, Long roleId, Long branchId) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setFullName("User " + id);
        user.setPasswordHash("encoded:secret");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoleId(roleId);
        user.setBranchId(branchId);
        return user;
    }

    private Role role(Long id, RoleCode code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code.name());
        role.setActive(true);
        return role;
    }

    private Device device(Long id, Long tenantId, Long branchId) {
        Device device = new Device();
        device.setId(id);
        device.setTenantId(tenantId);
        device.setName("POS " + id);
        device.setSecretKeyHash("secret-" + id);
        device.setActive(true);
        device.setBranch(branch(branchId, tenantId));
        return device;
    }

    private Branch branch(Long id, Long tenantId) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setTenantId(tenantId);
        branch.setCode("BR-" + id);
        branch.setName("Branch " + id);
        branch.setActive(true);
        return branch;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setModule("SHIFTS");
        permission.setName(code);
        permission.setType(PermissionType.ACTION);
        permission.setActive(true);
        return permission;
    }
}
