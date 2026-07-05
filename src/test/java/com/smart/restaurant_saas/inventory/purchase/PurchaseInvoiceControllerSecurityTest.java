package com.smart.restaurant_saas.inventory.purchase;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.core.PurchaseInvoiceService;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
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
        controllers = PurchaseInvoiceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(PurchaseInvoiceControllerSecurityTest.MethodSecurityConfig.class)
class PurchaseInvoiceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PurchaseInvoiceService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void postDoesNotRequireActionPermission() throws Exception {
        when(service.post(10L, 7L, 99L))
            .thenReturn(PurchaseInvoiceResponse.builder()
                .id(10L)
                .status(DocumentStatus.POSTED)
                .postedToInventory(true)
                .build());

        mockMvc.perform(post("/api/inventory/purchase-invoices/{id}/post", 10L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10L))
            .andExpect(jsonPath("$.status").value("POSTED"))
            .andExpect(jsonPath("$.postedToInventory").value(true));

        verify(service).post(10L, 7L, 99L);
    }

    @Test
    @WithMockUser
    void unpostRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/purchase-invoices/{id}/unpost", 10L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ENTRY_ERROR\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void unpostAllowsDedicatedPermission() throws Exception {
        securityService.allow("PURCHASE_INVOICE_UNPOST");
        when(service.unpost(eq(10L), argThat(req -> "ENTRY_ERROR".equals(req.getReason())),
                eq(7L), eq(99L)))
            .thenReturn(PurchaseInvoiceResponse.builder()
                .id(10L)
                .status(DocumentStatus.COMPLETE)
                .postedToInventory(false)
                .build());

        mockMvc.perform(post("/api/inventory/purchase-invoices/{id}/unpost", 10L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ENTRY_ERROR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10L))
            .andExpect(jsonPath("$.status").value("COMPLETE"))
            .andExpect(jsonPath("$.postedToInventory").value(false));

        verify(service).unpost(eq(10L), argThat(req -> "ENTRY_ERROR".equals(req.getReason())),
            eq(7L), eq(99L));
    }

    @Test
    @WithMockUser
    void uncompleteRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/purchase-invoices/{id}/uncomplete", 10L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"NEEDS_EDIT\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void uncompleteAllowsDedicatedPermission() throws Exception {
        securityService.allow("PURCHASE_INVOICE_UNCOMPLETE");
        when(service.uncomplete(eq(10L), argThat(req -> "NEEDS_EDIT".equals(req.getReason())),
                eq(7L), eq(99L)))
            .thenReturn(PurchaseInvoiceResponse.builder()
                .id(10L)
                .invoiceNumber("PINV-10")
                .status(DocumentStatus.DRAFT)
                .postedToInventory(false)
                .build());

        mockMvc.perform(post("/api/inventory/purchase-invoices/{id}/uncomplete", 10L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"NEEDS_EDIT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10L))
            .andExpect(jsonPath("$.invoiceNumber").value("PINV-10"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(service).uncomplete(eq(10L), argThat(req -> "NEEDS_EDIT".equals(req.getReason())),
            eq(7L), eq(99L));
    }

    @Test
    @WithMockUser
    void deleteRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(delete("/api/inventory/purchase-invoices/{id}", 10L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsDedicatedPermission() throws Exception {
        securityService.allow("PURCHASE_INVOICE_DELETE");

        mockMvc.perform(delete("/api/inventory/purchase-invoices/{id}", 10L)
                .header("X-Tenant-Id", 7L))
            .andExpect(status().isNoContent());

        verify(service).delete(10L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        PurchaseInvoiceService purchaseInvoiceService() {
            return mock(PurchaseInvoiceService.class);
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
