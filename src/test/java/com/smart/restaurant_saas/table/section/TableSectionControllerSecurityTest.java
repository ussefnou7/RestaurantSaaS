package com.smart.restaurant_saas.table.section;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.table.section.dto.TableSectionResponse;
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
        controllers = TableSectionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(TableSectionControllerSecurityTest.MethodSecurityConfig.class)
class TableSectionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TableSectionService service;

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
        mockMvc.perform(get("/api/table-sections")
                        .header("X-Tenant-Id", 7L)
                        .param("branchId", "3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void listAllowsTablesViewAndPassesBranch() throws Exception {
        securityService.allow("TABLES_VIEW");
        when(service.findAll(7L, 3L, false)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/table-sections")
                        .header("X-Tenant-Id", 7L)
                        .param("branchId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Outdoor"));

        verify(service).findAll(7L, 3L, false);
    }

    @Test
    @WithMockUser
    void listCanRequestInactiveRowsForManagement() throws Exception {
        securityService.allow("TABLES_VIEW");
        when(service.findAll(7L, 3L, true)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/table-sections")
                        .header("X-Tenant-Id", 7L)
                        .param("branchId", "3")
                        .param("includeInactive", "true"))
                .andExpect(status().isOk());

        verify(service).findAll(7L, 3L, true);
    }

    @Test
    @WithMockUser
    void createRequiresTablesManage() throws Exception {
        mockMvc.perform(post("/api/table-sections")
                        .header("X-Tenant-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");
        when(service.create(any(), eq(7L), eq(99L))).thenReturn(response());

        mockMvc.perform(post("/api/table-sections")
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11L));
    }

    @Test
    @WithMockUser
    void updateRequiresTablesManage() throws Exception {
        mockMvc.perform(put("/api/table-sections/{id}", 11L)
                        .header("X-Tenant-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deactivateAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");
        when(service.deactivate(11L, 7L, 99L)).thenReturn(response(false));

        mockMvc.perform(patch("/api/table-sections/{id}/deactivate", 11L)
                        .header("X-Tenant-Id", 7L)
                        .header("X-User-Id", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser
    void deleteRequiresTablesManage() throws Exception {
        mockMvc.perform(delete("/api/table-sections/{id}", 11L)
                        .header("X-Tenant-Id", 7L))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteAllowsTablesManage() throws Exception {
        securityService.allow("TABLES_MANAGE");

        mockMvc.perform(delete("/api/table-sections/{id}", 11L)
                        .header("X-Tenant-Id", 7L))
                .andExpect(status().isNoContent());

        verify(service).delete(11L, 7L);
    }

    private String sectionJson() {
        return """
                {"branchId":3,"name":"Outdoor","nameAr":"خارجي"}
                """;
    }

    private TableSectionResponse response() {
        return response(true);
    }

    private TableSectionResponse response(boolean active) {
        return new TableSectionResponse(11L, 3L, "Outdoor", "خارجي", active);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        TableSectionService tableSectionService() {
            return mock(TableSectionService.class);
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
