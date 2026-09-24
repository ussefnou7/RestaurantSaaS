package com.smart.restaurant_saas.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * D135 end to end, over the real filter rather than a hand-installed principal.
 *
 * <p>The unit tests prove {@code CurrentUserScopeProvider} decides correctly when told the caller
 * is scoped. They cannot prove the caller <em>is</em> told — that runs through
 * {@code roles.is_branch_scoped}, the authentication query's projection, and
 * {@code JwtAuthenticationFilter}. This covers that chain, and is therefore also the test that
 * fails if V65 is reverted or the role flag is flipped back.
 *
 * <p>{@code /api/expenses} is the probe because it is an ordinary permission-gated list with an
 * optional {@code branchId}. Nothing here is about expenses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchScopeIntegrationTest {

    private static final long BASE = 969_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private CrossTenantFixture fixture;
    private long ownBranchId;
    private long otherBranchId;
    private String cashierToken;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        fixture = new CrossTenantFixture(jdbcTemplate, jwtService, BASE);
        fixture.reset(1);
        ownerToken = fixture.seedTenantWithUser(0, "SCOPE", "EXPENSES_VIEW");

        ownBranchId = seedBranch(1, "OWN");
        otherBranchId = seedBranch(2, "OTHER");
        cashierToken = seedScopedCashier(ownBranchId);

        seedExpense(1, ownBranchId, "40.000000");
        seedExpense(2, otherBranchId, "70.000000");
        seedExpense(3, null, "90.000000");
    }

    /** The default flip: no branchId asked for, and the cashier still gets only their own. */
    @Test
    void aScopedCallerListingWithNoFilterSeesOnlyTheirOwnBranch() throws Exception {
        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(cashierToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].branchId").value(ownBranchId));
    }

    /** Refused, not silently emptied — an empty page would read as "that branch has nothing". */
    @Test
    void aScopedCallerAskingForAnotherBranchIsRefused() throws Exception {
        mockMvc.perform(get("/api/expenses")
                .param("branchId", String.valueOf(otherBranchId))
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(cashierToken)))
            .andExpect(status().isForbidden());
    }

    /** An unbranched expense is company-level data, so it is not theirs either. */
    @Test
    void aScopedCallerCannotAskForUnbranchedRecords() throws Exception {
        mockMvc.perform(get("/api/expenses")
                .param("unbranchedOnly", "true")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(cashierToken)))
            .andExpect(status().isForbidden());
    }

    /** The control: the same three rows, unfiltered, for a role that is not branch-scoped. */
    @Test
    void anUnscopedCallerStillSeesEveryBranch() throws Exception {
        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3));
    }

    /**
     * The scope is live, like the role code beside it: moving the cashier to another branch takes
     * effect on their next request, without re-issuing the token.
     */
    @Test
    void movingTheCashierChangesWhatTheSameTokenSees() throws Exception {
        jdbcTemplate.update("UPDATE users SET branch_id = ? WHERE id = ?", otherBranchId, cashierUserId());

        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(cashierToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].branchId").value(otherBranchId));
    }

    /**
     * The role flag is what makes all of the above true, so this asserts the dependency directly:
     * flip {@code CASHIER} back to unscoped — reverting what V65 did — and the same token, the
     * same user and the same rows stop being narrowed.
     *
     * <p>Done inside the test transaction rather than against the database, so it rolls back and
     * cannot leak into another test's fixture.
     */
    @Test
    void unscopingTheRoleRemovesTheNarrowing() throws Exception {
        jdbcTemplate.update(
            "UPDATE roles SET is_branch_scoped = false WHERE code = ? AND tenant_id IS NULL",
            RoleCode.CASHIER.name());

        mockMvc.perform(get("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, CrossTenantFixture.bearer(cashierToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3));
    }

    private long cashierUserId() {
        return fixture.userId(0) + 500;
    }

    private long seedBranch(int offset, String label) {
        long branchId = BASE + 100 + offset;
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, ?, ?, true, CURRENT_TIMESTAMP)
            """, branchId, fixture.tenantId(0), "Branch " + label, "BR_" + label);
        return branchId;
    }

    private String seedScopedCashier(long branchId) {
        long userId = cashierUserId();
        Long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL",
            Long.class, RoleCode.CASHIER.name());

        jdbcTemplate.update("""
            INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                               created_at, role_id, branch_id)
            VALUES (?, ?, 'Scoped Cashier', 'cashier_scope', 'x', 'ACTIVE',
                    CURRENT_TIMESTAMP, ?, ?)
            """, userId, fixture.tenantId(0), roleId, branchId);

        Long permissionId = jdbcTemplate.queryForObject(
            "SELECT id FROM permissions WHERE code = ?", Long.class, "EXPENSES_VIEW");
        jdbcTemplate.update("""
            INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, fixture.tenantId(0), userId, permissionId);

        return jwtService.generateAccessToken(
            userId, fixture.tenantId(0), "cashier_scope", RoleCode.CASHIER.name());
    }

    private void seedExpense(int offset, Long branchId, String amount) {
        Long categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM expense_category WHERE tenant_id IS NULL LIMIT 1", Long.class);
        jdbcTemplate.update("""
            INSERT INTO expense (id, tenant_id, branch_id, category_id, amount, expense_date,
                                 description, payment_source, source_type, status, created_at)
            VALUES (?, ?, ?, ?, CAST(? AS numeric), CURRENT_DATE, 'seed', 'CASH_ON_HAND',
                    'MANUAL', 'ACTIVE', CURRENT_TIMESTAMP)
            """, BASE + 200 + offset, fixture.tenantId(0), branchId, categoryId, amount);
    }
}
