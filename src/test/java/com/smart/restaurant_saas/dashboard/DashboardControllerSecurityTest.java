package com.smart.restaurant_saas.dashboard;

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
import com.smart.restaurant_saas.dashboard.alert.AlertCode;
import com.smart.restaurant_saas.dashboard.alert.AlertLinkTarget;
import com.smart.restaurant_saas.dashboard.alert.AlertService;
import com.smart.restaurant_saas.dashboard.alert.AlertSeverity;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertGroup;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertRow;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertsResponse;
import com.smart.restaurant_saas.dashboard.dto.DashboardBlock;
import com.smart.restaurant_saas.dashboard.dto.DashboardKpis;
import com.smart.restaurant_saas.dashboard.dto.DashboardSummaryResponse;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * The gate on both dashboard endpoints, and the shape of what comes back through it.
 *
 * <p>Two things are asserted that are easy to get wrong in opposite directions. First, both
 * endpoints need {@code DASHBOARD_VIEW} — the summary is a money screen and the alerts name
 * cashiers who are repeatedly short, so neither may be open. Second, {@code DASHBOARD_VIEW} alone
 * must still produce a usable response rather than a 403 on every field: the per-block permissions
 * live inside the service, and a block the caller cannot see comes back as
 * {@code HIDDEN_NO_PERMISSION}. A design where the controller refused outright would make the
 * dashboard unusable for every role except owner.
 */
