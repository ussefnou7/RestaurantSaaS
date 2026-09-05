package com.smart.restaurant_saas.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpenseApiContractIntegrationTest {

    private static final Long TENANT_ID = 987_001L;
    private static final Long USER_ID = 987_101L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SecurityService securityService;

    @BeforeEach
    void seedTenant() {
        jdbcTemplate.update("DELETE FROM expense WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM expense_category WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Expense Contract Tenant', 'EXPENSE_CONTRACT_TEST', 'ACTIVE',
                    CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        when(securityService.isSysAdmin()).thenReturn(true);
    }

    @Test
    void everyEndpointRunsAgainstTheMigratedSchema() throws Exception {
        Authentication authentication = tenantAuthentication();

        MvcResult initialCategories = mockMvc.perform(get("/api/expense-categories")
                .header("X-Tenant-Id", TENANT_ID)
                .with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'Rent')]").exists())
            .andReturn();
        assertThat(initialCategories.getResponse().getContentAsString()).contains("إيجار");

        MvcResult createdCategory = mockMvc.perform(post("/api/expense-categories")
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", USER_ID)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Office supplies\",\"nameAr\":\"أدوات مكتبية\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.global").value(false))
            .andReturn();
        long categoryId = json(createdCategory).get("id").asLong();

        mockMvc.perform(put("/api/expense-categories/{id}", categoryId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", USER_ID)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Office and stationery\",\"nameAr\":\"مكتب وقرطاسية\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Office and stationery"));

        mockMvc.perform(patch("/api/expense-categories/{id}/deactivate", categoryId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", USER_ID)
                .with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/expense-categories/{id}/activate", categoryId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", USER_ID)
                .with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        MvcResult createdExpense = mockMvc.perform(post("/api/expenses")
                .header("X-Tenant-Id", TENANT_ID)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "branchId": null,
                      "categoryId": %d,
                      "amount": 375.250000,
                      "expenseDate": "2026-09-04",
                      "description": "Printer paper and pens",
                      "payeeName": "Downtown Stationery",
                      "paymentSource": "CASH_ON_HAND"
                    }
                    """.formatted(categoryId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.branchId").doesNotExist())
            .andExpect(jsonPath("$.sourceType").value("MANUAL"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
        long expenseId = json(createdExpense).get("id").asLong();

        mockMvc.perform(get("/api/expenses")
                .header("X-Tenant-Id", TENANT_ID)
                .param("unbranchedOnly", "true")
                .param("categoryId", String.valueOf(categoryId))
                .param("dateFrom", "2026-09-01")
                .param("dateTo", "2026-09-05")
                .param("paymentSource", "CASH_ON_HAND")
                .param("status", "ACTIVE")
                .param("search", "paper")
                .with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(expenseId))
            .andExpect(jsonPath("$.content[0].categoryName").value("Office and stationery"));

        mockMvc.perform(get("/api/expenses/{id}", expenseId)
                .header("X-Tenant-Id", TENANT_ID)
                .with(authentication(authentication)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(expenseId));

        mockMvc.perform(post("/api/expenses/{id}/void", expenseId)
                .header("X-Tenant-Id", TENANT_ID)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate entry\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VOIDED"))
            .andExpect(jsonPath("$.voidedBy").value(USER_ID))
            .andExpect(jsonPath("$.voidReason").value("Duplicate entry"));
    }

    @Test
    void expenseWritesIgnoreSuppliedUserHeaderAndUseAuthenticatedPrincipal() throws Exception {
        long forgedUserId = USER_ID + 999;
        long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM expense_category WHERE tenant_id IS NULL AND name = 'Rent'",
            Long.class);
        Authentication authentication = tenantAuthentication();

        MvcResult created = mockMvc.perform(post("/api/expenses")
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", forgedUserId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "branchId": null,
                      "categoryId": %d,
                      "amount": 10.000000,
                      "expenseDate": "2026-09-05",
                      "paymentSource": "CASH_ON_HAND"
                    }
                    """.formatted(categoryId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value(USER_ID))
            .andReturn();
        long expenseId = json(created).get("id").asLong();

        mockMvc.perform(post("/api/expenses/{id}/void", expenseId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", forgedUserId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Regression guard\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voidedBy").value(USER_ID));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT created_by FROM expense WHERE id = ?", Long.class, expenseId))
            .isEqualTo(USER_ID);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT voided_by FROM expense WHERE id = ?", Long.class, expenseId))
            .isEqualTo(USER_ID);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Authentication tenantAuthentication() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
            USER_ID, TENANT_ID, "expense-contract-user", RoleCode.OWNER.name());
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(RoleCode.OWNER.name())));
    }
}
