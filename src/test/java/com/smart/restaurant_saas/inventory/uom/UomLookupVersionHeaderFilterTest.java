package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.tenant.TenantHeaders;
import jakarta.servlet.FilterChain;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UomLookupVersionHeaderFilterTest {

    private static final Long TENANT_ID = 988_001L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tenant-user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void ordinaryResponseGetsLookupVersionHeader() throws Exception {
        authenticate();
        UomLookupVersionService versionService = mock(UomLookupVersionService.class);
        when(versionService.versionForTenant(TENANT_ID)).thenReturn("lookup-version-1");
        ObjectProvider<UomLookupVersionService> provider = providerFor(versionService);
        UomLookupVersionHeaderFilter filter = new UomLookupVersionHeaderFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        request.addHeader(TenantHeaders.X_TENANT_ID, TENANT_ID.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {};

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(UomLookupVersionService.RESPONSE_HEADER))
            .isEqualTo("uom=lookup-version-1");
        verify(versionService).versionForTenant(TENANT_ID);
    }

    @Test
    void requestWithoutResolvableTenantOmitsLookupVersionHeader() throws Exception {
        authenticate();
        UomLookupVersionService versionService = mock(UomLookupVersionService.class);
        ObjectProvider<UomLookupVersionService> provider = providerFor(versionService);
        UomLookupVersionHeaderFilter filter = new UomLookupVersionHeaderFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {};

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(UomLookupVersionService.RESPONSE_HEADER)).isNull();
        verify(versionService, never()).versionForTenant(TENANT_ID);
    }

    /**
     * Spring Security clears the SecurityContext in a finally block as the chain unwinds, so the
     * context is empty by the time this filter regains control. The authentication check must
     * therefore happen before the chain runs — reading it afterwards silently drops the header from
     * every response, which no other test here would catch because they use a chain that leaves the
     * context alone.
     */
    @Test
    void headerSurvivesSecurityContextBeingClearedByTheChain() throws Exception {
        authenticate();
        UomLookupVersionService versionService = mock(UomLookupVersionService.class);
        when(versionService.versionForTenant(TENANT_ID)).thenReturn("lookup-version-1");
        UomLookupVersionHeaderFilter filter =
            new UomLookupVersionHeaderFilter(providerFor(versionService));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        request.addHeader(TenantHeaders.X_TENANT_ID, TENANT_ID.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> SecurityContextHolder.clearContext());

        assertThat(response.getHeader(UomLookupVersionService.RESPONSE_HEADER))
            .isEqualTo("uom=lookup-version-1");
    }

    /**
     * X-Tenant-Id is an untrusted request header and this filter runs on every request. Without an
     * authentication gate, unauthenticated traffic varying the header allocates a cache entry and a
     * database aggregation per distinct value — unbounded growth driven from outside.
     */
    @Test
    void unauthenticatedRequestNeitherEmitsNorComputesAVersion() throws Exception {
        UomLookupVersionService versionService = mock(UomLookupVersionService.class);
        ObjectProvider<UomLookupVersionService> provider = providerFor(versionService);
        UomLookupVersionHeaderFilter filter = new UomLookupVersionHeaderFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(TenantHeaders.X_TENANT_ID, TENANT_ID.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(UomLookupVersionService.RESPONSE_HEADER)).isNull();
        verify(versionService, never()).versionForTenant(TENANT_ID);
    }

    @Test
    void anonymousRequestNeitherEmitsNorComputesAVersion() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        UomLookupVersionService versionService = mock(UomLookupVersionService.class);
        ObjectProvider<UomLookupVersionService> provider = providerFor(versionService);
        UomLookupVersionHeaderFilter filter = new UomLookupVersionHeaderFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/materials");
        request.addHeader(TenantHeaders.X_TENANT_ID, TENANT_ID.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(UomLookupVersionService.RESPONSE_HEADER)).isNull();
        verify(versionService, never()).versionForTenant(TENANT_ID);
    }

    /** The LRU must not grow without bound, however many distinct tenant ids arrive. */
    @Test
    void versionCacheIsBounded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(),
            org.mockito.ArgumentMatchers.<Object>any()))
            .thenReturn("v");

        UomLookupVersionService versionService = new UomLookupVersionService(jdbcTemplate);
        int overflow = UomLookupVersionService.MAX_CACHED_TENANTS * 2;
        for (long tenantId = 1; tenantId <= overflow; tenantId++) {
            versionService.versionForTenant(tenantId);
        }

        assertThat(versionService.cachedTenantCount())
            .isLessThanOrEqualTo(UomLookupVersionService.MAX_CACHED_TENANTS);
    }

    @Test
    void versionServiceCachesWithoutQueryingOnEveryRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
            anyString(),
            org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(),
            eq(TENANT_ID)))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                ResultSetExtractor<String> extractor = invocation.getArgument(1, ResultSetExtractor.class);
                ResultSet resultSet = mock(ResultSet.class);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getLong("row_count")).thenReturn(4L);
                when(resultSet.getLong("active_count")).thenReturn(3L);
                when(resultSet.getLong("max_id")).thenReturn(988_202L);
                when(resultSet.getTimestamp("latest_changed_at"))
                    .thenReturn(Timestamp.valueOf("2026-01-01 08:05:00"));
                return extractor.extractData(resultSet);
            });

        UomLookupVersionService versionService = new UomLookupVersionService(jdbcTemplate);

        String first = versionService.versionForTenant(TENANT_ID);
        String second = versionService.versionForTenant(TENANT_ID);
        versionService.evictTenant(TENANT_ID);
        String third = versionService.versionForTenant(TENANT_ID);

        assertThat(first).isEqualTo(second).isEqualTo(third);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
            .query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(), eq(TENANT_ID));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<UomLookupVersionService> providerFor(UomLookupVersionService versionService) {
        ObjectProvider<UomLookupVersionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(versionService);
        return provider;
    }
}
