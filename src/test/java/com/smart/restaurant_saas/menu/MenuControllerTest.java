package com.smart.restaurant_saas.menu;

import com.smart.restaurant_saas.tenant.SliceTenantConfig;

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
@Import({MenuControllerTest.MethodSecurityConfig.class, SliceTenantConfig.class})
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
        mockMvc.perform(get("/api/menu"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void menuReturnsExplicitParentContractWithoutStoredParentPrice() throws Exception {
        securityService.allow("PRODUCTS_VIEW");
        when(menuService.findMenu(7L, false)).thenReturn(List.of(
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

        mockMvc.perform(get("/api/menu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("PARENT"))
            .andExpect(jsonPath("$[0].sellingPrice").doesNotExist())
            .andExpect(jsonPath("$[0].minPrice").value(70.00))
            .andExpect(jsonPath("$[0].maxPrice").value(140.00))
            .andExpect(jsonPath("$[0].variants[0].variantLabelAr").value("صغير"));

        // Bytes stay out unless a caller asks for them. This endpoint also serves the admin
        // web app, which authenticates with a session cookie and can load image urls
        // directly — it has no use for a base64 copy of every thumbnail on a catalog page.
        verify(menuService).findMenu(7L, false);
    }

    @Test
    @WithMockUser
    void includeImageDataAsksTheServiceToEmbedTheBytes() throws Exception {
        securityService.allow("PRODUCTS_VIEW");
        when(menuService.findMenu(7L, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/menu").param("includeImageData", "true"))
            .andExpect(status().isOk());

        // The POS sets this. Its urls point at /api/media/**, which is permission-gated, and
        // no browser attaches a bearer token to an <img> — so the bytes have to come back in
        // this response or the terminal has no way to render a single tile.
        verify(menuService).findMenu(7L, true);
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
