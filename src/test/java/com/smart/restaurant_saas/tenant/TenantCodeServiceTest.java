package com.smart.restaurant_saas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TenantCodeServiceTest {

    private StubTenantProvider currentTenantProvider;
    private TenantCodeService tenantCodeService;

    @BeforeEach
    void setUp() {
        currentTenantProvider = new StubTenantProvider();
        tenantCodeService = new TenantCodeService(currentTenantProvider, tenantRepository());
    }

    @Test
    void buildPrefixUsesTenantAndEntityPrefix() {
        assertThat(tenantCodeService.buildPrefix("kfc", TenantEntityPrefix.BR)).isEqualTo("KFC-BR-");
    }

    @Test
    void normalizeSuffixUppercasesAndReplacesSpecialCharacters() {
        assertThat(tenantCodeService.normalizeSuffix(" main branch ")).isEqualTo("MAIN-BRANCH");
    }

    @Test
    void branchCodeIsAcceptedAndNormalized() {
        var validated = tenantCodeService.validateAndNormalizeCode(" kfc-br-main ", TenantEntityPrefix.BR);

        assertThat(validated.tenantId()).isEqualTo(5L);
        assertThat(validated.code()).isEqualTo("KFC-BR-MAIN");
    }

    @Test
    void branchRejectsWrongEntityPrefix() {
        assertThatThrownBy(() -> tenantCodeService.validateAndNormalizeCode("KFC-JOB-MAIN", TenantEntityPrefix.BR))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Code must start with KFC-BR-");
                });
    }

    @Test
    void jobCodeIsAcceptedAndNormalized() {
        var validated = tenantCodeService.validateAndNormalizeCode("kfc-job-cashier", TenantEntityPrefix.JOB);

        assertThat(validated.code()).isEqualTo("KFC-JOB-CASHIER");
    }

    @Test
    void jobRejectsBranchPrefix() {
        assertThatThrownBy(() -> tenantCodeService.validateAndNormalizeCode("KFC-BR-CASHIER", TenantEntityPrefix.JOB))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Code must start with KFC-JOB-");
                });
    }

    @Test
    void employeeCodeIsAcceptedAndNormalized() {
        var validated = tenantCodeService.validateAndNormalizeCode("kfc-emp-0001", TenantEntityPrefix.EMP);

        assertThat(validated.code()).isEqualTo("KFC-EMP-0001");
    }

    @Test
    void employeeRejectsBranchPrefix() {
        assertThatThrownBy(() -> tenantCodeService.validateAndNormalizeCode("KFC-BR-0001", TenantEntityPrefix.EMP))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Code must start with KFC-EMP-");
                });
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

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("KFC");
        tenant.setCode("kfc");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private static final class StubTenantProvider extends CurrentTenantProvider {

        private StubTenantProvider() {
            super((HttpServletRequest) null, null);
        }

        @Override
        public Long getCurrentTenantId() {
            return 5L;
        }
    }
}
