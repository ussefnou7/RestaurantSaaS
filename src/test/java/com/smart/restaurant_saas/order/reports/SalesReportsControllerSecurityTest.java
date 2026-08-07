package com.smart.restaurant_saas.order.reports;

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
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByHourRow;
import com.smart.restaurant_saas.order.reports.dto.SalesByPaymentMethodRow;
import com.smart.restaurant_saas.order.reports.dto.SalesByProductRow;
import com.smart.restaurant_saas.order.reports.dto.SalesOverTimeRow;
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

/**
 * All four sales endpoints in one slice: they share a permission, a base path and a filter set, and
 * the thing most worth pinning is that they are consistent about all three.
 */
@WebMvcTest(
        controllers = {
            SalesOverTimeReportController.class,
            SalesByProductReportController.class,
            SalesByPaymentMethodReportController.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(SalesReportsControllerSecurityTest.MethodSecurityConfig.class)
class SalesReportsControllerSecurityTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    private static final String[] ENDPOINTS = {
        "/api/orders/reports/sales-over-time",
        "/api/orders/reports/sales-by-hour",
        "/api/orders/reports/sales-by-product",
        "/api/orders/reports/sales-by-payment-method"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SalesOverTimeReportService overTimeService;

    @Autowired
    private SalesByProductReportService productService;

    @Autowired
    private SalesByPaymentMethodReportService paymentMethodService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(overTimeService, productService, paymentMethodService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void everySalesReportRequiresReportsViewSales() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint)
                    .header("X-Tenant-Id", 7L)
                    .queryParam("dateFrom", "2026-03-01")
                    .queryParam("dateTo", "2026-03-31"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser
    void inventoryReportsPermissionDoesNotGrantSalesReports() throws Exception {
        // These are the first Orders-sourced reports; they must not ride on the inventory grant.
        securityService.allow("INVENTORY_REPORTS_VIEW");

        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint)
                    .header("X-Tenant-Id", 7L)
                    .queryParam("dateFrom", "2026-03-01")
                    .queryParam("dateTo", "2026-03-31"))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser
    void everySalesReportIsAllowedBySysAdmin() throws Exception {
        securityService.sysAdmin = true;

        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint)
                    .header("X-Tenant-Id", 7L)
                    .queryParam("dateFrom", "2026-03-01")
                    .queryParam("dateTo", "2026-03-31"))
                .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser
    void salesOverTimeSerializesItsComponents() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");
        when(overTimeService.salesOverTime(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(SalesOverTimeRow.builder()
                .salesDate(LocalDate.of(2026, 3, 5))
                .orderCount(2L)
                .subtotal("300.000000")
                .taxAmount("42.000000")
                .totalAmount("342.000000")
                .averageOrderValue("171.000000")
                .build()));

        mockMvc.perform(get("/api/orders/reports/sales-over-time")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].salesDate").value("2026-03-05"))
            .andExpect(jsonPath("$[0].orderCount").value(2L))
            .andExpect(jsonPath("$[0].subtotal").value("300.000000"))
            .andExpect(jsonPath("$[0].taxAmount").value("42.000000"))
            .andExpect(jsonPath("$[0].totalAmount").value("342.000000"))
            .andExpect(jsonPath("$[0].averageOrderValue").value("171.000000"));
    }

    @Test
    @WithMockUser
    void salesByHourCarriesTheHourBucket() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");
        when(overTimeService.salesByHour(eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(SalesByHourRow.builder()
                .salesDate(LocalDate.of(2026, 3, 5))
                .hourOfDay(19)
                .orderCount(3L)
                .subtotal("300.000000")
                .taxAmount("42.000000")
                .totalAmount("342.000000")
                .averageOrderValue("114.000000")
                .build()));

        mockMvc.perform(get("/api/orders/reports/sales-by-hour")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hourOfDay").value(19));
    }

    @Test
    @WithMockUser
    void salesByProductOmitsCodeAndArabicNameRatherThanNullingThem() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");
        when(productService.salesByProduct(eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(SalesByProductRow.builder()
                .productId(20L)
                .productName("Burger")
                .quantitySold("3.000000")
                .revenue("120.000000")
                .revenueSharePercent("66.666667")
                .build()));

        mockMvc.perform(get("/api/orders/reports/sales-by-product")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productName").value("Burger"))
            .andExpect(jsonPath("$[0].revenue").value("120.000000"))
            // The product table has neither column — absent, not null placeholders.
            .andExpect(jsonPath("$[0].productCode").doesNotExist())
            .andExpect(jsonPath("$[0].productNameAr").doesNotExist());
    }

    @Test
    @WithMockUser
    void salesByPaymentMethodSerializesItsBucket() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");
        when(paymentMethodService.salesByPaymentMethod(
                eq(7L), eq(FROM), eq(TO), isNull(), isNull(), isNull()))
            .thenReturn(List.of(SalesByPaymentMethodRow.builder()
                .paymentMethod("UNSPECIFIED")
                .orderCount(1L)
                .subtotal("100.000000")
                .taxAmount("14.000000")
                .totalAmount("114.000000")
                .totalSharePercent("100.000000")
                .build()));

        mockMvc.perform(get("/api/orders/reports/sales-by-payment-method")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].paymentMethod").value("UNSPECIFIED"))
            .andExpect(jsonPath("$[0].totalSharePercent").value("100.000000"));
    }

    @Test
    @WithMockUser
    void everyReportPassesTheSharedFilterSetThrough() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");

        for (String endpoint : ENDPOINTS) {
            mockMvc.perform(get(endpoint)
                    .header("X-Tenant-Id", 7L)
                    .queryParam("dateFrom", "2026-03-01")
                    .queryParam("dateTo", "2026-03-31")
                    .queryParam("branchId", "10")
                    .queryParam("cashierUserId", "30")
                    .queryParam("orderType", "DELIVERY"))
                .andExpect(status().isOk());
        }

        verify(overTimeService).salesOverTime(7L, FROM, TO, 10L, 30L, OrderType.DELIVERY);
        verify(overTimeService).salesByHour(7L, FROM, TO, 10L, 30L, OrderType.DELIVERY);
        verify(productService).salesByProduct(7L, FROM, TO, 10L, 30L, OrderType.DELIVERY);
        verify(paymentMethodService)
            .salesByPaymentMethod(7L, FROM, TO, 10L, 30L, OrderType.DELIVERY);
    }

    @Test
    @WithMockUser
    void aBadOrderTypeIsRejectedRatherThanIgnored() throws Exception {
        securityService.allow("REPORTS_VIEW_SALES");

        mockMvc.perform(get("/api/orders/reports/sales-over-time")
                .header("X-Tenant-Id", 7L)
                .queryParam("dateFrom", "2026-03-01")
                .queryParam("dateTo", "2026-03-31")
                .queryParam("orderType", "TELEPATHY"))
            .andExpect(status().is5xxServerError());
        // Documents O26 rather than endorsing it: an unparseable enum raises
        // MethodArgumentTypeMismatchException, which GlobalExceptionHandler does not handle, so the
        // client gets a 500 where a 400 is correct. Update this assertion when that gap is fixed.
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        SalesOverTimeReportService salesOverTimeReportService() {
            return mock(SalesOverTimeReportService.class);
        }

        @Bean
        SalesByProductReportService salesByProductReportService() {
            return mock(SalesByProductReportService.class);
        }

        @Bean
        SalesByPaymentMethodReportService salesByPaymentMethodReportService() {
            return mock(SalesByPaymentMethodReportService.class);
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