@WebMvcTest(
        controllers = DashboardController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({DashboardControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class DashboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardSummaryService summaryService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(summaryService, alertService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    @DisplayName("the summary needs DASHBOARD_VIEW")
    void summaryRequiresDashboardViewPermission() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                .queryParam("from", "2026-03-01")
                .queryParam("to", "2026-03-31"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("the alerts need DASHBOARD_VIEW")
    void alertsRequireDashboardViewPermission() throws Exception {
        mockMvc.perform(get("/api/dashboard/alerts"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("a sysadmin reaches both without an explicit grant")
    void sysAdminBypassesThePermission() throws Exception {
        securityService.sysAdmin = true;
        when(summaryService.summary(eq(7L), eq(LocalDate.of(2026, 3, 1)),
            eq(LocalDate.of(2026, 3, 31)), isNull())).thenReturn(summary());
        when(alertService.alerts(eq(7L), isNull())).thenReturn(alerts());

        mockMvc.perform(get("/api/dashboard/summary")
                .queryParam("from", "2026-03-01")
                .queryParam("to", "2026-03-31"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/alerts"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("the summary passes the range and branch through untouched")
    void summaryPassesParametersThrough() throws Exception {
        securityService.allow("DASHBOARD_VIEW");
        when(summaryService.summary(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 5L))
            .thenReturn(summary());

        mockMvc.perform(get("/api/dashboard/summary")
                .queryParam("from", "2026-03-01")
                .queryParam("to", "2026-03-31")
                .queryParam("branchId", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bucket").value("DAY"))
            .andExpect(jsonPath("$.kpis.status").value("OK"))
            .andExpect(jsonPath("$.kpis.data.netSales").value("906.500000"));

        verify(summaryService).summary(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 5L);
    }

    @Test
    @WithMockUser
    @DisplayName("a withheld block serialises its status and a null payload, never zeros")
    void withheldBlockIsDistinguishableFromAnEmptyOne() throws Exception {
        securityService.allow("DASHBOARD_VIEW");
        when(summaryService.summary(eq(7L), eq(LocalDate.of(2026, 3, 1)),
            eq(LocalDate.of(2026, 3, 31)), isNull())).thenReturn(summaryWithHiddenCostCoverage());

        mockMvc.perform(get("/api/dashboard/summary")
                .queryParam("from", "2026-03-01")
                .queryParam("to", "2026-03-31"))
            .andExpect(status().isOk())
            // The contract the frontend depends on to say "withheld" instead of rendering 0%.
            .andExpect(jsonPath("$.costCoverage.status").value("HIDDEN_NO_PERMISSION"))
            .andExpect(jsonPath("$.costCoverage.data").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("alerts take no date range, and expose severity, link and withheld codes")
    void alertsExposeTheStripContract() throws Exception {
        securityService.allow("DASHBOARD_VIEW");
        when(alertService.alerts(eq(7L), isNull())).thenReturn(alerts());

        mockMvc.perform(get("/api/dashboard/alerts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].code").value("UNPOSTED_CONSUMPTION"))
            .andExpect(jsonPath("$.groups[0].severity").value("CRITICAL"))
            .andExpect(jsonPath("$.groups[0].valueIsEstimate").value(true))
            .andExpect(jsonPath("$.groups[0].link").value("ORDER_CONSUMPTION_LIST"))
            .andExpect(jsonPath("$.groups[0].rows[0].warehouseName").value("Nasr City Kitchen"))
            .andExpect(jsonPath("$.groups[0].rows[0].linkParams.warehouseId").value(996401))
            // An empty strip means "nothing is wrong" only when withheld is also empty, so the
            // frontend has to be able to read it.
            .andExpect(jsonPath("$.withheld[0]").value("CASH_VARIANCE"))
            .andExpect(jsonPath("$.evaluatedAt").exists());

        verify(alertService).alerts(7L, null);
    }

    @Test
    @WithMockUser
    @DisplayName("alerts narrow by branch when asked")
    void alertsPassBranchThrough() throws Exception {
        securityService.allow("DASHBOARD_VIEW");
        when(alertService.alerts(7L, 5L)).thenReturn(alerts());

        mockMvc.perform(get("/api/dashboard/alerts").queryParam("branchId", "5"))
            .andExpect(status().isOk());

        verify(alertService).alerts(7L, 5L);
    }

    @Test
    @WithMockUser
    void alertFailureIsAnErrorResponseNotAnEmptySuccessfulStrip() throws Exception {
        securityService.allow("DASHBOARD_VIEW");
        when(alertService.alerts(eq(7L), isNull())).thenThrow(new IllegalStateException("query failed"));
        mockMvc.perform(get("/api/dashboard/alerts"))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.groups").doesNotExist());
    }

    private static DashboardSummaryResponse summary() {
        return DashboardSummaryResponse.builder()
            .from(LocalDate.of(2026, 3, 1))
            .to(LocalDate.of(2026, 3, 31))
            .bucket(TrendBucket.DAY)
            .generatedAt(LocalDateTime.of(2026, 3, 31, 18, 0))
            .kpis(DashboardBlock.of(DashboardKpis.builder()
                .orderCount(5L)
                .netSales("906.500000")
                .taxAmount("126.920000")
                .totalAmount("1033.420000")
                .averageOrderValue("206.684000")
                .build()))
            .build();
    }

    private static DashboardSummaryResponse summaryWithHiddenCostCoverage() {
        return DashboardSummaryResponse.builder()
            .from(LocalDate.of(2026, 3, 1))
            .to(LocalDate.of(2026, 3, 31))
            .bucket(TrendBucket.DAY)
            .generatedAt(LocalDateTime.of(2026, 3, 31, 18, 0))
            .kpis(DashboardBlock.of(DashboardKpis.builder().orderCount(5L).build()))
            .costCoverage(DashboardBlock.hidden())
            .build();
    }

    private static DashboardAlertsResponse alerts() {
        return DashboardAlertsResponse.builder()
            .group(DashboardAlertGroup.builder()
                .code(AlertCode.UNPOSTED_CONSUMPTION)
                .severity(AlertSeverity.CRITICAL)
                .count(4)
                .value("12400.000000")
                .valueIsEstimate(true)
                .oldestAt(LocalDateTime.of(2026, 3, 29, 9, 15))
                .link(AlertLinkTarget.ORDER_CONSUMPTION_LIST)
                .row(DashboardAlertRow.builder()
                    .branchId(996101L)
                    .branchName("Nasr City")
                    .branchNameAr("مدينة نصر")
                    .warehouseId(996401L)
                    .warehouseName("Nasr City Kitchen")
                    .count(4)
                    .value("12400.000000")
                    .oldestAt(LocalDateTime.of(2026, 3, 29, 9, 15))
                    .params(Map.of("docCount", 4))
                    .link(AlertLinkTarget.ORDER_CONSUMPTION_LIST)
                    .linkParams(Map.of("warehouseId", 996401L))
                    .build())
                .build())
            .withheldCode(AlertCode.CASH_VARIANCE)
            .evaluatedAt(LocalDateTime.of(2026, 3, 31, 18, 0))
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        DashboardSummaryService dashboardSummaryService() {
            return mock(DashboardSummaryService.class);
        }

        @Bean
        AlertService alertService() {
            return mock(AlertService.class);
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
