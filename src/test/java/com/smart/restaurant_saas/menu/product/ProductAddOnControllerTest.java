package com.smart.restaurant_saas.menu.product;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.menu.product.dto.ProductAddOnResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ProductAddOnController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProductAddOnControllerTest.MethodSecurityConfig.class)
class ProductAddOnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductAddOnService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listRequiresProductsView() throws Exception {
        mockMvc.perform(get("/api/menu/products/1/add-ons").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsProductsView() throws Exception {
        securityService.allow("PRODUCTS_VIEW");
        when(service.findByProduct(1L, 7L)).thenReturn(List.of(
            ProductAddOnResponse.builder().id(9L).productId(1L).addOnProductId(2L)
                .addOnProductName("Fries").build()));

        mockMvc.perform(get("/api/menu/products/1/add-ons").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].addOnProductName").value("Fries"));

        verify(service).findByProduct(1L, 7L);
    }

    @Test
    @WithMockUser
    void createRequiresProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_VIEW");

        mockMvc.perform(post("/api/menu/products/1/add-ons")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addOnProductId\":2}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");
        when(service.create(eq(1L), eq(2L), eq(7L), eq(99L))).thenReturn(
            ProductAddOnResponse.builder().id(9L).productId(1L).addOnProductId(2L).build());

        mockMvc.perform(post("/api/menu/products/1/add-ons")
                .header("X-Tenant-Id", 7L)
                .header("X-User-Id", 99L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addOnProductId\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addOnProductId").value(2));

        verify(service).create(1L, 2L, 7L, 99L);
    }

    @Test
    @WithMockUser
    void createRejectsMissingAddOnProductId() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");

        mockMvc.perform(post("/api/menu/products/1/add-ons")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void deleteRequiresProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_VIEW");

        mockMvc.perform(delete("/api/menu/products/1/add-ons/2").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");

        mockMvc.perform(delete("/api/menu/products/1/add-ons/2").header("X-Tenant-Id", 7L))
            .andExpect(status().isNoContent());

        verify(service).delete(1L, 2L, 7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ProductAddOnService productAddOnService() {
            return mock(ProductAddOnService.class);
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
