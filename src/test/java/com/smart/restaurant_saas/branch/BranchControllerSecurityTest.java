package com.smart.restaurant_saas.branch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
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
 * Every {@code @PreAuthorize} on {@link BranchController}, asserted by its absence.
 *
 * <p>Written after a guard-reversion audit (ledger L006) found all five of this controller's gates
 * <b>UNCOVERED</b>: deleting any one of them left the whole suite green. The gates were never
 * broken — nothing would have noticed if they became so. Branch is the root of the MVP's master
 * data, so a dropped gate here exposes the object every warehouse, device, shift and order hangs
 * off.
 *
 * <p>Each endpoint gets two assertions: denied without the permission, allowed with it. The pair
 * matters — a test that only checks the happy path passes just as well when the annotation is
 * deleted.
 */
@WebMvcTest(
        controllers = BranchController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({BranchControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class BranchControllerSecurityTest {

    private static final String CREATE_BODY = """
            {"nameEn":"Main","nameAr":"الرئيسي","code":"ACME-BR-01","timezone":"Africa/Cairo","active":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    // ---- A018  GET /api/branches ----

    @Test
    @WithMockUser
    void listRequiresBranchesView() throws Exception {
        mockMvc.perform(get("/api/branches"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsBranchesView() throws Exception {
        securityService.allow("BRANCHES_VIEW");
        when(service.listBranches()).thenReturn(List.of());

        mockMvc.perform(get("/api/branches"))
            .andExpect(status().isOk());
    }

    // ---- A019  POST /api/branches ----

    @Test
    @WithMockUser
    void createRequiresBranchesCreate() throws Exception {
        mockMvc.perform(post("/api/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRejectsViewOnlyUser() throws Exception {
        // Read access must not imply the right to add a branch.
        securityService.allow("BRANCHES_VIEW");

        mockMvc.perform(post("/api/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsBranchesCreate() throws Exception {
        securityService.allow("BRANCHES_CREATE");

        mockMvc.perform(post("/api/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A020  GET /api/branches/{id} ----

    @Test
    @WithMockUser
    void getRequiresBranchesView() throws Exception {
        mockMvc.perform(get("/api/branches/{id}", 3L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getAllowsBranchesView() throws Exception {
        securityService.allow("BRANCHES_VIEW");

        mockMvc.perform(get("/api/branches/{id}", 3L))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A021  PUT /api/branches/{id} ----

    @Test
    @WithMockUser
    void updateRequiresBranchesUpdate() throws Exception {
        mockMvc.perform(put("/api/branches/{id}", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateRejectsCreateOnlyUser() throws Exception {
        // BRANCHES_CREATE is the right to add, not to edit what exists.
        securityService.allow("BRANCHES_CREATE");

        mockMvc.perform(put("/api/branches/{id}", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateAllowsBranchesUpdate() throws Exception {
        securityService.allow("BRANCHES_UPDATE");

        mockMvc.perform(put("/api/branches/{id}", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A022  PATCH /api/branches/{id}/status ----

    @Test
    @WithMockUser
    void updateStatusRequiresBranchesUpdate() throws Exception {
        mockMvc.perform(patch("/api/branches/{id}/status", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateStatusRejectsViewOnlyUser() throws Exception {
        // Deactivating a branch takes every warehouse, device and shift under it
        // out of use. Read access must not reach it.
        securityService.allow("BRANCHES_VIEW");

        mockMvc.perform(patch("/api/branches/{id}/status", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateStatusAllowsBranchesUpdate() throws Exception {
        securityService.allow("BRANCHES_UPDATE");

        mockMvc.perform(patch("/api/branches/{id}/status", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().is2xxSuccessful());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        BranchService branchService() {
            BranchService service = mock(BranchService.class);
            // The gate is what is under test, not the body. Returning something
            // non-null for every read keeps a 200 from depending on a stub that
            // happens to be missing.
            when(service.listBranches()).thenReturn(List.of());
            when(service.getBranch(anyLong())).thenReturn(null);
            when(service.createBranch(any())).thenReturn(null);
            return service;
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
