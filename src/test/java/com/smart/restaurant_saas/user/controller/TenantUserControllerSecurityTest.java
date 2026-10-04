package com.smart.restaurant_saas.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
import com.smart.restaurant_saas.user.service.TenantUserService;
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
 * Every {@code @PreAuthorize} on {@link TenantUserController}, asserted by its absence.
 *
 * <p>Written after a guard-reversion audit (ledger L006) found all six of this controller's gates
 * <b>UNCOVERED</b> — deleting any one left the suite green.
 *
 * <p>This is the highest-stakes controller in the set. {@code POST /api/users} takes a
 * {@code roleCode}, so an unguarded create is not a data leak but a <b>privilege escalation</b>:
 * any authenticated caller could mint themselves an {@code OWNER}, and {@code OWNER} holds every
 * active permission the tenant has. The six gates are also deliberately distinct — view, create,
 * update, change-status and delete are separate grants, so the tests below check not only that a
 * permission is required but that the <em>wrong</em> one does not substitute for it.
 */
@WebMvcTest(
        controllers = TenantUserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({TenantUserControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class TenantUserControllerSecurityTest {

    private static final String CREATE_BODY = """
            {"username":"cashier1","fullName":"Cashier One","password":"Passw0rd",
             "roleCode":"CASHIER","branchId":3}
            """;

    // Must satisfy UpdateUserRequest in full. Bean validation runs during argument
    // resolution, i.e. *before* the method-security interceptor, so an invalid body
    // returns 400 and the 403 these tests exist to assert never happens.
    private static final String UPDATE_BODY = """
            {"fullName":"Cashier One","roleCode":"CASHIER","branchId":3,"password":"Passw0rd"}
            """;

    private static final String STATUS_BODY = """
            {"active":false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantUserService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    // ---- A233  GET /api/users ----

    @Test
    @WithMockUser
    void listRequiresUsersView() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsUsersView() throws Exception {
        securityService.allow("USERS_VIEW");
        when(service.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk());
    }

    // ---- A234  POST /api/users — the escalation path ----

    @Test
    @WithMockUser
    void createRequiresUsersCreate() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRejectsViewOnlyUser() throws Exception {
        // Seeing the user list must not confer the right to add to it. The
        // request body carries roleCode, so this gate is what stands between an
        // authenticated caller and an OWNER account.
        securityService.allow("USERS_VIEW");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsUsersCreate() throws Exception {
        securityService.allow("USERS_CREATE");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A235  GET /api/users/{id} ----

    @Test
    @WithMockUser
    void getRequiresUsersView() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 5L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getAllowsUsersView() throws Exception {
        securityService.allow("USERS_VIEW");

        mockMvc.perform(get("/api/users/{id}", 5L))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A236  PUT /api/users/{id} ----

    @Test
    @WithMockUser
    void updateRequiresUsersUpdate() throws Exception {
        mockMvc.perform(put("/api/users/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateRejectsCreateOnlyUser() throws Exception {
        securityService.allow("USERS_CREATE");

        mockMvc.perform(put("/api/users/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateAllowsUsersUpdate() throws Exception {
        securityService.allow("USERS_UPDATE");

        mockMvc.perform(put("/api/users/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A237  PATCH /api/users/{id}/status ----

    @Test
    @WithMockUser
    void updateStatusRequiresUsersChangeStatus() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/status", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateStatusRejectsUpdateOnlyUser() throws Exception {
        // USERS_UPDATE edits a user's details. Deactivating one is a separate
        // grant, because it is how access is revoked.
        securityService.allow("USERS_UPDATE");

        mockMvc.perform(patch("/api/users/{id}/status", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateStatusAllowsUsersChangeStatus() throws Exception {
        securityService.allow("USERS_CHANGE_STATUS");

        mockMvc.perform(patch("/api/users/{id}/status", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY))
            .andExpect(status().is2xxSuccessful());
    }

    // ---- A238  DELETE /api/users/{id} ----

    @Test
    @WithMockUser
    void deleteRequiresUsersDelete() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 5L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteRejectsChangeStatusOnlyUser() throws Exception {
        securityService.allow("USERS_CHANGE_STATUS");

        mockMvc.perform(delete("/api/users/{id}", 5L))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsUsersDelete() throws Exception {
        securityService.allow("USERS_DELETE");

        mockMvc.perform(delete("/api/users/{id}", 5L))
            .andExpect(status().is2xxSuccessful());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        TenantUserService tenantUserService() {
            TenantUserService service = mock(TenantUserService.class);
            when(service.listUsers()).thenReturn(List.of());
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
