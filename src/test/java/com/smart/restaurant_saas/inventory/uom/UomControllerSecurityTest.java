package com.smart.restaurant_saas.inventory.uom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.core.UomService;
import com.smart.restaurant_saas.inventory.uom.dto.UomRequest;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;
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

/**
 * Permission gates on the tenant UOM surface.
 *
 * <p>Every endpoint on {@code /api/uom} previously carried no {@code @PreAuthorize} at all, so
 * any authenticated tenant user could create, deactivate, or delete a unit of measure regardless
 * of role. Global authentication applied, but that is not the documented permission gate
 * (CONVENTIONS -> Controllers).
 *
 * <p>No new permission was invented to close the gap. UOM is inventory setup master data, and the
 * already-seeded {@code INVENTORY_SETUP_VIEW} / {@code INVENTORY_SETUP_MANAGE} pair names it
 * explicitly in its own V2 seed description -- "View/Manage inventory setup (materials,
 * categories, UOMs)" -- and is what the sibling setup controllers (Material, MaterialCategory,
 * Warehouse) already use.
 */
@WebMvcTest(
        controllers = UomController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(UomControllerSecurityTest.MethodSecurityConfig.class)
class UomControllerSecurityTest {

    private static final String VALID_CREATE_BODY = """
        {"code":"SACK","name":"Sack","symbol":"sack","type":"WEIGHT",\
         "baseUom":3,"factorToBase":25000}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UomService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    // ---- reads: INVENTORY_SETUP_VIEW ----

    @Test
    @WithMockUser
    void listRequiresSetupViewPermission() throws Exception {
        mockMvc.perform(get("/api/uom").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());

        verify(service, never()).findAvailableForTenant(anyLong());
    }

    @Test
    @WithMockUser
    void listAllowsSetupViewPermission() throws Exception {
        securityService.allow("INVENTORY_SETUP_VIEW");
        when(service.findAvailableForTenant(7L))
            .thenReturn(List.of(UomResponse.builder().id(3L).name("Kilogram").build()));

        mockMvc.perform(get("/api/uom").header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(3L))
            .andExpect(jsonPath("$[0].name").value("Kilogram"));

        verify(service).findAvailableForTenant(7L);
    }

    @Test
    @WithMockUser
    void lookupRequiresSetupViewPermission() throws Exception {
        mockMvc.perform(get("/api/uom/lookup").header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());

        verify(service, never()).findLookupForTenant(anyLong());
    }

    @Test
    @WithMockUser
    void resolveRequiresSetupViewPermission() throws Exception {
        mockMvc.perform(get("/api/uom/{id}", 3L).header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());

        verify(service, never()).resolveForTenant(anyLong(), anyLong());
    }

    // ---- writes: INVENTORY_SETUP_MANAGE ----

    @Test
    @WithMockUser
    void createRequiresSetupManagePermission() throws Exception {
        mockMvc.perform(post("/api/uom")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_BODY))
            .andExpect(status().isForbidden());

        verify(service, never()).createForTenant(any(UomRequest.class), anyLong());
    }

    @Test
    @WithMockUser
    void setupViewAloneDoesNotAllowCreate() throws Exception {
        securityService.allow("INVENTORY_SETUP_VIEW");

        mockMvc.perform(post("/api/uom")
                .header("X-Tenant-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_BODY))
            .andExpect(status().isForbidden());

        verify(service, never()).createForTenant(any(UomRequest.class), anyLong());
    }

    @Test
    @WithMockUser
    void deactivateRequiresSetupManagePermission() throws Exception {
        mockMvc.perform(patch("/api/uom/{id}/deactivate", 3L).header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());

        verify(service, never()).deactivate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @WithMockUser
    void deactivateAllowsSetupManagePermission() throws Exception {
        securityService.allow("INVENTORY_SETUP_MANAGE");
        when(service.deactivate(3L, 7L, false))
            .thenReturn(UomResponse.builder().id(3L).name("Kilogram").active(false).build());

        mockMvc.perform(patch("/api/uom/{id}/deactivate", 3L).header("X-Tenant-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(3L))
            .andExpect(jsonPath("$.active").value(false));

        verify(service).deactivate(3L, 7L, false);
    }

    @Test
    @WithMockUser
    void deleteRequiresSetupManagePermission() throws Exception {
        mockMvc.perform(delete("/api/uom/{id}", 3L).header("X-Tenant-Id", 7L))
            .andExpect(status().isForbidden());

        verify(service, never()).delete(anyLong(), anyLong());
    }

    @Test
    @WithMockUser
    void deleteAllowsSetupManagePermission() throws Exception {
        securityService.allow("INVENTORY_SETUP_MANAGE");

        mockMvc.perform(delete("/api/uom/{id}", 3L).header("X-Tenant-Id", 7L))
            .andExpect(status().isNoContent());

        verify(service).delete(3L, 7L);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        UomService uomService() {
            return mock(UomService.class);
        }

        @Bean
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
