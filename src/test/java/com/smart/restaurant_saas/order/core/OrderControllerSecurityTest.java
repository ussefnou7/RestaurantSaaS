package com.smart.restaurant_saas.order.core;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.HashMap;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderControllerSecurityTest.MethodSecurityConfig.class)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void createRequiresOrdersCreatePermission() throws Exception {
        mockMvc.perform(post("/api/orders")
                .header("X-Tenant-Id", 7L)
                .header("X-Branch-Id", 101L)
                .header("X-User-Id", 11L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRejectsMissingBranchHeaderUsingExistingRequiredHeaderHandling() throws Exception {
        securityService.allow("ORDERS_CREATE");

        mockMvc.perform(post("/api/orders")
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 11L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson()))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void createPassesBranchHeaderToService() throws Exception {
        securityService.allow("ORDERS_CREATE");
        when(service.createCompletedOrder(
                argThat(req -> req.getOrderDate() != null && req.getLines().size() == 1),
                eq(7L),
                eq(11L),
                eq(101L)))
            .thenReturn(OrderResponse.builder()
                .id(900L)
                .status(OrderStatus.COMPLETE)
                .branchId(101L)
                .warehouseId(202L)
                .totalAmount(new BigDecimal("90.00"))
                .build());

        mockMvc.perform(post("/api/orders")
                .header("X-Tenant-Id", 7L)
                .header("X-Branch-Id", 101L)
                .header("X-User-Id", 11L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(900L))
            .andExpect(jsonPath("$.branchId").value(101L))
            .andExpect(jsonPath("$.warehouseId").value(202L));

        verify(service).createCompletedOrder(
            argThat(req -> req.getOrderDate() != null && req.getLines().size() == 1),
            eq(7L),
            eq(11L),
            eq(101L));
    }

    private String orderJson() {
        return """
            {
              "orderType": "DINE_IN",
              "orderSource": "POS",
              "status": "COMPLETE",
              "paymentMethod": "CASH",
              "tableNo": "T1",
              "orderDate": "2026-07-10T12:00:00",
              "lines": [
                {
                  "productId": 303,
                  "quantity": 2.000000,
                  "unitPrice": 45.00
                }
              ]
            }
            """;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
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
