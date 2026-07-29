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
import com.smart.restaurant_saas.inventory.reports.dto.StockValuationRow;
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
        controllers = StockValuationReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(StockValuationReportControllerSecurityTest.MethodSecurityConfig.class)
class StockValuationReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockValuationReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void stockValuationRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/stock-valuation")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void stockValuationAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.stockValuation(eq(7L), isNull(), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/stock-valuation")
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
            .andExpect(jsonPath("$[0].quantity").value("12.500000"))
            .andExpect(jsonPath("$[0].averageCost").value("4.000000"))
            .andExpect(jsonPath("$[0].totalValue").value("50.000000"));

        verify(service).stockValuation(eq(7L), isNull(), isNull(), isNull());
    }

    @Test
    @WithMockUser
    void stockValuationAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.stockValuation(eq(7L), isNull(), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/stock-valuation")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void stockValuationPassesOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.stockValuation(7L, 1L, 10L, 30L)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/stock-valuation")
                .header("X-Tenant-Id", 7L)
                .queryParam("branchId", "1")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].warehouseId").value(10L));

        verify(service).stockValuation(7L, 1L, 10L, 30L);
    }

    private static StockValuationRow row() {
        return StockValuationRow.builder()
            .warehouseId(10L)
            .warehouseName("Main Warehouse")
            .warehouseNameAr("المستودع الرئيسي")
            .materialId(20L)
            .materialName("Tomato")
            .materialNameAr("طماطم")
            .categoryId(30L)
            .categoryName("Vegetables")
            .categoryNameAr("خضروات")
            .quantity("12.500000")
            .averageCost("4.000000")
            .totalValue("50.000000")
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        StockValuationReportService stockValuationReportService() {
            return mock(StockValuationReportService.class);
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
