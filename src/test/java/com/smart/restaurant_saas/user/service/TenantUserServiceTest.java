package com.smart.restaurant_saas.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.AssignUserRoleRequest;
import com.smart.restaurant_saas.rbac.dto.response.UserRoleResponse;
import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.entity.UserRole;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.rbac.service.UserRoleService;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.dto.request.CreateUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateUserStatusRequest;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantUserServiceTest {

    private final Map<Long, User> users = new HashMap<>();
    private final Map<Long, Branch> branches = new HashMap<>();
    private final Map<RoleCode, Role> roles = new HashMap<>();
    private final Map<Long, UserRole> userRoles = new HashMap<>();
    private final AtomicLong userIds = new AtomicLong(100L);

    private StubTenantProvider currentTenantProvider;
    private RecordingUserRoleService userRoleService;
    private TenantUserService tenantUserService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();
        userRoleService = new RecordingUserRoleService(userRoles);

        roles.put(RoleCode.OWNER, role(1L, RoleCode.OWNER));
        roles.put(RoleCode.CASHIER, role(2L, RoleCode.CASHIER));
        roles.put(RoleCode.SYS_ADMIN, role(3L, RoleCode.SYS_ADMIN));

        tenantUserService = new TenantUserService(
                currentTenantProvider,
                userRepository(),
                roleRepository(),
                userRoleRepository(),
                userRoleService,
                passwordEncoder(),
                branchRepository()
        );
    }

    @Test
    void createUserUsesEffectiveTenantAndAssignsTenantRole() {
        currentTenantProvider.tenantId = 5L;

        var response = tenantUserService.createUser(new CreateUserRequest(
                "Cashier1",
                "Cashier One",
                "01000000000",
                "secret",
                "CASHIER",
                null,
                true
        ));

        User savedUser = users.get(response.id());
        assertThat(savedUser.getTenantId()).isEqualTo(5L);
        assertThat(savedUser.getUsername()).isEqualTo("cashier1");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded:secret");
        assertThat(response.role().code()).isEqualTo("CASHIER");
        assertThat(userRoleService.lastTenantId).isEqualTo(5L);
        assertThat(userRoleService.lastRequest.roleCode()).isEqualTo("CASHIER");
        assertThat(userRoleService.lastRequest.scope()).isEqualTo("TENANT");
    }

    @Test
    void createUserRejectsDuplicateUsernameWithinTenant() {
        currentTenantProvider.tenantId = 5L;
        users.put(1L, user(1L, 5L, "owner", UserStatus.ACTIVE));

        assertThatThrownBy(() -> tenantUserService.createUser(new CreateUserRequest(
                "OWNER",
                "Owner Two",
                null,
                "secret",
                "OWNER",
                null,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("Username already exists");
                });
    }

    @Test
    void createUserRejectsSysAdminRole() {
        currentTenantProvider.tenantId = 5L;

        assertThatThrownBy(() -> tenantUserService.createUser(new CreateUserRequest(
                "admin",
                "Admin",
                null,
                "secret",
                "SYS_ADMIN",
                null,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("SYS_ADMIN");
                });
    }

    @Test
    void updateStatusRejectsDisablingCurrentActor() {
        currentTenantProvider.tenantId = 5L;
        currentTenantProvider.actorUserId = 10L;
        users.put(10L, user(10L, 5L, "owner", UserStatus.ACTIVE));

        assertThatThrownBy(() -> tenantUserService.updateUserStatus(10L, new UpdateUserStatusRequest(false)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("currently authenticated user");
                });
    }

    @Test
    void deleteUserDeactivatesTenantUser() {
        currentTenantProvider.tenantId = 5L;
        currentTenantProvider.actorUserId = 10L;
        users.put(20L, user(20L, 5L, "cashier", UserStatus.ACTIVE));

        tenantUserService.deleteUser(20L);

        assertThat(users.get(20L).getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void createUserAssignsActiveBranchWhenProvided() {
        currentTenantProvider.tenantId = 5L;
        branches.put(7L, branch(7L, 5L, "main", true));

        var response = tenantUserService.createUser(new CreateUserRequest(
                "Cashier2",
                "Cashier Two",
                null,
                "secret",
                "CASHIER",
                7L,
                true
        ));

        assertThat(response.branchId()).isEqualTo(7L);
        assertThat(response.branchCode()).isEqualTo("main");
        assertThat(userRoleService.lastRequest.scope()).isEqualTo("BRANCH");
        assertThat(userRoleService.lastRequest.branchId()).isEqualTo(7L);
    }

    @Test
    void createUserRejectsInactiveBranchAssignment() {
        currentTenantProvider.tenantId = 5L;
        branches.put(7L, branch(7L, 5L, "main", false));

        assertThatThrownBy(() -> tenantUserService.createUser(new CreateUserRequest(
                "Cashier2",
                "Cashier Two",
                null,
                "secret",
                "CASHIER",
                7L,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Branch is inactive");
                });
    }

    @Test
    void getUserDoesNotReturnUsersFromAnotherTenant() {
        currentTenantProvider.tenantId = 5L;
        users.put(20L, user(20L, 9L, "cashier", UserStatus.ACTIVE));

        assertThatThrownBy(() -> tenantUserService.getUser(20L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("User not found");
                });
    }

    private UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdAndStatusNotOrderByIdDesc" -> users.values().stream()
                            .filter(user -> user.getTenantId().equals(args[0]))
                            .filter(user -> user.getStatus() != args[1])
                            .sorted((left, right) -> right.getId().compareTo(left.getId()))
                            .toList();
                    case "existsByTenantIdAndUsername" -> users.values().stream()
                            .anyMatch(user -> user.getTenantId().equals(args[0])
                                    && user.getUsername().equals(args[1]));
                    case "findByIdAndTenantIdAndStatusNot" -> Optional.ofNullable(users.get(args[0]))
                            .filter(user -> user.getTenantId().equals(args[1]))
                            .filter(user -> user.getStatus() != args[2]);
                    case "save", "saveAndFlush" -> saveUser((User) args[0]);
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
                    case "findByCodeAndActiveTrue" -> Optional.ofNullable(roles.get(args[0]))
                            .filter(Role::getActive);
                    case "findById" -> roles.values().stream()
                            .filter(role -> role.getId().equals(args[0]))
                            .findFirst();
                    case "toString" -> "RoleRepositoryStub";
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

    private BranchRepository branchRepository() {
        return (BranchRepository) Proxy.newProxyInstance(
                BranchRepository.class.getClassLoader(),
                new Class<?>[]{BranchRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndTenantId" -> Optional.ofNullable(branches.get(args[0]))
                            .filter(branch -> branch.getTenantId().equals(args[1]));
                    case "toString" -> "BranchRepositoryStub";
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

    private User saveUser(User user) {
        if (user.getId() == null) {
            user.setId(userIds.incrementAndGet());
        }
        users.put(user.getId(), user);
        return user;
    }

    private User user(Long id, Long tenantId, String username, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setFullName("User " + id);
        user.setPasswordHash("encoded:secret");
        user.setStatus(status);
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

    private Branch branch(Long id, Long tenantId, String code, boolean active) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setTenantId(tenantId);
        branch.setName("Branch " + id);
        branch.setCode(code);
        branch.setActive(active);
        return branch;
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

    private static final class RecordingUserRoleService extends UserRoleService {

        private final Map<Long, UserRole> userRoles;
        private Long lastTenantId;
        private AssignUserRoleRequest lastRequest;

        private RecordingUserRoleService(Map<Long, UserRole> userRoles) {
            super(null, null, null, null, null);
            this.userRoles = userRoles;
        }

        @Override
        public UserRoleResponse assignUserRole(Long tenantId, Long userId, AssignUserRoleRequest request) {
            this.lastTenantId = tenantId;
            this.lastRequest = request;

            UserRole userRole = new UserRole();
            userRole.setId(userId);
            userRole.setTenantId(tenantId);
            userRole.setUserId(userId);
            userRole.setRoleId(RoleCode.CASHIER.name().equals(request.roleCode()) ? 2L : 1L);
            userRole.setBranchId(request.branchId());
            userRoles.put(userId, userRole);
            return null;
        }
    }
}
