package com.smart.restaurant_saas.inventory.waste;

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
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentResponse;
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
        controllers = WasteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(WasteControllerSecurityTest.MethodSecurityConfig.class)
class WasteControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WasteService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void uncompleteRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/waste-documents/{id}/uncomplete", 40L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"NEEDS_EDIT\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void uncompleteAllowsDedicatedPermission() throws Exception {
        securityService.allow("WASTE_UNCOMPLETE");
        when(service.uncomplete(eq(40L), argThat(req -> "NEEDS_EDIT".equals(req.getReason())),
                eq(7L), eq(99L)))
            .thenReturn(WasteDocumentResponse.builder()
                .id(40L)
                .code("F7AM-WST-2026-00001")
                .status(DocumentStatus.DRAFT)
                .postedToInventory(false)
                .build());

        mockMvc.perform(post("/api/inventory/waste-documents/{id}/uncomplete", 40L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"NEEDS_EDIT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(40L))
            .andExpect(jsonPath("$.code").value("F7AM-WST-2026-00001"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(service).uncomplete(eq(40L), argThat(req -> "NEEDS_EDIT".equals(req.getReason())),
            eq(7L), eq(99L));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        WasteService wasteService() {
            return mock(WasteService.class);
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
