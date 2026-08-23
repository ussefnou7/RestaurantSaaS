package com.smart.restaurant_saas.inventory.uom;

import com.smart.restaurant_saas.tenant.TenantHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class UomLookupVersionHeaderFilter extends OncePerRequestFilter {

    private final ObjectProvider<UomLookupVersionService> versionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);

        // Gate on an authenticated principal. X-Tenant-Id is an untrusted request header and this
        // filter runs on every request in the application, including unauthenticated login and
        // OPTIONS traffic. Without this gate any caller can vary the header to allocate a cache
        // entry and a database aggregation per distinct value. The frontend is unaffected: it only
        // loads the lookup once a session exists.
        if (!isAuthenticated() || response.isCommitted()) {
            return;
        }

        Long tenantId = parseTenantId(request.getHeader(TenantHeaders.X_TENANT_ID));
        if (tenantId == null) {
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

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Long parseTenantId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            Long tenantId = Long.valueOf(headerValue.trim());
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
