package com.smart.restaurant_saas.branch;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.branch.dto.request.CreateBranchRequest;
import com.smart.restaurant_saas.branch.dto.request.UpdateBranchStatusRequest;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.tenant.TenantCodeService;
import com.smart.restaurant_saas.tenant.TenantStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
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
    private StubUserRepository userRepository;
    private TenantCodeService tenantCodeService;
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();
        userRepository = new StubUserRepository();
        tenantCodeService = new TenantCodeService(currentTenantProvider, tenantRepository());
        branchService = new BranchService(
                currentTenantProvider,
                tenantCodeService,
                branchRepository(),
                userRepository.repository(),
                TestZones.cairo()
        );
    }

    @Test
    void createBranchUsesEffectiveTenantAndNormalizesCode() {
        currentTenantProvider.tenantId = 5L;

        var response = branchService.createBranch(new CreateBranchRequest(
                "Main Branch",
                " kfc-br-main ",
                "Address",
                "01000000000",
                null
        ));

        Branch savedBranch = branches.get(response.id());
        assertThat(savedBranch.getTenantId()).isEqualTo(5L);
        assertThat(savedBranch.getNameEn()).isEqualTo("Main Branch");
        assertThat(savedBranch.getAddressEn()).isEqualTo("Address");
        assertThat(response.nameEn()).isEqualTo("Main Branch");
        assertThat(savedBranch.getCode()).isEqualTo("KFC-BR-MAIN");
        assertThat(savedBranch.getActive()).isTrue();
    }

    @Test
    void createBranchRejectsCodeWithoutTenantPrefix() {
        assertThatThrownBy(() -> branchService.createBranch(new CreateBranchRequest(
                "Main Branch",
                "MAIN",
                null,
                null,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Code must start with KFC-BR-");
                });
    }

    @Test
    void createBranchRejectsWrongEntityPrefix() {
        assertThatThrownBy(() -> branchService.createBranch(new CreateBranchRequest(
                "Main Branch",
                "KFC-JOB-MAIN",
                null,
                null,
                true
        )))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Code must start with KFC-BR-");
                });
    }

    @Test
    void createBranchRejectsDuplicateCodeWithinTenant() {
        branches.put(1L, branch(1L, 5L, "KFC-BR-MAIN", true));

        assertThatThrownBy(() -> branchService.createBranch(new CreateBranchRequest(
                "Another Branch",
                "kfc-br-main",
                null,
                null,
                true
        )))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("Branch code already exists");
                });
    }

    @Test
    void getBranchDoesNotReturnAnotherTenantBranch() {
        branches.put(1L, branch(1L, 9L, "main", true));

        assertThatThrownBy(() -> branchService.getBranch(1L))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Branch not found");
                });
    }

    @Test
    void cannotDeactivateLastActiveBranch() {
        branches.put(1L, branch(1L, 5L, "main", true));

        assertThatThrownBy(() -> branchService.updateBranchStatus(1L, new UpdateBranchStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("last active branch");
                });
    }

    @Test
    void cannotDeactivateBranchWithActiveAssignedUsers() {
        branches.put(1L, branch(1L, 5L, "main", true));
        branches.put(2L, branch(2L, 5L, "other", true));
        userRepository.activeUserAssignedBranchId = 1L;

        assertThatThrownBy(() -> branchService.updateBranchStatus(1L, new UpdateBranchStatusRequest(false)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
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

    private TenantRepository tenantRepository() {
        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(tenant((Long) args[0]));
                    case "toString" -> "TenantRepositoryStub";
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

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("KFC");
        tenant.setCode("kfc");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
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

    private static final class StubUserRepository {

        private Long activeUserAssignedBranchId;

        private UserRepository repository() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsByTenantIdAndBranchIdAndStatus" -> args[1].equals(activeUserAssignedBranchId);
                        case "toString" -> "UserRepositoryStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
