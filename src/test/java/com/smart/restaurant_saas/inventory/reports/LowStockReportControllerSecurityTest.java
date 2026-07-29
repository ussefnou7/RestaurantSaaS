package com.smart.restaurant_saas.inventory.reports;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.reports.dto.LowStockRow;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = LowStockReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(LowStockReportControllerSecurityTest.MethodSecurityConfig.class)
class LowStockReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LowStockReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void lowStockRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/low-stock")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void lowStockAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.lowStock(eq(7L), isNull(), isNull(), isNull())).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/low-stock")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].warehouseId").value(10L))
            .andExpect(jsonPath("$[0].warehouseName").value("Main Warehouse"))
            .andExpect(jsonPath("$[0].warehouseNameAr").value("المستودع الرئيسي"))
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[0].materialName").value("Tomato"))
            .andExpect(jsonPath("$[0].materialNameAr").value("طماطم"))
            .andExpect(jsonPath("$[0].categoryId").value(30L))
            .andExpect(jsonPath("$[0].categoryName").value("Vegetables"))
            .andExpect(jsonPath("$[0].categoryNameAr").value("خضروات"))
            .andExpect(jsonPath("$[0].quantity").value("2.000000"))
            .andExpect(jsonPath("$[0].minQuantity").value("5.000000"))
            .andExpect(jsonPath("$[0].shortfall").value("3.000000"));

        verify(service).lowStock(eq(7L), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser
    void lowStockAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.lowStock(eq(7L), isNull(), isNull(), isNull())).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/low-stock")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void lowStockPassesOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.lowStock(7L, 1L, 10L, 30L)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/low-stock")
                .header("X-Tenant-Id", 7L)
                .queryParam("branchId", "1")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].warehouseId").value(10L));

        verify(service).lowStock(7L, 1L, 10L, 30L);
    }

    private static LowStockRow row() {
        return LowStockRow.builder()
            .warehouseId(10L)
            .warehouseName("Main Warehouse")
            .warehouseNameAr("المستودع الرئيسي")
            .materialId(20L)
            .materialName("Tomato")
            .materialNameAr("طماطم")
            .categoryId(30L)
            .categoryName("Vegetables")
            .categoryNameAr("خضروات")
            .quantity("2.000000")
            .minQuantity("5.000000")
            .shortfall("3.000000")
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        LowStockReportService lowStockReportService() {
            return mock(LowStockReportService.class);
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();
        private boolean sysAdmin = false;

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
    }
}
