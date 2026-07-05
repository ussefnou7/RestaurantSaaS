package com.smart.restaurant_saas.inventory.physicalcount;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PhysicalCountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(PhysicalCountControllerSecurityTest.MethodSecurityConfig.class)
class PhysicalCountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void revertToDraftRequiresDedicatedPermission() throws Exception {
        mockMvc.perform(post("/api/inventory/physical-counts/{id}/revert-to-draft", 30L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void revertToDraftAllowsDedicatedPermission() throws Exception {
        securityService.allow("PHYSICAL_COUNT_REVERT_TO_DRAFT");
        when(service.revertToDraft(30L, 7L, 99L))
            .thenReturn(PhysicalCountResponse.builder()
                .id(30L)
                .status(PhysicalCountStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/inventory/physical-counts/{id}/revert-to-draft", 30L)
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(30L))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(service).revertToDraft(30L, 7L, 99L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        PhysicalCountService physicalCountService() {
            return mock(PhysicalCountService.class);
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
