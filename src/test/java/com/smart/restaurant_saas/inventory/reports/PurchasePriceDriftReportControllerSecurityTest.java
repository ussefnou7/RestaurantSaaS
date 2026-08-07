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
import com.smart.restaurant_saas.inventory.reports.dto.PurchasePriceDriftRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        controllers = PurchasePriceDriftReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(PurchasePriceDriftReportControllerSecurityTest.MethodSecurityConfig.class)
class PurchasePriceDriftReportControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PurchasePriceDriftReportService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void priceDriftRequiresInventoryReportsViewPermission() throws Exception {
        mockMvc.perform(get("/api/inventory/reports/purchase-price-drift")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void priceDriftAllowsInventoryReportsViewPermission() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.purchasePriceDrift(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/purchase-price-drift")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L))
            .andExpect(jsonPath("$[0].materialCode").value("CHK-001"))
            .andExpect(jsonPath("$[0].materialName").value("Chicken"))
            .andExpect(jsonPath("$[0].materialNameAr").value("دجاج"))
            .andExpect(jsonPath("$[0].firstPrice").value("80.000000"))
            .andExpect(jsonPath("$[0].lastPrice").value("110.000000"))
            .andExpect(jsonPath("$[0].priceChange").value("30.000000"))
            .andExpect(jsonPath("$[0].changePercent").value("37.500000"))
            .andExpect(jsonPath("$[0].purchaseCount").value(2L))
            .andExpect(jsonPath("$[0].uomId").value(40L))
            .andExpect(jsonPath("$[0].uomSymbol").value("kg"))
            .andExpect(jsonPath("$[0].materialActive").value(true))
            .andExpect(jsonPath("$[0].firstPurchaseDate").exists())
            .andExpect(jsonPath("$[0].lastPurchaseDate").exists());
    }

    @Test
    @WithMockUser
    void priceDriftAllowsSysAdminWithoutPermission() throws Exception {
        securityService.sysAdmin = true;
        when(service.purchasePriceDrift(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/purchase-price-drift")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].materialId").value(20L));
    }

    @Test
    @WithMockUser
    void priceDriftPassesTheSupplierFilterAndOtherOptionalFiltersThrough() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.purchasePriceDrift(7L, FROM, TO, 10L, 30L, 50L)).thenReturn(List.of(row()));

        mockMvc.perform(get("/api/inventory/reports/purchase-price-drift")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31")
                .queryParam("warehouseId", "10")
                .queryParam("categoryId", "30")
                .queryParam("supplierId", "50"))
            .andExpect(status().isOk());

        verify(service).purchasePriceDrift(7L, FROM, TO, 10L, 30L, 50L);
    }

    @Test
    @WithMockUser
    void priceDriftSerializesANullPercentAsAbsentRatherThanZero() throws Exception {
        securityService.allow("INVENTORY_REPORTS_VIEW");
        when(service.purchasePriceDrift(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(PurchasePriceDriftRow.builder()
                .materialId(21L)
                .materialCode("FREE-001")
                .materialName("Sample")
                .firstPrice("0.000000")
                .firstPurchaseDate(LocalDateTime.of(2026, 3, 5, 0, 0))
                .lastPrice("50.000000")
                .lastPurchaseDate(LocalDateTime.of(2026, 3, 20, 0, 0))
                .priceChange("50.000000")
                .changePercent(null)
                .purchaseCount(2L)
                .uomId(40L)
                .uomSymbol("kg")
                .materialActive(true)
                .build()));

        mockMvc.perform(get("/api/inventory/reports/purchase-price-drift")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            // Absent, not "0.000000" — a zero would assert the price did not move.
            .andExpect(jsonPath("$[0].changePercent").doesNotExist())
            .andExpect(jsonPath("$[0].priceChange").value("50.000000"));
    }

    private static PurchasePriceDriftRow row() {
        return PurchasePriceDriftRow.builder()
            .materialId(20L)
            .materialCode("CHK-001")
            .materialName("Chicken")
            .materialNameAr("دجاج")
            .firstPrice("80.000000")
            .firstPurchaseDate(LocalDateTime.of(2026, 3, 5, 0, 0))
            .lastPrice("110.000000")
            .lastPurchaseDate(LocalDateTime.of(2026, 3, 20, 0, 0))
            .priceChange("30.000000")
            .changePercent("37.500000")
            .purchaseCount(2L)
            .uomId(40L)
            .uomSymbol("kg")
            .materialActive(true)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        PurchasePriceDriftReportService purchasePriceDriftReportService() {
            return mock(PurchasePriceDriftReportService.class);
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
