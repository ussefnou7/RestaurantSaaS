package com.smart.restaurant_saas.rbac.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.rbac.dto.response.RoleResponse;
import com.smart.restaurant_saas.rbac.service.RoleService;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RbacRoleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(RbacRoleControllerTest.TestConfig.class)
class RbacRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingRoleService roleService;

    @BeforeEach
    void setUp() {
        roleService.reset();
    }

    @Test
    void tenantRbacCanListRolesWithoutPermissionGate() throws Exception {
        roleService.response = List.of(
                new RoleResponse(1L, "CASHIER", "Cashier", null, null, null, null, null, true, true)
        );

        mockMvc.perform(get("/api/rbac/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CASHIER"));

        assertThat(roleService.listCalls).isEqualTo(1);
    }

    @Test
    void tenantRbacHasNoRoleOrRolePermissionWriteRoutes() throws Exception {
        mockMvc.perform(post("/api/rbac/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/rbac/roles/{roleCode}", "CASHIER"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/rbac/roles/{roleCode}", "CASHIER"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/rbac/roles/{roleCode}/permissions", "CASHIER"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        RecordingRoleService roleService() {
            return new RecordingRoleService();
        }
    }

    static class RecordingRoleService extends RoleService {

        private int listCalls;
        private List<RoleResponse> response = List.of();

        RecordingRoleService() {
            super(null, null, null);
        }

        @Override
        public List<RoleResponse> listActiveRoles() {
            listCalls++;
            return response;
        }

        private void reset() {
            listCalls = 0;
            response = List.of();
        }
    }
}
