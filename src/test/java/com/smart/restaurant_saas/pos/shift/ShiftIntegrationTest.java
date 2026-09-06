package com.smart.restaurant_saas.pos.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The guards that only exist below the service: schema constraints, and the permission gates as a
 * real request actually meets them.
 *
 * <p>Every token here is minted by the application's own {@link JwtService} with a real
 * {@code deviceId} claim, so these exercise the same principal construction a POS request does. A
 * hand-built principal could carry a shape the login path never produces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShiftIntegrationTest {

    private static final long BASE = 974_000L;
    private static final long TENANT_ID = BASE;
    private static final long BRANCH_ID = BASE + 1;
    private static final long OTHER_BRANCH_ID = BASE + 2;
    private static final long DEVICE_ID = BASE + 3;
    private static final long OTHER_DEVICE_ID = BASE + 4;
    private static final long CASHIER_ID = BASE + 5;
    private static final long COLLEAGUE_ID = BASE + 6;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    /**
     * These tests run inside the caller's transaction, so an UPDATE issued by the service sits
     * unflushed in the persistence context while {@link JdbcTemplate} reads straight past it. An
     * INSERT is written eagerly (IDENTITY keys force it), which is why opening a shift is visible
     * without this and closing one is not. Flush before every raw-SQL assertion about a change.
     */
    @PersistenceContext private EntityManager entityManager;

    private String cashierToken;
    private String colleagueToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        seedTenantAndBranches();
        seedDevice(DEVICE_ID, BRANCH_ID, "Till 1");
        seedDevice(OTHER_DEVICE_ID, OTHER_BRANCH_ID, "Other branch till");
        seedUser(CASHIER_ID, "cashier_a");
        seedUser(COLLEAGUE_ID, "cashier_b");

        grant(CASHIER_ID, "SHIFTS_OPEN", "SHIFTS_CLOSE", "SHIFTS_VIEW", "ORDERS_CREATE");
        grant(COLLEAGUE_ID, "SHIFTS_OPEN", "SHIFTS_CLOSE", "SHIFTS_VIEW");

        cashierToken = tokenFor(CASHIER_ID, "cashier_a", DEVICE_ID);
        colleagueToken = tokenFor(COLLEAGUE_ID, "cashier_b", DEVICE_ID);
    }

    // ------------------------------------------------------------------ the database constraint

    /**
     * D120 moves this from a service check to a database one. V22 stated the opposite in a comment
     * and created no index, so two concurrent opens could both pass an application-level look-then-
     * insert. Two open shifts on one drawer are two accounts of the same money, and no variance
     * could be attributed to either.
     *
     * <p>Asserted against the constraint directly rather than through the service, because the
     * service's own check would mask it — which is precisely the arrangement being replaced.
     */
    @Test
    void databaseRefusesASecondOpenShiftOnTheSameDevice() {
        insertOpenShift(BASE + 10, DEVICE_ID, CASHIER_ID, LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> insertOpenShift(BASE + 11, DEVICE_ID, COLLEAGUE_ID, LocalDate.of(2026, 9, 1)))
            .isInstanceOf(DuplicateKeyException.class);
    }

    /** The same index must not block a second CLOSED shift, or a device could only ever run once. */
    @Test
    void databaseAllowsManyClosedShiftsOnOneDevice() {
        insertClosedShift(BASE + 12, DEVICE_ID, CASHIER_ID, "100", "100");
        insertClosedShift(BASE + 13, DEVICE_ID, CASHIER_ID, "100", "100");

        assertThat(countShiftsOnDevice(DEVICE_ID)).isEqualTo(2);
    }

    /**
     * forcedClose is derived from {@code closedBy != openedBy} and never accepted from a client
     * (D122). The schema refuses the contradiction outright, so no service path — present or
     * future — can record a forced close that the actors do not support.
     */
    @Test
    void databaseRefusesForcedCloseWhenTheCloserIsAlsoTheOpener() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               closed_by_user_id, forced_close, opening_count, closing_count,
                               expected_cash, variance, expenses_at_close, status, opened_at,
                               closed_at, created_at)
            VALUES (?, ?, ?, DATE '2026-09-01', ?, ?, TRUE, 100, 100, 100, 0, 0, 'CLOSED',
                    TIMESTAMP '2026-09-01 08:00', TIMESTAMP '2026-09-01 16:00', CURRENT_TIMESTAMP)
            """, BASE + 14, TENANT_ID, DEVICE_ID, CASHIER_ID, CASHIER_ID))
            .hasMessageContaining("chk_shift_forced_close");
    }

    /**
     * An OPEN shift carries no close figures at all, so there is no expected figure sitting in the
     * row for a read path to leak before the drawer is counted (D123).
     */
    @Test
    void databaseRefusesAnExpectedFigureOnAnOpenShift() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               forced_close, opening_count, expected_cash, status, opened_at, created_at)
            VALUES (?, ?, ?, DATE '2026-09-01', ?, FALSE, 100, 450, 'OPEN',
                    TIMESTAMP '2026-09-01 08:00', CURRENT_TIMESTAMP)
            """, BASE + 15, TENANT_ID, DEVICE_ID, CASHIER_ID))
            .hasMessageContaining("chk_shift_close_fields");
    }

    // ------------------------------------------------------------------ endpoints and permissions

    @Test
    void openThenCurrentReturnsTheShiftWithoutAnyExpectedFigure() throws Exception {
        // doesNotHaveJsonPath, not doesNotExist: the latter also passes for a key present with a
        // null value, and on an OPEN shift these columns are null by constraint anyway — so the
        // weaker matcher passed even with the fields put back on the DTO. It pinned nothing.
        openShift(cashierToken, "200.00").andExpect(status().isCreated());

        mockMvc.perform(get("/api/shifts/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shift.openedByUserId").value(CASHIER_ID))
            .andExpect(jsonPath("$.shift.openingCount").value(200.0))
            .andExpect(jsonPath("$.shift.expectedCash").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.shift.variance").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.shift.handoverVariance").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.shift.totalByPaymentMethod").doesNotHaveJsonPath());
    }

    /** No open shift is the first login of the day, not an error (correction 2). */
    @Test
    void currentWithNoOpenShiftIsAnEmptyResultNotAnError() throws Exception {
        mockMvc.perform(get("/api/shifts/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shift").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void openingTwiceAsTheSameCashierResumesWithoutWritingASecondRow() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());
        // 200, not 201: a resume creates nothing.
        openShift(cashierToken, "999.00").andExpect(status().isOk());

        entityManager.flush();
        assertThat(countShiftsOnDevice(DEVICE_ID)).isEqualTo(1);
        // The resumed shift keeps the original count; the 999 is discarded rather than written.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT opening_count FROM shift WHERE device_id = ?", java.math.BigDecimal.class, DEVICE_ID))
            .isEqualByComparingTo("200.000000");
    }

    @Test
    void openingOnAColleaguesDrawerFailsWithItsOwnCode() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());

        openShift(colleagueToken, "50.00")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SHIFT_OPEN_BY_ANOTHER_USER"));
    }

    /**
     * The live defect this rewrite fixes: SHIFTS_CLOSE was seeded but enforced nowhere, and all
     * three endpoints required SHIFTS_OPEN — which the CASHIER role holds. Any cashier could close
     * any shift in the tenant.
     */
    @Test
    void closingAColleaguesShiftWithoutForceClosePermissionIsRejected() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());
        long shiftId = openShiftId();

        closeShift(colleagueToken, shiftId, "200.00")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("SHIFT_FORCE_CLOSE_NOT_PERMITTED"));

        assertThat(statusOf(shiftId)).isEqualTo("OPEN");
    }

    @Test
    void withForceClosePermissionTheColleagueClosesItAndItIsRecordedAsForced() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());
        long shiftId = openShiftId();
        grant(COLLEAGUE_ID, "SHIFTS_FORCE_CLOSE");

        closeShift(colleagueToken, shiftId, "180.00").andExpect(status().isOk());

        entityManager.flush();
        assertThat(statusOf(shiftId)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT forced_close FROM shift WHERE id = ?", Boolean.class, shiftId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT closed_by_user_id FROM shift WHERE id = ?", Long.class, shiftId))
            .isEqualTo(COLLEAGUE_ID);
    }

    /**
     * D123 as an API property rather than a UI one. If the figure were returned and the POS merely
     * declined to render it, it would sit in the network tab and the guarantee would be one commit
     * from gone.
     */
    @Test
    void theCloseResponseCarriesNoExpectedFigureAndNoVariance() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());
        long shiftId = openShiftId();

        closeShift(cashierToken, shiftId, "180.00")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closingCount").value(180.0))
            .andExpect(jsonPath("$.expectedCash").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.variance").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.handoverVariance").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.expensesAtClose").doesNotHaveJsonPath());

        // Stored, though — the figure exists, it is just not sent to the till.
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT variance FROM shift WHERE id = ?", java.math.BigDecimal.class, shiftId))
            .isEqualByComparingTo("-20.000000");
    }

    @Test
    void aClosedShiftCannotBeClosedAgain() throws Exception {
        openShift(cashierToken, "200.00").andExpect(status().isCreated());
        long shiftId = openShiftId();
        closeShift(cashierToken, shiftId, "200.00").andExpect(status().isOk());

        closeShift(cashierToken, shiftId, "150.00")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SHIFT_ALREADY_CLOSED"));

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT closing_count FROM shift WHERE id = ?", java.math.BigDecimal.class, shiftId))
            .isEqualByComparingTo("200.000000");
    }

    /** A web session has no drawer, so every shift path rejects it (D127). */
    @Test
    void aTokenWithoutADeviceClaimCannotOpenAShift() throws Exception {
        String webToken = jwtService.generateAccessToken(
            CASHIER_ID, TENANT_ID, "cashier_a", RoleCode.OWNER.name());

        openShift(webToken, "200.00")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("DEVICE_IDENTITY_REQUIRED"));
    }

    @Test
    void withoutViewVariancePermissionTheListOmitsTheFiguresEntirely() throws Exception {
        insertClosedShift(BASE + 20, DEVICE_ID, CASHIER_ID, "100", "80");

        mockMvc.perform(get("/api/shifts").param("branchId", String.valueOf(BRANCH_ID))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(BASE + 20))
            .andExpect(jsonPath("$.content[0].variance").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.content[0].expectedCash").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.content[0].handoverVariance").doesNotHaveJsonPath());
    }

    @Test
    void withViewVariancePermissionTheFiguresAppear() throws Exception {
        insertClosedShift(BASE + 21, DEVICE_ID, CASHIER_ID, "100", "80");
        grant(CASHIER_ID, "SHIFTS_VIEW_VARIANCE");

        mockMvc.perform(get("/api/shifts").param("branchId", String.valueOf(BRANCH_ID))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].variance").value(-20.0));
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions openShift(String token, String count)
            throws Exception {
        return mockMvc.perform(post("/api/shifts/open")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"openingCount\":" + count + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions closeShift(
            String token, long shiftId, String count) throws Exception {
        return mockMvc.perform(post("/api/shifts/" + shiftId + "/close")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"closingCount\":" + count + "}"));
    }

    private long openShiftId() {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM shift WHERE device_id = ? AND status = 'OPEN'", Long.class, DEVICE_ID);
    }

    private String statusOf(long shiftId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM shift WHERE id = ?", String.class, shiftId);
    }

    private int countShiftsOnDevice(long deviceId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM shift WHERE device_id = ?", Integer.class, deviceId);
    }

    private void insertOpenShift(long id, long deviceId, long userId, LocalDate businessDate) {
        jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               forced_close, opening_count, status, opened_at, created_at)
            VALUES (?, ?, ?, ?, ?, FALSE, 100, 'OPEN', TIMESTAMP '2026-09-01 08:00', CURRENT_TIMESTAMP)
            """, id, TENANT_ID, deviceId, businessDate, userId);
    }

    private void insertClosedShift(long id, long deviceId, long userId, String opening, String closing) {
        jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               closed_by_user_id, forced_close, opening_count, closing_count,
                               expected_cash, variance, expenses_at_close, status, opened_at,
                               closed_at, created_at)
            VALUES (?, ?, ?, DATE '2026-09-01', ?, ?, FALSE, ?, ?, ?, ?, 0, 'CLOSED',
                    TIMESTAMP '2026-09-01 08:00', TIMESTAMP '2026-09-01 16:00', CURRENT_TIMESTAMP)
            """, id, TENANT_ID, deviceId, userId, userId,
            new java.math.BigDecimal(opening), new java.math.BigDecimal(closing),
            new java.math.BigDecimal(opening),
            new java.math.BigDecimal(closing).subtract(new java.math.BigDecimal(opening)));
    }

    private void seedTenantAndBranches() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Shift IT', 'SHIFT_IT', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);
        seedBranch(BRANCH_ID, "Main");
        seedBranch(OTHER_BRANCH_ID, "Other");
    }

    private void seedBranch(long id, String name) {
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, name, "BR_" + id);
    }

    private void seedDevice(long id, long branchId, String name) {
        jdbcTemplate.update("""
            INSERT INTO device (id, tenant_id, branch_id, name, secret_key_hash, active, created_at)
            VALUES (?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, branchId, name, "hash-" + id);
    }

    private void seedUser(long id, String username) {
        Long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL", Long.class, RoleCode.OWNER.name());
        jdbcTemplate.update("""
            INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                               created_at, role_id)
            VALUES (?, ?, ?, ?, 'x', 'ACTIVE', CURRENT_TIMESTAMP, ?)
            """, id, TENANT_ID, username, username, roleId);
    }

    /**
     * Permissions are direct user grants; the role is a gate and grants nothing. OWNER is used
     * above only because it is the global role the fixture can rely on — it confers no shift
     * permission by itself, so each code below is genuinely load-bearing.
     */
    private void grant(long userId, String... codes) {
        for (String code : codes) {
            Long permissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM permissions WHERE code = ?", Long.class, code);
            jdbcTemplate.update("""
                INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, TENANT_ID, userId, permissionId);
        }
    }

    private String tokenFor(long userId, String username, long deviceId) {
        return jwtService.generateAccessToken(
            userId, TENANT_ID, username, RoleCode.OWNER.name(), deviceId);
    }

    private void cleanUp() {
        jdbcTemplate.update("UPDATE orders SET shift_id = NULL WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM expense WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM shift WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM user_permissions WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM device WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
    }
}
