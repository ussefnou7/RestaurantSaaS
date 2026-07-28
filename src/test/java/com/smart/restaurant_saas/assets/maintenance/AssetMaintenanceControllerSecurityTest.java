package com.smart.restaurant_saas.assets.maintenance;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceListItemResponse;
import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AssetMaintenanceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(AssetMaintenanceControllerSecurityTest.MethodSecurityConfig.class)
class AssetMaintenanceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetMaintenanceService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listMaintenanceRequiresAssetsView() throws Exception {
        mockMvc.perform(get("/api/assets/maintenance").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listMaintenanceAllowsAssetsView() throws Exception {
        securityService.allow("ASSETS_VIEW");
        when(service.listMaintenance(eq(7L), eq(100L), eq(500L), eq(null), eq(3L),
                eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)),
                anyPageable()))
            .thenReturn(new PageImpl<>(List.of(listItem())));

        mockMvc.perform(get("/api/assets/maintenance")
                .header("X-Tenant-Id", 7L)
                .param("assetId", "100")
                .param("assetLineId", "500")
                .param("branchId", "3")
                .param("dateFrom", "2026-07-01")
                .param("dateTo", "2026-07-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(700L))
            .andExpect(jsonPath("$.content[0].assetName").value("Oven"))
            .andExpect(jsonPath("$.content[0].vendor").value("Acme Repairs"));

        verify(service).listMaintenance(eq(7L), eq(100L), eq(500L), eq(null), eq(3L),
            eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)), anyPageable());
    }

    @Test
    @WithMockUser
    void listMaintenanceAllowsSysadmin() throws Exception {
        securityService.sysAdmin();
        when(service.listMaintenance(eq(7L), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), anyPageable()))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/assets/maintenance").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void listMaintenanceRejectsMalformedCategory() throws Exception {
        securityService.allow("ASSETS_VIEW");

        mockMvc.perform(get("/api/assets/maintenance")
                .header("X-Tenant-Id", 7L)
                .param("category", "NOT_A_CATEGORY"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void listMaintenanceRejectsMalformedDate() throws Exception {
        securityService.allow("ASSETS_VIEW");

        mockMvc.perform(get("/api/assets/maintenance")
                .header("X-Tenant-Id", 7L)
                .param("dateFrom", "not-a-date"))
            .andExpect(status().isBadRequest());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        AssetMaintenanceService assetMaintenanceService() {
            return mock(AssetMaintenanceService.class);
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();
        private boolean sysAdmin;

        RecordingSecurityService() {
            super(null, null);
        }

        @Override
        public boolean isSysAdmin() {
            return sysAdmin;
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
            sysAdmin = false;
        }

        private void sysAdmin() {
            sysAdmin = true;
        }
    }

    private static Pageable anyPageable() {
        return org.mockito.ArgumentMatchers.any(Pageable.class);
    }

    private static AssetMaintenanceListItemResponse listItem() {
        return new AssetMaintenanceListItemResponse(700L, 100L, "Oven", "فرن",
            com.smart.restaurant_saas.assets.core.enums.AssetCategory.KITCHEN_EQUIPMENT, 3L,
            500L, "Main unit", new BigDecimal("45.000000"),
            LocalDate.of(2026, 7, 13), "Annual service", "Acme Repairs");
    }
}
