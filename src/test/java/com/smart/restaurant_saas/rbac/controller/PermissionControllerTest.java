package com.smart.restaurant_saas.rbac.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.UserPermissionsResponse;
import com.smart.restaurant_saas.rbac.service.PermissionService;
import com.smart.restaurant_saas.rbac.service.UserPermissionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@WebMvcTest(
        controllers = PermissionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        PermissionControllerTest.MethodSecurityConfig.class,
        PermissionControllerTest.MethodSecurityExceptionHandler.class
})
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingPermissionService permissionService;

    @Autowired
    private RecordingUserPermissionService userPermissionService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        permissionService.reset();
        userPermissionService.reset();
        securityService.reset();
    }

    @Test
    @WithMockUser
    void ownerWithPermissionsViewCanListPermissions() throws Exception {
        securityService.allow("PERMISSIONS_VIEW");
        permissionService.response = List.of(
                new PermissionResponse(1L, "PERMISSIONS_VIEW", "PERMISSIONS", "View Permissions", null, "ACTION", true)
        );

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("PERMISSIONS_VIEW"));

        assertThat(permissionService.listCalls).isEqualTo(1);
    }

    @Test
    @WithMockUser
    void ownerWithPermissionsViewCanGetSameTenantUserPermissions() throws Exception {
        securityService.allow("PERMISSIONS_VIEW");
        userPermissionService.response = userPermissionsResponse(5L, 20L);

        mockMvc.perform(get("/api/users/{userId}/permissions", 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(5L))
                .andExpect(jsonPath("$.userId").value(20L));

        assertThat(userPermissionService.lastGetUserId).isEqualTo(20L);
    }

    @Test
    @WithMockUser
    void ownerWithUserPermissionsUpdateCanReplaceSameTenantUserPermissions() throws Exception {
        securityService.allow("USER_PERMISSIONS_UPDATE");
        userPermissionService.response = userPermissionsResponse(5L, 20L);

        mockMvc.perform(put("/api/rbac/users/{userId}/permissions", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"PERMISSIONS_VIEW\",\"USERS_VIEW\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(5L))
                .andExpect(jsonPath("$.userId").value(20L));

        assertThat(userPermissionService.lastReplaceUserId).isEqualTo(20L);
        assertThat(userPermissionService.lastReplaceCodes)
                .containsExactly("PERMISSIONS_VIEW", "USERS_VIEW");
    }

    @Test
    @WithMockUser
    void ownerWithUserPermissionsUpdateCanResetSameTenantUserPermissionsToRoleDefaults() throws Exception {
        securityService.allow("USER_PERMISSIONS_UPDATE");
        userPermissionService.response = userPermissionsResponse(5L, 20L);

        mockMvc.perform(post("/api/rbac/users/{userId}/permissions/reset-to-role-defaults", 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(5L))
                .andExpect(jsonPath("$.userId").value(20L));

        assertThat(userPermissionService.lastResetUserId).isEqualTo(20L);
    }

    @Test
    @WithMockUser
    void userWithoutRequiredPermissionCannotListPermissions() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isForbidden());

        assertThat(permissionService.listCalls).isZero();
    }

    @Test
    @WithMockUser
    void userWithoutRequiredPermissionCannotReplaceUserPermissions() throws Exception {
        mockMvc.perform(put("/api/rbac/users/{userId}/permissions", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"PERMISSIONS_VIEW\"]"))
                .andExpect(status().isForbidden());

        assertThat(userPermissionService.lastReplaceUserId).isNull();
    }

    private static UserPermissionsResponse userPermissionsResponse(Long tenantId, Long userId) {
        return new UserPermissionsResponse(
                tenantId,
                userId,
                List.of(new UserPermissionsResponse.PermissionSelectionResponse(
                        1L,
                        "PERMISSIONS_VIEW",
                        "PERMISSIONS",
                        "View Permissions",
                        null,
                        "ACTION",
                        true,
                        true
                ))
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        RecordingPermissionService permissionService() {
            return new RecordingPermissionService();
        }

        @Bean
        RecordingUserPermissionService userPermissionService() {
            return new RecordingUserPermissionService();
        }

        @Bean("securityService")
        RecordingSecurityService securityService() {
            return new RecordingSecurityService();
        }
    }

    @RestControllerAdvice
    static class MethodSecurityExceptionHandler {

        @ExceptionHandler(AuthorizationDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handleAuthorizationDenied() {
        }
    }

    static class RecordingPermissionService extends PermissionService {

        private int listCalls;
        private List<PermissionResponse> response = List.of();

        RecordingPermissionService() {
            super(null, null);
        }

        @Override
        public List<PermissionResponse> listActivePermissions() {
            listCalls++;
            return response;
        }

        private void reset() {
            listCalls = 0;
            response = List.of();
        }
    }

    static class RecordingUserPermissionService extends UserPermissionService {

        private Long lastGetUserId;
        private Long lastReplaceUserId;
        private Long lastResetUserId;
        private List<String> lastReplaceCodes;
        private UserPermissionsResponse response = userPermissionsResponse(5L, 20L);

        RecordingUserPermissionService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public UserPermissionsResponse getUserPermissions(Long userId) {
            lastGetUserId = userId;
            return response;
        }

        @Override
        public UserPermissionsResponse replaceUserPermissions(
                Long userId,
                List<String> permissionCodes
        ) {
            lastReplaceUserId = userId;
            lastReplaceCodes = permissionCodes;
            return response;
        }

        @Override
        public UserPermissionsResponse resetUserPermissionsToRoleDefaults(Long userId) {
            lastResetUserId = userId;
            return response;
        }

        private void reset() {
            lastGetUserId = null;
            lastReplaceUserId = null;
            lastResetUserId = null;
            lastReplaceCodes = null;
            response = userPermissionsResponse(5L, 20L);
        }
    }

    static class RecordingSecurityService extends SecurityService {

        private final Map<String, Boolean> permissions = new HashMap<>();

        RecordingSecurityService() {
            super(null, null);
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
