package com.smart.restaurant_saas.assets.disposal;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AssetDisposalController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(AssetDisposalControllerSecurityTest.MethodSecurityConfig.class)
class AssetDisposalControllerSecurityTest {

    private static final String URL = "/api/assets/{assetId}/lines/{lineId}/disposals";
    private static final String BODY = "{\"assetId\":100,\"assetLineId\":500,"
        + "\"quantityDisposed\":4,\"reason\":\"DAMAGED\",\"disposalDate\":\"2026-07-13\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetDisposalService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void createRequiresAssetsManage() throws Exception {
        mockMvc.perform(post(URL, 100L, 500L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsAssetsManageAndPassesPathAndUser() throws Exception {
        securityService.allow("ASSETS_MANAGE");
        when(service.create(eq(100L), eq(500L),
                argThat(req -> req.getQuantityDisposed().compareTo(new BigDecimal("4")) == 0
                    && req.getReason() == AssetDisposalReason.DAMAGED),
                eq(7L), eq(99L)))
            .thenReturn(AssetDisposalResponse.builder()
                .id(900L)
                .assetId(100L)
                .assetLineId(500L)
                .quantityDisposed(new BigDecimal("4"))
                .reason(AssetDisposalReason.DAMAGED)
                .build());

        mockMvc.perform(post(URL, 100L, 500L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(900L))
            .andExpect(jsonPath("$.assetLineId").value(500L));

        verify(service).create(eq(100L), eq(500L), argThat(req ->
            req.getAssetId().equals(100L) && req.getAssetLineId().equals(500L)), eq(7L), eq(99L));
    }

    @Test
    @WithMockUser
    void createWorksWithoutOptionalUserHeader() throws Exception {
        securityService.allow("ASSETS_MANAGE");
        when(service.create(eq(100L), eq(500L), argThat(req -> true), eq(7L), eq(null)))
            .thenReturn(AssetDisposalResponse.builder().id(900L).build());

        mockMvc.perform(post(URL, 100L, 500L)
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isCreated());

        verify(service).create(eq(100L), eq(500L), argThat(req -> true), eq(7L), eq(null));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        AssetDisposalService assetDisposalService() {
            return mock(AssetDisposalService.class);
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();

        RecordingSecurityService() {
            super(null, null);
        }

        @Override
        public boolean isSysAdmin() {
            return false;
        }

        @Override
        public boolean hasPermission(String permissionCode) {
            return permissions.getOrDefault(permissionCode, false);
        }

        private void allow(String permissionCode) {
            permissions.put(permissionCode, true);
        }

        private void reset() {
            permissions.clear();
        }
    }
}
