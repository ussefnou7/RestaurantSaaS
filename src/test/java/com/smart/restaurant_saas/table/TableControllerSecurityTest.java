package com.smart.restaurant_saas.table;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.inventory.core.enums.TableShape;
import com.smart.restaurant_saas.table.dto.TableResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TableController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(TableControllerSecurityTest.MethodSecurityConfig.class)
class TableControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableService service;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(service);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listRequiresTablesView() throws Exception {
        mockMvc.perform(get("/api/tables").header("X-Tenant-Id", 7L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsTablesViewAndPassesFilters() throws Exception {
        securityService.allow("TABLES_VIEW");
        when(service.findAll(7L, 3L, 11L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/tables")
                        .header("X-Tenant-Id", 7L)
                        .param("branchId", "3")
                        .param("sectionId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("T1"));

        verify(service).findAll(7L, 3L, 11L);
    }

    @Test
    @WithMockUser
    void getAllowsTablesView() throws Exception {
        securityService.allow("TABLES_VIEW");
        when(service.findById(10L, 7L)).thenReturn(response());

        mockMvc.perform(get("/api/tables/{id}", 10L).header("X-Tenant-Id", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    @WithMockUser
    void createRequiresTablesManage() throws Exception {
        mockMvc.perform(post("/api/tables")
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");
        when(service.create(any(), eq(7L), eq(99L))).thenReturn(response());

        mockMvc.perform(post("/api/tables")
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shape").value("SQUARE"));
    }

    @Test
    @WithMockUser
    void updateRequiresTablesManage() throws Exception {
        mockMvc.perform(put("/api/tables/{id}", 10L)
                        .header("X-Tenant-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tableJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void activateRequiresTablesManage() throws Exception {
        mockMvc.perform(patch("/api/tables/{id}/activate", 10L).header("X-Tenant-Id", 7L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deactivateAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");
        when(service.deactivate(10L, 7L, 99L)).thenReturn(response(false));

        mockMvc.perform(patch("/api/tables/{id}/deactivate", 10L)
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser
    void layoutRequiresTablesManage() throws Exception {
        mockMvc.perform(patch("/api/tables/{id}/layout", 10L)
                        .header("X-Tenant-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(layoutJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void layoutAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");
        when(service.updateLayout(any(), any(), any(), any())).thenReturn(response());

        mockMvc.perform(patch("/api/tables/{id}/layout", 10L)
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(layoutJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posX").value(100.25));

        verify(service).updateLayout(eq(10L), any(), eq(7L), eq(99L));
    }

    private String tableJson() {
        return """
                {"branchId":3,"name":"T1","sectionId":11,"capacity":4,"active":true}
                """;
    }

    private String layoutJson() {
        return """
                {"posX":100.25,"posY":220.50,"rotation":90,"shape":"RECTANGLE"}
                """;
    }

    private TableResponse response() {
        return response(true);
    }

    private TableResponse response(boolean active) {
        return new TableResponse(
                10L,
                3L,
                "Main",
                "T1",
                11L,
                "Outdoor",
                4,
                TableShape.SQUARE,
                new BigDecimal("100.25"),
                new BigDecimal("220.50"),
                90,
                active,
                null,
                null
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        TableService tableService() {
            return mock(TableService.class);
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
