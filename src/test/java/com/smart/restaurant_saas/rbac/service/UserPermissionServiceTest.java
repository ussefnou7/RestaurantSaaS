package com.smart.restaurant_saas.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.ReplaceUserPermissionsRequest;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserPermission;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.PermissionScope;
import com.smart.restaurant_saas.rbac.enums.PermissionType;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.PermissionRepository;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserPermissionRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantHeaders;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.tenant.TenantStatus;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UserPermissionServiceTest {

    private final Map<Long, Tenant> tenants = new HashMap<>();
    private final Map<Long, User> users = new HashMap<>();
    private final Map<Long, Permission> permissions = new HashMap<>();
    private final Map<Long, Role> roles = new HashMap<>();
    private final Map<Long, UserRole> userRoles = new HashMap<>();
    private final Set<String> userPermissions = new LinkedHashSet<>();

    private StubTenantProvider currentTenantProvider;
    private UserPermissionService userPermissionService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();

        permissions.put(1L, permission(1L, "PERMISSIONS_VIEW", true));
        permissions.put(2L, permission(2L, "USERS_VIEW", true));
        permissions.put(3L, permission(3L, "INACTIVE_PERMISSION", false));

        roles.put(1L, role(1L, RoleCode.OWNER));
        roles.put(2L, role(2L, RoleCode.CASHIER));
        roles.put(3L, role(3L, RoleCode.SYS_ADMIN));

        userPermissionService = service(currentTenantProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserPermissionsUsesResolvedTenant() {
        users.put(20L, user(20L, 5L));
        grant(5L, 20L, 1L);
        grant(9L, 20L, 2L);

        var response = userPermissionService.getUserPermissions(20L);

        assertThat(response.tenantId()).isEqualTo(5L);
        assertThat(response.userId()).isEqualTo(20L);
        assertThat(response.permissions())
                .filteredOn(permission -> permission.selected())
                .extracting("id")
                .containsExactly(1L);
    }

    @Test
    void getUserPermissionsDoesNotReturnAnotherTenantUser() {
        users.put(20L, user(20L, 9L));

        assertThatThrownBy(() -> userPermissionService.getUserPermissions(20L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("User not found");
                });
    }

    @Test
    void replaceUserPermissionsReplacesFinalEffectivePermissionsForSameTenantUser() {
        users.put(20L, user(20L, 5L));
        userRoles.put(20L, userRole(5L, 20L, 2L));
        grant(5L, 20L, 1L);

        var response = userPermissionService.replaceUserPermissions(
                20L,
                new ReplaceUserPermissionsRequest(List.of("USERS_VIEW"))
        );

        assertThat(hasGrant(5L, 20L, 1L)).isFalse();
        assertThat(hasGrant(5L, 20L, 2L)).isTrue();
        assertThat(response.permissions())
                .filteredOn(permission -> permission.selected())
                .extracting("id")
                .containsExactly(2L);
    }

    @Test
    void replaceUserPermissionsRejectsOwnerTargetForMvp() {
        users.put(20L, user(20L, 5L));
        userRoles.put(20L, userRole(5L, 20L, 1L));

        assertThatThrownBy(() -> userPermissionService.replaceUserPermissions(
                20L,
                new ReplaceUserPermissionsRequest(List.of("USERS_VIEW"))
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("OWNER");
                });
    }

    @Test
    void replaceUserPermissionsRejectsCurrentAuthenticatedUser() {
        users.put(10L, user(10L, 5L));
        userRoles.put(10L, userRole(5L, 10L, 2L));

        assertThatThrownBy(() -> userPermissionService.replaceUserPermissions(
                10L,
                new ReplaceUserPermissionsRequest(List.of("USERS_VIEW"))
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("currently authenticated user");
                });
    }

    @Test
    void replaceUserPermissionsRejectsInactivePermissionCodes() {
        users.put(20L, user(20L, 5L));
        userRoles.put(20L, userRole(5L, 20L, 2L));

        assertThatThrownBy(() -> userPermissionService.replaceUserPermissions(
                20L,
                new ReplaceUserPermissionsRequest(List.of("INACTIVE_PERMISSION"))
        ))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Permissions not found or inactive");
                });
    }

    @Test
    void sysAdminWithTenantHeaderCanAccessTenantUserPermissions() {
        CurrentTenantProvider realTenantProvider = currentTenantProviderWithRequest("9");
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);
        tenants.put(9L, tenant(9L, TenantStatus.ACTIVE));
        users.put(30L, user(30L, 9L));
        grant(9L, 30L, 1L);

        var response = service(realTenantProvider).getUserPermissions(30L);

        assertThat(response.tenantId()).isEqualTo(9L);
        assertThat(response.userId()).isEqualTo(30L);
        assertThat(response.permissions())
                .filteredOn(permission -> permission.selected())
                .extracting("id")
                .containsExactly(1L);
    }

    @Test
    void sysAdminWithoutTenantHeaderFailsTenantScopedUserPermissions() {
        CurrentTenantProvider realTenantProvider = currentTenantProviderWithRequest(null);
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);

        assertThatThrownBy(() -> service(realTenantProvider).getUserPermissions(30L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains(TenantHeaders.X_TENANT_ID);
                });
    }

    private UserPermissionService service(CurrentTenantProvider tenantProvider) {
        PermissionRepository permissionRepository = permissionRepository();
        return new UserPermissionService(
                tenantProvider,
                userRepository(),
                permissionRepository,
                userPermissionRepository(),
                new PermissionService(permissionRepository, tenantProvider),
                userRoleRepository(),
                roleRepository()
        );
    }

    private CurrentTenantProvider currentTenantProviderWithRequest(String tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (tenantHeader != null) {
            request.addHeader(TenantHeaders.X_TENANT_ID, tenantHeader);
        }
        return new CurrentTenantProvider(request, tenantRepository());
    }

    private UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndTenantId" -> Optional.ofNullable(users.get(args[0]))
                            .filter(user -> user.getTenantId().equals(args[1]));
                    case "toString" -> "UserRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PermissionRepository permissionRepository() {
        return (PermissionRepository) Proxy.newProxyInstance(
                PermissionRepository.class.getClassLoader(),
                new Class<?>[]{PermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByActiveTrueOrderByModuleAscCodeAsc" -> permissions.values().stream()
                            .filter(Permission::getActive)
                            .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                            .toList();
                    case "findByCodeInAndActiveTrue" -> permissions.values().stream()
                            .filter(permission -> ((List<?>) args[0]).contains(permission.getCode()))
                            .filter(Permission::getActive)
                            .toList();
                    case "toString" -> "PermissionRepositoryStub";
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
                    case "findPermissionIdsByTenantIdAndUserId" -> permissionIdsFor((Long) args[0], (Long) args[1]);
                    case "deleteByTenantIdAndUserId" -> {
                        deleteGrants((Long) args[0], (Long) args[1]);
                        yield null;
                    }
                    case "saveAll" -> saveAllPermissions(args[0]);
                    case "toString" -> "UserPermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepository() {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class<?>[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdAndUserId" -> Optional.ofNullable(userRoles.get(args[1]))
                            .filter(userRole -> userRole.getTenantId().equals(args[0]));
                    case "toString" -> "UserRoleRepositoryStub";
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

    private TenantRepository tenantRepository() {
        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(tenants.get(args[0]));
                    case "toString" -> "TenantRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private List<Long> permissionIdsFor(Long tenantId, Long userId) {
        return userPermissions.stream()
                .map(Grant::parse)
                .filter(grant -> grant.tenantId().equals(tenantId))
                .filter(grant -> grant.userId().equals(userId))
                .map(Grant::permissionId)
                .toList();
    }

    private void deleteGrants(Long tenantId, Long userId) {
        userPermissions.removeIf(key -> {
            Grant grant = Grant.parse(key);
            return grant.tenantId().equals(tenantId) && grant.userId().equals(userId);
        });
    }

    private List<UserPermission> saveAllPermissions(Object iterable) {
        List<UserPermission> savedPermissions = new ArrayList<>();
        for (Object item : (Iterable<?>) iterable) {
            UserPermission userPermission = (UserPermission) item;
            grant(userPermission.getTenantId(), userPermission.getUserId(), userPermission.getPermissionId());
            savedPermissions.add(userPermission);
        }
        return savedPermissions;
    }

    private void grant(Long tenantId, Long userId, Long permissionId) {
        userPermissions.add(new Grant(tenantId, userId, permissionId).key());
    }

    private boolean hasGrant(Long tenantId, Long userId, Long permissionId) {
        return userPermissions.contains(new Grant(tenantId, userId, permissionId).key());
    }

    private void authenticate(Long userId, Long tenantId, RoleCode roleCode) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                userId,
                tenantId,
                "user-" + userId,
                roleCode.name()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(roleCode.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User user(Long id, Long tenantId) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername("user-" + id);
        user.setFullName("User " + id);
        user.setPasswordHash("encoded:secret");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Permission permission(Long id, String code, boolean active) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setModule("USERS");
        permission.setName(code);
        permission.setType(PermissionType.ACTION);
        permission.setActive(active);
        return permission;
    }

    private Role role(Long id, RoleCode code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code.name());
        role.setActive(true);
        return role;
    }

    private UserRole userRole(Long tenantId, Long userId, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setScope(PermissionScope.TENANT);
        return userRole;
    }

    private Tenant tenant(Long id, TenantStatus status) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Tenant " + id);
        tenant.setCode("tenant-" + id);
        tenant.setStatus(status);
        return tenant;
    }

    private record Grant(Long tenantId, Long userId, Long permissionId) {

        private String key() {
            return tenantId + ":" + userId + ":" + permissionId;
        }

        private static Grant parse(String key) {
            String[] parts = key.split(":");
            return new Grant(Long.valueOf(parts[0]), Long.valueOf(parts[1]), Long.valueOf(parts[2]));
        }
    }

    private static final class StubTenantProvider extends CurrentTenantProvider {

        private Long tenantId = 5L;
        private Long actorUserId = 10L;

        private StubTenantProvider() {
            super((HttpServletRequest) null, null);
        }

        @Override
        public Long getCurrentTenantId() {
            return tenantId;
        }

        @Override
        public Long getActorUserId() {
            return actorUserId;
        }
    }
}
