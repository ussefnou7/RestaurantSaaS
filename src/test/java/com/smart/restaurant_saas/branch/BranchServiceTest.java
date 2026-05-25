package com.smart.restaurant_saas.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.repository.UserRoleRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BranchServiceTest {

    private final Map<Long, Branch> branches = new HashMap<>();
    private final AtomicLong branchIds = new AtomicLong(100L);

    private StubTenantProvider currentTenantProvider;
    private StubUserRoleRepository userRoleRepository;
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();
        userRoleRepository = new StubUserRoleRepository();
        branchService = new BranchService(currentTenantProvider, branchRepository(), userRoleRepository.repository());
    }

    @Test
    void createBranchUsesEffectiveTenantAndNormalizesCode() {
        currentTenantProvider.tenantId = 5L;

        var response = branchService.createBranch(new CreateBranchRequest(
                "Main Branch",
                " MAIN ",
                "Address",
                "01000000000",
                null
        ));

        Branch savedBranch = branches.get(response.id());
        assertThat(savedBranch.getTenantId()).isEqualTo(5L);
        assertThat(savedBranch.getCode()).isEqualTo("main");
        assertThat(savedBranch.getActive()).isTrue();
    }

    @Test
    void createBranchRejectsDuplicateCodeWithinTenant() {
        branches.put(1L, branch(1L, 5L, "main", true));

        assertThatThrownBy(() -> branchService.createBranch(new CreateBranchRequest(
                "Another Branch",
                "main",
                null,
                null,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("Branch code already exists");
                });
    }

    @Test
    void getBranchDoesNotReturnAnotherTenantBranch() {
        branches.put(1L, branch(1L, 9L, "main", true));

        assertThatThrownBy(() -> branchService.getBranch(1L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Branch not found");
                });
    }

    @Test
    void cannotDeactivateLastActiveBranch() {
        branches.put(1L, branch(1L, 5L, "main", true));

        assertThatThrownBy(() -> branchService.updateBranchStatus(1L, new UpdateBranchStatusRequest(false)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("last active branch");
                });
    }

    @Test
    void cannotDeactivateBranchWithActiveAssignedUsers() {
        branches.put(1L, branch(1L, 5L, "main", true));
        branches.put(2L, branch(2L, 5L, "other", true));
        userRoleRepository.activeUserAssignedBranchId = 1L;

        assertThatThrownBy(() -> branchService.updateBranchStatus(1L, new UpdateBranchStatusRequest(false)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("active users");
                });
    }

    @Test
    void deactivateBranchWhenAnotherActiveBranchExistsAndNoActiveUsersAssigned() {
        branches.put(1L, branch(1L, 5L, "main", true));
        branches.put(2L, branch(2L, 5L, "other", true));

        var response = branchService.updateBranchStatus(1L, new UpdateBranchStatusRequest(false));

        assertThat(response.active()).isFalse();
        assertThat(branches.get(1L).getActive()).isFalse();
    }

    private BranchRepository branchRepository() {
        return (BranchRepository) Proxy.newProxyInstance(
                BranchRepository.class.getClassLoader(),
                new Class<?>[]{BranchRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTenantIdOrderByIdDesc" -> branches.values().stream()
                            .filter(branch -> branch.getTenantId().equals(args[0]))
                            .sorted((left, right) -> right.getId().compareTo(left.getId()))
                            .toList();
                    case "findByIdAndTenantId" -> Optional.ofNullable(branches.get(args[0]))
                            .filter(branch -> branch.getTenantId().equals(args[1]));
                    case "existsByTenantIdAndCode" -> branches.values().stream()
                            .anyMatch(branch -> branch.getTenantId().equals(args[0])
                                    && branch.getCode().equals(args[1]));
                    case "existsByTenantIdAndCodeAndIdNot" -> branches.values().stream()
                            .anyMatch(branch -> branch.getTenantId().equals(args[0])
                                    && branch.getCode().equals(args[1])
                                    && !branch.getId().equals(args[2]));
                    case "countByTenantIdAndActiveTrue" -> branches.values().stream()
                            .filter(branch -> branch.getTenantId().equals(args[0]))
                            .filter(branch -> Boolean.TRUE.equals(branch.getActive()))
                            .count();
                    case "save", "saveAndFlush" -> saveBranch((Branch) args[0]);
                    case "toString" -> "BranchRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Branch saveBranch(Branch branch) {
        if (branch.getId() == null) {
            branch.setId(branchIds.incrementAndGet());
        }
        branches.put(branch.getId(), branch);
        return branch;
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

        private StubTenantProvider() {
            super((HttpServletRequest) null, null);
        }

        @Override
        public Long getCurrentTenantId() {
            return tenantId;
        }
    }

    private static final class StubUserRoleRepository {

        private Long activeUserAssignedBranchId;

        private UserRoleRepository repository() {
            return (UserRoleRepository) Proxy.newProxyInstance(
                    UserRoleRepository.class.getClassLoader(),
                    new Class<?>[]{UserRoleRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsActiveUserAssignedToBranch" -> args[1].equals(activeUserAssignedBranchId);
                        case "toString" -> "UserRoleRepositoryStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
