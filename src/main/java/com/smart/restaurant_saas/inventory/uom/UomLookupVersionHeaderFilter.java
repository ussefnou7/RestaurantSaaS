package com.smart.restaurant_saas.inventory.uom;

import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class UomLookupVersionHeaderFilter extends OncePerRequestFilter {

    private final ObjectProvider<UomLookupVersionService> versionService;
    private final ObjectProvider<CurrentTenantProvider> currentTenantProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // Resolve BEFORE the chain runs. Spring Security clears the SecurityContext in its own
        // finally block, so by the time control returns here the context is empty and every
        // response would silently lose the header.
        //
        // The tenant comes from CurrentTenantProvider, not from X-Tenant-Id. This filter used to
        // read the raw header, which let any caller vary it to allocate a cache entry and a
        // database aggregation per distinct value, and to learn the lookup version of a tenant
        // that is not theirs. Resolving instead of reading closes both: a tenant principal can
        // only ever name its own tenant. The null return also subsumes the old authentication
        // gate, since an unauthenticated request has no resolvable tenant.
        CurrentTenantProvider tenantProvider = currentTenantProvider.getIfAvailable();
        Long tenantId = tenantProvider == null ? null : tenantProvider.getCurrentTenantIdOrNull();

        filterChain.doFilter(request, response);

        if (tenantId == null || response.isCommitted()) {
            return;
        }

        UomLookupVersionService resolver = versionService.getIfAvailable();
        if (resolver == null) {
            return;
        }

        String version = resolver.versionForTenant(tenantId);
        response.setHeader(
            UomLookupVersionService.RESPONSE_HEADER,
            UomLookupVersionService.lookupHeaderValue(version));
    }
}
