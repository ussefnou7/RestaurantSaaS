package com.smart.restaurant_saas.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the {@link CurrentTenantProvider} that {@code @WebMvcTest} slices need in order to
 * resolve {@link CurrentTenantId} controller parameters. The real provider is a {@code @Service}
 * and so is absent from a web slice.
 *
 * <p>The stub resolves to a <strong>fixed</strong> tenant and deliberately does not look at
 * {@code X-Tenant-Id}. That matters: a stub that read the header would let every slice test go on
 * passing while quietly asserting the behaviour this control exists to remove, which is the
 * failure mode where a test defends an absent control. In a slice the tenant is a fixture, not an
 * input — real resolution is covered by {@link CurrentTenantProviderTest} and, end to end, by
 * {@code CrossTenantIsolationIntegrationTest}.
 *
 * <p>{@link #TENANT_ID} is 7 because that is the id the existing slice assertions were already
 * written against.
 */
@TestConfiguration
public class SliceTenantConfig {

    public static final Long TENANT_ID = 7L;

    @Bean
    public CurrentTenantProvider currentTenantProvider() {
        CurrentTenantProvider provider = mock(CurrentTenantProvider.class);
        when(provider.getCurrentTenantId()).thenReturn(TENANT_ID);
        when(provider.getCurrentTenantIdOrNull()).thenReturn(TENANT_ID);
        return provider;
    }
}
