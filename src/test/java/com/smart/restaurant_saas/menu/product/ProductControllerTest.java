package com.smart.restaurant_saas.menu.product;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.menu.MenuErrorCode;
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
        controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProductControllerTest.MethodSecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(productService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void deleteRequiresProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_VIEW");

        mockMvc.perform(delete("/api/menu/products/1").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsProductsUpdate() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");

        mockMvc.perform(delete("/api/menu/products/1").header("X-Tenant-Id", 7L))
            .andExpect(status().isNoContent());

        verify(productService).deleteProduct(7L, 1L);
    }

    @Test
    @WithMockUser
    void deleteWithVariantChildrenReturnsConflict() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");
        doThrow(new BusinessException(MenuErrorCode.PRODUCT_HAS_VARIANTS,
            "Cannot delete a parent product while variant children exist.",
            ErrorParams.of("productId", 1L)))
            .when(productService).deleteProduct(7L, 1L);

        mockMvc.perform(delete("/api/menu/products/1").header("X-Tenant-Id", 7L))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("PRODUCT_HAS_VARIANTS"));
    }

    @Test
    @WithMockUser
    void deleteMissingProductReturnsNotFound() throws Exception {
        securityService.allow("PRODUCTS_UPDATE");
        doThrow(new ResourceNotFoundException(MenuErrorCode.PRODUCT_NOT_FOUND,
            "Product not found", ErrorParams.of("productId", 404L)))
            .when(productService).deleteProduct(7L, 404L);

        mockMvc.perform(delete("/api/menu/products/404").header("X-Tenant-Id", 7L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ProductService productService() {
            return mock(ProductService.class);
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
