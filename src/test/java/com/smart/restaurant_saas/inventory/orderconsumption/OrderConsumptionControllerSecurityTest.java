package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = OrderConsumptionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderConsumptionControllerSecurityTest.MethodSecurityConfig.class)
class OrderConsumptionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderConsumptionService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void recalculateRequiresInventoryStockManagePermission() throws Exception {
        mockMvc.perform(post("/api/inventory/order-consumption-docs/{id}/recalculate", 50L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void recalculateAllowsInventoryStockManagePermission() throws Exception {
        securityService.allow("INVENTORY_STOCK_MANAGE");
        when(service.recalculate(50L, 7L, 99L))
            .thenReturn(OrderConsumptionDocResponse.builder()
                .id(50L)
                .warehouseId(10L)
                .status(OrderConsumptionStatus.POSTED)
                .build());

        mockMvc.perform(post("/api/inventory/order-consumption-docs/{id}/recalculate", 50L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(50L))
            .andExpect(jsonPath("$.warehouseId").value(10L))
            .andExpect(jsonPath("$.status").value("POSTED"));

        verify(service).recalculate(50L, 7L, 99L);
    }

    @Test
    @WithMockUser
    void listRequiresInventoryStockManagePermission() throws Exception {
        mockMvc.perform(get("/api/inventory/order-consumption-docs")
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsInventoryStockManagePermission() throws Exception {
        securityService.allow("INVENTORY_STOCK_MANAGE");
        when(service.list(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(OrderConsumptionStatus.CONFLICT),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(OrderConsumptionDocListResponse.builder()
            .id(50L)
            .warehouseId(10L)
            .warehouseName("Main Warehouse")
            .status(OrderConsumptionStatus.CONFLICT)
            .createdAt(LocalDateTime.of(2026, 7, 10, 12, 0))
            .lineCount(4)
            .build())));

        mockMvc.perform(get("/api/inventory/order-consumption-docs")
                .header("X-Tenant-Id", 7L)
                .queryParam("status", "CONFLICT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(50L))
            .andExpect(jsonPath("$.content[0].lineCount").value(4));
    }

    @Test
    @WithMockUser
    void detailRequiresInventoryStockManagePermission() throws Exception {
        mockMvc.perform(get("/api/inventory/order-consumption-docs/{id}", 50L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void detailAllowsInventoryStockManagePermission() throws Exception {
        securityService.allow("INVENTORY_STOCK_MANAGE");
        when(service.getById(50L, 7L)).thenReturn(OrderConsumptionDocDetailResponse.builder()
            .id(50L)
            .warehouseId(10L)
            .warehouseName("Main Warehouse")
            .status(OrderConsumptionStatus.PENDING)
            .lines(List.of())
            .build());

        mockMvc.perform(get("/api/inventory/order-consumption-docs/{id}", 50L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(50L))
            .andExpect(jsonPath("$.lines").isArray());

        verify(service).getById(50L, 7L);
    }

    @Test
    @WithMockUser
    void materialsSummaryRequiresInventoryStockManagePermission() throws Exception {
        mockMvc.perform(get("/api/inventory/order-consumption-docs/{id}/materials-summary", 50L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void materialsSummaryAllowsInventoryStockManagePermission() throws Exception {
        securityService.allow("INVENTORY_STOCK_MANAGE");
        when(service.getMaterialsSummary(50L, 7L)).thenReturn(
            OrderConsumptionMaterialsSummaryResponse.builder()
                .docId(50L)
                .materials(List.of())
                .build());

        mockMvc.perform(get("/api/inventory/order-consumption-docs/{id}/materials-summary", 50L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docId").value(50L))
            .andExpect(jsonPath("$.materials").isArray());

        verify(service).getMaterialsSummary(50L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        OrderConsumptionService orderConsumptionService() {
            return mock(OrderConsumptionService.class);
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
