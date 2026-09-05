package com.smart.restaurant_saas.expense.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
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
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryResponse;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ExpenseCategoryController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({ExpenseCategoryControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class ExpenseCategoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseCategoryService categoryService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(categoryService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listRequiresExpensesView() throws Exception {
        mockMvc.perform(get("/api/expense-categories"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRequiresCategoryManage() throws Exception {
        mockMvc.perform(post("/api/expense-categories")
                .contentType("application/json")
                .content(requestJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void updateRequiresCategoryManage() throws Exception {
        mockMvc.perform(put("/api/expense-categories/20")
                .contentType("application/json")
                .content(requestJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void activateRequiresCategoryManage() throws Exception {
        mockMvc.perform(patch("/api/expense-categories/20/activate"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deactivateRequiresCategoryManage() throws Exception {
        mockMvc.perform(patch("/api/expense-categories/20/deactivate"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void endpointsReturnTheirDocumentedSuccessContracts() throws Exception {
        ExpenseCategoryResponse response = response();
        when(categoryService.findAll(7L)).thenReturn(List.of(response));
        when(categoryService.create(any(), any(), any())).thenReturn(response);
        when(categoryService.update(any(), any(), any(), any())).thenReturn(response);
        when(categoryService.activate(20L, 7L, 9L)).thenReturn(response);
        when(categoryService.deactivate(20L, 7L, 9L)).thenReturn(response);

        securityService.allow("EXPENSES_VIEW");
        mockMvc.perform(get("/api/expense-categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Staff meals"));

        securityService.allow("EXPENSES_CATEGORY_MANAGE");
        mockMvc.perform(post("/api/expense-categories")
                .header("X-User-Id", "9")
                .contentType("application/json")
                .content(requestJson()))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/expense-categories/20")
                .header("X-User-Id", "9")
                .contentType("application/json")
                .content(requestJson()))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/expense-categories/20/activate")
                .header("X-User-Id", "9"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/expense-categories/20/deactivate")
                .header("X-User-Id", "9"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void categoryDeleteRouteDoesNotExist() throws Exception {
        securityService.allow("EXPENSES_CATEGORY_MANAGE");

        mockMvc.perform(delete("/api/expense-categories/20"))
            .andExpect(status().isNotFound());
    }

    private static String requestJson() {
        return "{\"name\":\"Staff meals\",\"nameAr\":\"وجبات الموظفين\"}";
    }

    private static ExpenseCategoryResponse response() {
        return ExpenseCategoryResponse.builder()
            .id(20L)
            .tenantId(7L)
            .name("Staff meals")
            .nameAr("وجبات الموظفين")
            .active(true)
            .global(false)
            .createdBy(9L)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ExpenseCategoryService expenseCategoryService() {
            return mock(ExpenseCategoryService.class);
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

        void allow(String permissionCode) {
            permissions.put(permissionCode, true);
        }

        void reset() {
            permissions.clear();
        }
    }
}
