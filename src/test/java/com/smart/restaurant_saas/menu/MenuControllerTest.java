package com.smart.restaurant_saas.menu;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import com.smart.restaurant_saas.menu.dto.MenuItemType;
import com.smart.restaurant_saas.menu.dto.MenuVariantResponse;
import java.math.BigDecimal;
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
        controllers = MenuController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(MenuControllerTest.MethodSecurityConfig.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuService menuService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(menuService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void menuRequiresProductsView() throws Exception {
        mockMvc.perform(get("/api/menu").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void menuReturnsExplicitParentContractWithoutStoredParentPrice() throws Exception {
        securityService.allow("PRODUCTS_VIEW");
        when(menuService.findMenu(7L)).thenReturn(List.of(
            MenuItemResponse.builder()
                .id(21L)
                .name("Cheese Pizza")
                .type(MenuItemType.PARENT)
                .menuCategoryId(8L)
                .menuCategoryName("Pizza")
                .minPrice(new BigDecimal("70.00"))
                .maxPrice(new BigDecimal("140.00"))
                .variants(List.of(MenuVariantResponse.builder()
                    .id(22L)
                    .name("Cheese Pizza Small")
                    .variantLabel("Small")
                    .variantLabelAr("صغير")
                    .sellingPrice(new BigDecimal("70.00"))
                    .build()))
                .addOns(List.of())
                .build()));

        mockMvc.perform(get("/api/menu").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("PARENT"))
            .andExpect(jsonPath("$[0].sellingPrice").doesNotExist())
            .andExpect(jsonPath("$[0].minPrice").value(70.00))
            .andExpect(jsonPath("$[0].maxPrice").value(140.00))
            .andExpect(jsonPath("$[0].variants[0].variantLabelAr").value("صغير"));

        verify(menuService).findMenu(7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        MenuService menuService() {
            return mock(MenuService.class);
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
