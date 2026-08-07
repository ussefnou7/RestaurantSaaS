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
import com.smart.restaurant_saas.inventory.reports.dto.WasteAnalysisRow;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = WasteAnalysisReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(WasteAnalysisReportControllerSecurityTest.MethodSecurityConfig.class)
class WasteAnalysisReportControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WasteAnalysisReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void wasteAnalysisRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/waste-analysis")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void wasteAnalysisAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.wasteAnalysis(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/waste-analysis")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[0].materialCode").value("CHK-001"))
            .andExpect(jsonPath("$[0].materialName").value("Chicken"))
            .andExpect(jsonPath("$[0].materialNameAr").value("دجاج"))
            .andExpect(jsonPath("$[0].reasonCode").value("EXPIRED"))
            .andExpect(jsonPath("$[0].netQuantity").value("-60.000000"))
            .andExpect(jsonPath("$[0].uomId").value(40L))
            .andExpect(jsonPath("$[0].uomSymbol").value("kg"))
            .andExpect(jsonPath("$[0].netValue").value("-930.000000"))
            .andExpect(jsonPath("$[0].movementCount").value(1L));
    }

    @Test
    @WithMockUser
    void wasteAnalysisAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.wasteAnalysis(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/waste-analysis")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void wasteAnalysisPassesTheReasonFilterAndOtherOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.wasteAnalysis(7L, FROM, TO, 10L, 30L, "EXPIRED", true))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/waste-analysis")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30")
                .queryParam("reasonCode", "EXPIRED")
                .queryParam("negativesOnly", "true"))
            .andExpect(status().isOk());

        verify(service).wasteAnalysis(7L, FROM, TO, 10L, 30L, "EXPIRED", true);
    }

    @Test
    @WithMockUser
    void wasteAnalysisReturnsOneRowPerReasonForTheSameMaterial() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.wasteAnalysis(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(row(), WasteAnalysisRow.builder()
                .materialId(20L)
                .materialCode("CHK-001")
                .materialName("Chicken")
                .reasonCode("SPOILED")
                .netQuantity("-20.000000")
                .uomId(40L)
                .uomSymbol("kg")
                .netValue("-310.000000")
                .movementCount(1L)
                .build()));

        // Flat, not nested: the breakdown is extra rows on the same material.
        mockMvc.perform(get("/api/inventory/reports/waste-analysis")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[1].materialId").value(20L))
            .andExpect(jsonPath("$[0].reasonCode").value("EXPIRED"))
            .andExpect(jsonPath("$[1].reasonCode").value("SPOILED"))
            .andExpect(jsonPath("$[0].reasons").doesNotExist());
    }

    private static WasteAnalysisRow row() {
        return WasteAnalysisRow.builder()
            .materialId(20L)
            .materialCode("CHK-001")
            .materialName("Chicken")
            .materialNameAr("دجاج")
            .reasonCode("EXPIRED")
            .netQuantity("-60.000000")
            .uomId(40L)
            .uomSymbol("kg")
            .netValue("-930.000000")
            .movementCount(1L)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        WasteAnalysisReportService wasteAnalysisReportService() {
            return mock(WasteAnalysisReportService.class);
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
