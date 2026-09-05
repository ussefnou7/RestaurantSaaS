package com.smart.restaurant_saas.expense;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.JwtAuthenticationFilter;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseSourceType;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import com.smart.restaurant_saas.expense.dto.ExpenseResponse;
import com.smart.restaurant_saas.tenant.SliceTenantConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ExpenseController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({ExpenseControllerSecurityTest.MethodSecurityConfig.class, SliceTenantConfig.class})
class ExpenseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private RecordingSecurityService securityService;

    @BeforeEach
    void setUp() {
        reset(expenseService);
        securityService.reset();
    }

    @Test
    @WithMockUser
    void listRequiresExpensesView() throws Exception {
        mockMvc.perform(get("/api/expenses"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getRequiresExpensesView() throws Exception {
        mockMvc.perform(get("/api/expenses/100"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void createRequiresExpensesCreate() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .contentType("application/json")
                .content(createJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void voidRequiresExpensesVoid() throws Exception {
        mockMvc.perform(post("/api/expenses/100/void")
                .header("X-User-Id", "9")
                .contentType("application/json")
                .content("{\"reason\":\"Duplicate\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void endpointsReturnTheirDocumentedSuccessContracts() throws Exception {
        ExpenseResponse active = response(ExpenseStatus.ACTIVE);
        ExpenseResponse voided = response(ExpenseStatus.VOIDED);
        when(expenseService.findAll(any(), any(), any(Boolean.class), any(), any(), any(),
                any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(active)));
        when(expenseService.findById(100L, 7L)).thenReturn(active);
        when(expenseService.create(any(), any(), any())).thenReturn(active);
        when(expenseService.voidExpense(100L, 7L, 9L, "Duplicate")).thenReturn(voided);

        securityService.allow("EXPENSES_VIEW");
        mockMvc.perform(get("/api/expenses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].categoryName").value("Rent"))
            .andExpect(jsonPath("$.content[0].amount").value(125.5));
        mockMvc.perform(get("/api/expenses/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(100L));

        securityService.allow("EXPENSES_CREATE");
        mockMvc.perform(post("/api/expenses")
                .header("X-User-Id", "9")
                .contentType("application/json")
                .content(createJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        securityService.allow("EXPENSES_VOID");
        mockMvc.perform(post("/api/expenses/100/void")
                .header("X-User-Id", "9")
                .contentType("application/json")
                .content("{\"reason\":\"Duplicate\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    @WithMockUser
    void expenseUpdateAndDeleteRoutesDoNotExist() throws Exception {
        securityService.allow("EXPENSES_CREATE");
        securityService.allow("EXPENSES_VOID");

        mockMvc.perform(put("/api/expenses/100")
                .contentType("application/json")
                .content(createJson()))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/expenses/100"))
            .andExpect(status().isNotFound());
    }

    private static String createJson() {
        return """
            {
              "branchId": null,
              "categoryId": 20,
              "amount": 125.500000,
              "expenseDate": "2026-01-15",
              "description": "January rent",
              "payeeName": "Landlord",
              "paymentSource": "BANK"
            }
            """;
    }

    private static ExpenseResponse response(ExpenseStatus status) {
        return ExpenseResponse.builder()
            .id(100L)
            .categoryId(20L)
            .categoryName("Rent")
            .categoryNameAr("إيجار")
            .amount(new BigDecimal("125.500000"))
            .expenseDate(LocalDate.of(2026, 1, 15))
            .description("January rent")
            .payeeName("Landlord")
            .paymentSource(ExpensePaymentSource.BANK)
            .sourceType(ExpenseSourceType.MANUAL)
            .status(status)
            .voidedAt(status == ExpenseStatus.VOIDED
                ? LocalDateTime.of(2026, 1, 16, 10, 0) : null)
            .voidedBy(status == ExpenseStatus.VOIDED ? 9L : null)
            .voidReason(status == ExpenseStatus.VOIDED ? "Duplicate" : null)
            .createdBy(9L)
            .createdAt(LocalDateTime.of(2026, 1, 15, 9, 30))
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        ExpenseService expenseService() {
            return mock(ExpenseService.class);
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
