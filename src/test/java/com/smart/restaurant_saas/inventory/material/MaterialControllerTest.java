package com.smart.restaurant_saas.inventory.material;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.material.dto.MaterialRequest;
import com.smart.restaurant_saas.inventory.material.dto.MaterialResponse;
import com.smart.restaurant_saas.inventory.service.setup.MaterialCatalogService;
import com.smart.restaurant_saas.inventory.service.setup.MaterialService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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

/**
 * Exercises the real MVC/Jackson message-converter stack (not a bare {@code ObjectMapper}),
 * because whether an unknown JSON property is tolerated or rejected is decided by Spring Boot's
 * autoconfigured converters, not by Jackson's own defaults.
 */
@WebMvcTest(
        controllers = MaterialController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(MaterialControllerTest.MethodSecurityConfig.class)
class MaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(materialService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void createToleratesStaleMinimumStockLevelField() throws Exception {
        securityService.allow("INVENTORY_SETUP_MANAGE");
        when(materialService.create(ArgumentMatchers.any(MaterialRequest.class), ArgumentMatchers.eq(7L)))
            .thenReturn(MaterialResponse.builder().id(1L).name("Flour").build());

        String staleJson = """
                {
                  "name": "Flour",
                  "categoryId": 10,
                  "stockUomId": 20,
                  "displayUomId": 20,
                  "defaultUomId": 20,
                  "minimumStockLevel": 5.5,
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/inventory/materials")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(staleJson))
            .andExpect(status().isCreated());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        MaterialService materialService() {
            return mock(MaterialService.class);
        }

        @Bean
        MaterialCatalogService materialCatalogService() {
            return mock(MaterialCatalogService.class);
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
