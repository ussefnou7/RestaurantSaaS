package com.smart.restaurant_saas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentTenantProviderTest {

    private MockHttpServletRequest request;
    private TenantRepository tenantRepository;
    private CurrentTenantProvider currentTenantProvider;
    private Map<Long, Tenant> tenants;
    private AtomicInteger findByIdCalls;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        tenants = new HashMap<>();
        findByIdCalls = new AtomicInteger();
        tenantRepository = tenantRepository();
        currentTenantProvider = new CurrentTenantProvider(request, tenantRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantUserWithoutHeaderUsesJwtTenantId() {
        authenticate(10L, 5L, RoleCode.OWNER);
        tenants.put(5L, tenant(5L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(5L);
        assertThat(currentTenantProvider.getActorUserId()).isEqualTo(10L);
        assertThat(currentTenantProvider.isSysAdmin()).isFalse();
    }

    @Test
    void tenantUserWithMatchingHeaderIsAllowed() {
        authenticate(10L, 5L, RoleCode.OWNER);
        request.addHeader(TenantHeaders.X_TENANT_ID, "5");
        tenants.put(5L, tenant(5L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(5L);
    }

    /**
     * The header is ignored, not rejected. This replaced an assertion that a mismatched header
     * produced 403 "Forbidden tenant override".
     *
     * <p>The rejection was real but was only a defence on paths that reached this method at all —
     * which, before the argument resolver landed, meant paths that happened to carry a
     * {@code @PreAuthorize}. 169 controller parameters took the raw header directly and never
     * consulted it. Ignoring the header removes the class of bug rather than one instance: a
     * value that is never read cannot be forged on the one path someone forgets to guard.
     *
     * <p>What is deliberately given up: a mismatched header no longer signals a forgery attempt.
     * A misconfigured client now succeeds quietly against its own tenant instead of failing
     * loudly. That is the accepted cost of the header not being an input.
     */
    @Test
    void tenantUserHeaderIsIgnoredEntirely() {
        authenticate(10L, 5L, RoleCode.OWNER);
        request.addHeader(TenantHeaders.X_TENANT_ID, "9");
        tenants.put(5L, tenant(5L, TenantStatus.ACTIVE));
        tenants.put(9L, tenant(9L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(5L);
    }

    /**
     * A tenant user naming a tenant that does not exist is still bound to their own. Guards the
     * failure mode where "ignored" is implemented as "fall back to the header when the JWT tenant
     * lookup is inconvenient".
     */
    @Test
    void tenantUserHeaderNamingUnknownTenantIsStillIgnored() {
        authenticate(10L, 5L, RoleCode.OWNER);
        request.addHeader(TenantHeaders.X_TENANT_ID, "404");
        tenants.put(5L, tenant(5L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(5L);
    }

    /**
     * A malformed header is a 400 for SYS_ADMIN (it is input there) but must not be able to break
     * a tenant user's request, because for them it is not input at all.
     */
    @Test
    void tenantUserWithMalformedHeaderIsUnaffected() {
        authenticate(10L, 5L, RoleCode.OWNER);
        request.addHeader(TenantHeaders.X_TENANT_ID, "not-a-number");
        tenants.put(5L, tenant(5L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(5L);
    }

    @Test
    void sysAdminWithHeaderUsesActiveRequestedTenant() {
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);
        request.addHeader(TenantHeaders.X_TENANT_ID, "9");
        tenants.put(9L, tenant(9L, TenantStatus.ACTIVE));

        assertThat(currentTenantProvider.getCurrentTenantId()).isEqualTo(9L);
        assertThat(currentTenantProvider.getActorUserId()).isEqualTo(1L);
        assertThat(currentTenantProvider.isSysAdmin()).isTrue();
    }

    @Test
    void sysAdminWithoutHeaderGetsBadRequest() {
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);

        assertThatThrownBy(() -> currentTenantProvider.getCurrentTenantId())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains(TenantHeaders.X_TENANT_ID);
                });
    }

    @Test
    void invalidTenantHeaderGetsBadRequest() {
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);
        request.addHeader(TenantHeaders.X_TENANT_ID, "not-a-number");

        assertThatThrownBy(() -> currentTenantProvider.getCurrentTenantId())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("must be a positive number");
                });
    }

    @Test
    void inactiveTenantGetsForbidden() {
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);
        request.addHeader(TenantHeaders.X_TENANT_ID, "9");
        tenants.put(9L, tenant(9L, TenantStatus.SUSPENDED));

        assertThatThrownBy(() -> currentTenantProvider.getCurrentTenantId())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("Tenant is not active");
                });
    }

    @Test
    void missingTenantGetsBadRequest() {
        authenticate(1L, 0L, RoleCode.SYS_ADMIN);
        request.addHeader(TenantHeaders.X_TENANT_ID, "9");

        assertThatThrownBy(() -> currentTenantProvider.getCurrentTenantId())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Invalid tenant id");
                });
    }

    private TenantRepository tenantRepository() {
        return (TenantRepository) Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        findByIdCalls.incrementAndGet();
                        return Optional.ofNullable(tenants.get((Long) args[0]));
                    }
                    if ("toString".equals(method.getName())) {
                        return "TenantRepositoryStub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
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

    private Tenant tenant(Long id, TenantStatus status) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName("Tenant " + id);
        tenant.setCode("tenant-" + id);
        tenant.setStatus(status);
        return tenant;
    }
}
