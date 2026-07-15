package com.smart.restaurant_saas.assets.asset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.assets.asset.dto.AssetResponse;
import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
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
        controllers = AssetController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(AssetControllerSecurityTest.MethodSecurityConfig.class)
class AssetControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetService service;

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
        mockMvc.perform(post("/api/assets")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":3,\"name\":\"Oven\",\"category\":\"KITCHEN_EQUIPMENT\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsAssetsManage() throws Exception {
        securityService.allow("ASSETS_MANAGE");
        when(service.create(any(), eq(7L))).thenReturn(AssetResponse.builder()
            .id(100L)
            .branchId(3L)
            .name("Oven")
            .category(AssetCategory.KITCHEN_EQUIPMENT)
            .status(AssetStatus.ACTIVE)
            .lineCount(0)
            .totalCurrentValue(BigDecimal.ZERO)
            .build());

        mockMvc.perform(post("/api/assets")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":3,\"name\":\"Oven\",\"category\":\"KITCHEN_EQUIPMENT\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(100L))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void listRequiresAssetsView() throws Exception {
        mockMvc.perform(get("/api/assets").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsAssetsView() throws Exception {
        securityService.allow("ASSETS_VIEW");
        when(service.findAll(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/assets").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk());

        verify(service).findAll(7L);
    }

    @Test
    @WithMockUser
    void listRejectsManageOnlyUser() throws Exception {
        // D52: ASSETS_MANAGE alone must not grant read access.
        securityService.allow("ASSETS_MANAGE");

        mockMvc.perform(get("/api/assets").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRejectsViewOnlyUser() throws Exception {
        // D52: ASSETS_VIEW alone must not grant write access.
        securityService.allow("ASSETS_VIEW");

        mockMvc.perform(post("/api/assets")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":3,\"name\":\"Oven\",\"category\":\"KITCHEN_EQUIPMENT\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteRejectsViewOnlyUser() throws Exception {
        // D52: ASSETS_VIEW alone must not grant write access.
        securityService.allow("ASSETS_VIEW");

        mockMvc.perform(delete("/api/assets/{id}", 100L).header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteRequiresAssetsManage() throws Exception {
        mockMvc.perform(delete("/api/assets/{id}", 100L).header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsAssetsManage() throws Exception {
        securityService.allow("ASSETS_MANAGE");

        mockMvc.perform(delete("/api/assets/{id}", 100L).header("X-Tenant-Id", 7L))
            .andExpect(status().isNoContent());

        verify(service).delete(100L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        AssetService assetService() {
            return mock(AssetService.class);
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
