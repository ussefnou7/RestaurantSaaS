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
import com.smart.restaurant_saas.inventory.reports.dto.ShrinkageRow;
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
        controllers = ShrinkageReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShrinkageReportControllerSecurityTest.MethodSecurityConfig.class)
class ShrinkageReportControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShrinkageReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void shrinkageRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/shrinkage")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void shrinkageAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.shrinkage(eq(7L), eq(FROM), eq(TO), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/shrinkage")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[0].materialCode").value("CHK-001"))
            .andExpect(jsonPath("$[0].materialName").value("Chicken"))
            .andExpect(jsonPath("$[0].materialNameAr").value("دجاج"))
            .andExpect(jsonPath("$[0].netQuantity").value("-7.000000"))
            .andExpect(jsonPath("$[0].uomId").value(40L))
            .andExpect(jsonPath("$[0].uomSymbol").value("kg"))
            .andExpect(jsonPath("$[0].netValue").value("-140.000000"))
            .andExpect(jsonPath("$[0].movementCount").value(2L));
    }

    @Test
    @WithMockUser
    void shrinkageAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.shrinkage(eq(7L), eq(FROM), eq(TO), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/shrinkage")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void shrinkagePassesOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.shrinkage(7L, FROM, TO, 10L, 30L, true)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/shrinkage")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30")
                .queryParam("negativesOnly", "true"))
            .andExpect(status().isOk());

        verify(service).shrinkage(7L, FROM, TO, 10L, 30L, true);
    }

    @Test
    @WithMockUser
    void shrinkageSerializesAnUnconvertibleRowAsNullQuantityWithItsValueIntact() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.shrinkage(eq(7L), eq(FROM), eq(TO), isNull(), isNull(), eq(false)))
            .thenReturn(List.of(ShrinkageRow.builder()
                .materialId(21L)
                .materialCode("MIX-009")
                .materialName("Misconfigured")
                .netValue("-310.000000")
                .movementCount(1L)
                .build()));

        mockMvc.perform(get("/api/inventory/reports/shrinkage")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            // The frontend counts affected rows by testing exactly this.
            .andExpect(jsonPath("$[0].netQuantity").doesNotExist())
            .andExpect(jsonPath("$[0].uomId").doesNotExist())
            .andExpect(jsonPath("$[0].uomSymbol").doesNotExist())
            .andExpect(jsonPath("$[0].netValue").value("-310.000000"));
    }

    private static ShrinkageRow row() {
        return ShrinkageRow.builder()
            .materialId(20L)
            .materialCode("CHK-001")
            .materialName("Chicken")
            .materialNameAr("دجاج")
            .netQuantity("-7.000000")
            .uomId(40L)
            .uomSymbol("kg")
            .netValue("-140.000000")
            .movementCount(2L)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ShrinkageReportService shrinkageReportService() {
            return mock(ShrinkageReportService.class);
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
