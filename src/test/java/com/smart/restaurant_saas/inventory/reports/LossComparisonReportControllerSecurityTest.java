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
import com.smart.restaurant_saas.inventory.reports.dto.LossComparisonRow;
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
        controllers = LossComparisonReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(LossComparisonReportControllerSecurityTest.MethodSecurityConfig.class)
class LossComparisonReportControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LossComparisonReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void lossComparisonRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/loss-comparison")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void lossComparisonAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.lossComparison(eq(7L), eq(FROM), eq(TO), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/loss-comparison")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[0].materialCode").value("CHK-001"))
            .andExpect(jsonPath("$[0].materialName").value("Chicken"))
            .andExpect(jsonPath("$[0].materialNameAr").value("دجاج"))
            // Waste is a positive magnitude; shrinkage keeps its sign — in the same row.
            .andExpect(jsonPath("$[0].wasteQuantity").value("6.000000"))
            .andExpect(jsonPath("$[0].wasteValue").value("120.000000"))
            .andExpect(jsonPath("$[0].shrinkageQuantity").value("-3.000000"))
            .andExpect(jsonPath("$[0].shrinkageValue").value("-60.000000"))
            .andExpect(jsonPath("$[0].totalValue").value("180.000000"))
            .andExpect(jsonPath("$[0].uomId").value(40L))
            .andExpect(jsonPath("$[0].uomSymbol").value("kg"))
            .andExpect(jsonPath("$[0].materialActive").value(true));
    }

    @Test
    @WithMockUser
    void lossComparisonAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.lossComparison(eq(7L), eq(FROM), eq(TO), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/loss-comparison")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void lossComparisonPassesOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.lossComparison(7L, FROM, TO, 10L, 30L)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/loss-comparison")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30"))
            .andExpect(status().isOk());

        verify(service).lossComparison(7L, FROM, TO, 10L, 30L);
    }

    @Test
    @WithMockUser
    void lossComparisonSerializesACleanRowAsZerosRatherThanOmittingIt() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.lossComparison(eq(7L), eq(FROM), eq(TO), isNull(), isNull()))
            .thenReturn(List.of(LossComparisonRow.builder()
                .materialId(21L)
                .materialCode("SLT-001")
                .materialName("Salt")
                .materialActive(true)
                .wasteQuantity("0.000000")
                .wasteValue("0.000000")
                .shrinkageQuantity("0.000000")
                .shrinkageValue("0.000000")
                .totalValue("0.000000")
                .uomId(40L)
                .uomSymbol("kg")
                .build()));

        mockMvc.perform(get("/api/inventory/reports/loss-comparison")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].totalValue").value("0.000000"))
            .andExpect(jsonPath("$[0].wasteValue").value("0.000000"));
    }

    private static LossComparisonRow row() {
        return LossComparisonRow.builder()
            .materialId(20L)
            .materialCode("CHK-001")
            .materialName("Chicken")
            .materialNameAr("دجاج")
            .wasteQuantity("6.000000")
            .wasteValue("120.000000")
            .shrinkageQuantity("-3.000000")
            .shrinkageValue("-60.000000")
            .totalValue("180.000000")
            .uomId(40L)
            .uomSymbol("kg")
            .materialActive(true)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        LossComparisonReportService lossComparisonReportService() {
            return mock(LossComparisonReportService.class);
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
