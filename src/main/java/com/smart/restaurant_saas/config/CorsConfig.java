package com.smart.restaurant_saas.config;

import com.smart.restaurant_saas.inventory.uom.UomLookupVersionService;
import com.smart.restaurant_saas.tenant.TenantHeaders;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5180,http://localhost:5188,http://localhost:5173,http://192.168.1.28:5173,http://192.168.100.34:5188,http://192.168.100.34:5174}")
            String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // If-None-Match is not a CORS-safelisted request header. Without it here the preflight
        // omits it from Access-Control-Allow-Headers, the browser blocks every conditional request,
        // and revalidate-on-open (D111) fails silently — XHR reports status 0, which the caller
        // swallows, so the picker keeps serving stale units with nothing in the console.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept",
                TenantHeaders.X_TENANT_ID, "X-Branch-Id", "X-User-Id", HttpHeaders.IF_NONE_MATCH));
        // ETag so the client can read it back; X-Lookups-Version so ordinary responses can tell the
        // cache it is stale.
        configuration.setExposedHeaders(List.of("Authorization", HttpHeaders.ETAG,
                UomLookupVersionService.RESPONSE_HEADER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
