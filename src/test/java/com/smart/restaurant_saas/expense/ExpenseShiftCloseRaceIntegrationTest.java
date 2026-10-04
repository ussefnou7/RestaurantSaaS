package com.smart.restaurant_saas.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ledger L008 — the expense/close race, pinned deterministically.
 *
 * <p><b>The defect.</b> {@code ShiftService.closeShift} freezes the expense total at
 * {@code ShiftService.java:189-190} while holding the shift row lock it took at {@code :163}.
 * {@code ExpenseService.validatePaidFromShift} reads the same shift with the <em>unlocked</em>
 * {@code findByIdAndTenantId} ({@code ExpenseService.java:236}), so recording an expense never
 * contends for that lock. An expense that commits after {@code sumActiveByShift} has run but
 * before {@code closedAt} is written is therefore excluded from {@code expensesAtClose}; and
 * because {@code recordedAfterShiftClose} derives lateness at read time from
 * {@code createdAt > closedAt}, its earlier {@code createdAt} also makes it read as recorded
 * <em>before</em> the close. The money is missing from the variance and nothing flags it.
 *
 * <p><b>How the interleaving is made deterministic.</b> Not by sleeps or by hoping two threads
 * collide. The expense is inserted inside a transaction that is then held open on a latch. An
 * uncommitted row is invisible to {@code sumActiveByShift} no matter when it runs, while the row's
 * {@code created_at} was already fixed at insert time — which is exactly the state the race
 * produces, reached by construction rather than by luck.
 *
 * <h2>These tests assert today's defective behaviour, on purpose</h2>
 *
 * <p>L008 is an open finding and this audit is not fixing it (L006 is a find-don't-fix pass), so
 * asserting the correct behaviour would leave a permanently red suite and destroy the signal the
 * audit exists to create. Instead each assertion below names the wrong value and the right one
 * next to it. <b>When L008 is fixed these tests must be inverted</b>, and the comments mark every
 * line that has to change — they double as the fix's acceptance criteria.
 *
 * <p>No {@code @Transactional}: the whole point is two uncoordinated transactions. Teardown is
 * manual.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpenseShiftCloseRaceIntegrationTest {

    private static final long BASE = 976_000L;
    private static final long TENANT_ID = BASE;
    private static final long BRANCH_ID = BASE + 1;
    private static final long DEVICE_ID = BASE + 2;
    private static final long CASHIER_ID = BASE + 3;
    private static final long SHIFT_ID = BASE + 4;
    private static final long CATEGORY_ID = BASE + 5;

    /** Recorded and committed well before the close. Must land in the frozen sum. */
    private static final BigDecimal EARLY_EXPENSE = new BigDecimal("10.000000");
    /** Inserted before the close but committed after it. The one the race loses. */
    private static final BigDecimal RACING_EXPENSE = new BigDecimal("50.000000");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private String token;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        cleanUp();
        seed();
        token = jwtService.generateAccessToken(
            CASHIER_ID, TENANT_ID, "cashier_l008", RoleCode.OWNER.name(), DEVICE_ID);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        cleanUp();
    }

    /**
     * The race, end to end. An expense in flight across the close is lost from the frozen sum and
     * then reported as having been recorded before the close — both halves missed, as filed.
     */
    @Test
    void anExpenseCommittingAcrossTheCloseIsLostFromTheSumAndFromTheLateClassification()
            throws Exception {
        insertExpense(BASE + 10, EARLY_EXPENSE);

        CountDownLatch racingExpenseWritten = new CountDownLatch(1);
        CountDownLatch commitRacingExpense = new CountDownLatch(1);

        Future<?> racingExpense = executor.submit(() -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> {
                // created_at is fixed here, before the close writes closed_at. The row stays
                // invisible to sumActiveByShift until this transaction commits, below.
                insertExpense(BASE + 11, RACING_EXPENSE);
                racingExpenseWritten.countDown();
                await(commitRacingExpense);
            }));

        assertThat(racingExpenseWritten.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(closeShift()).isEqualTo(200);

        commitRacingExpense.countDown();
        racingExpense.get(5, TimeUnit.SECONDS);

        // Both expenses are now committed against this shift.
        assertThat(sumOfCommittedExpenses())
            .isEqualByComparingTo(EARLY_EXPENSE.add(RACING_EXPENSE));

        // L008, half one: the frozen sum kept only the early expense. The racing 50 is gone from
        // the shift's books. WHEN L008 IS FIXED this must become EARLY_EXPENSE.add(RACING_EXPENSE),
        // or the close must refuse to proceed while an expense is in flight.
        assertThat(storedExpensesAtClose()).isEqualByComparingTo(EARLY_EXPENSE);

        // ... and the variance is overstated by exactly the lost expense, because expectedCash
        // subtracted a total that was 50 short.
        assertThat(storedExpectedCash())
            .isEqualByComparingTo(openingCount().subtract(EARLY_EXPENSE));

        // L008, half two: the lost expense is not even flagged as late, because its created_at
        // precedes closed_at. WHEN L008 IS FIXED this must be true (recorded after close) or the
        // expense must be inside the sum above — it must not be both excluded and unflagged.
        assertThat(recordedAfterShiftCloseFor(BASE + 11)).isFalse();
    }

    /**
     * The mechanism underneath the race, isolated: recording an expense against a shift does not
     * wait for the lock {@code closeShift} holds on that shift.
     *
     * <p>The lock is taken through {@link ShiftRepository#findByIdAndTenantIdForUpdate} — the very
     * method {@code ShiftService.java:163} calls — and not through hand-written
     * {@code SELECT ... FOR UPDATE}. The two are not the same lock, which is easy to get wrong: a
     * raw {@code FOR UPDATE} <em>does</em> block the expense insert, because PostgreSQL takes
     * {@code FOR KEY SHARE} on the parent row to check {@code fk_expense_paid_from_shift} and that
     * conflicts with {@code FOR UPDATE}. Holding the lock the shipped code actually takes is what
     * makes this evidence about {@code closeShift} rather than about a lock it never requests.
     *
     * <p>WHEN L008 IS FIXED by making expense creation contend for the same shift row, this
     * request will block and the assertion below has to become a timeout expectation instead.
     */
    @Test
    void recordingAnExpenseDoesNotWaitForTheShiftRowLockTheCloseHolds() throws Exception {
        CountDownLatch shiftRowLocked = new CountDownLatch(1);
        CountDownLatch releaseShiftRow = new CountDownLatch(1);

        Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> {
                shiftRepository.findByIdAndTenantIdForUpdate(SHIFT_ID, TENANT_ID).orElseThrow();
                shiftRowLocked.countDown();
                await(releaseShiftRow);
            }));

        assertThat(shiftRowLocked.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Integer> expenseStatus = executor.submit(this::createExpenseViaApi);

        // Completes while the close's own lock is held. A guard that serialised the two could not,
        // which is precisely what L008 says is missing.
        assertThat(expenseStatus.get(3, TimeUnit.SECONDS)).isEqualTo(201);

        releaseShiftRow.countDown();
        lockHolder.get(5, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ helpers

    private int closeShift() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shifts/" + SHIFT_ID + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closingCount\":100}"))
            .andReturn();
        return result.getResponse().getStatus();
    }

    private int createExpenseViaApi() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/expenses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"branchId":%d,"categoryId":%d,"amount":25,"expenseDate":"%s",
                     "paymentSource":"CASH_DRAWER","paidFromShiftId":%d}
                    """.formatted(BRANCH_ID, CATEGORY_ID, java.time.LocalDate.now(), SHIFT_ID)))
            .andReturn();
        return result.getResponse().getStatus();
    }

    /**
     * Raw SQL rather than {@code ExpenseService.create}, because the service call has to happen
     * inside a transaction this test holds open, and the service resolves its actor from the
     * request-scoped security context that a bare worker thread does not have. The columns written
     * here are the ones the sum and the classifier read: amount, status, paid_from_shift_id and
     * created_at. {@link #recordingAnExpenseDoesNotWaitForTheShiftRowLockTheCloseHolds} covers the
     * real service path.
     */
    private void insertExpense(long id, BigDecimal amount) {
        jdbcTemplate.update("""
            INSERT INTO expense (id, tenant_id, branch_id, category_id, amount, expense_date,
                                 payment_source, source_type, status, paid_from_shift_id,
                                 created_at, created_by)
            VALUES (?, ?, ?, ?, ?, CURRENT_DATE, 'CASH_DRAWER', 'MANUAL', 'ACTIVE', ?,
                    CURRENT_TIMESTAMP, ?)
            """, id, TENANT_ID, BRANCH_ID, CATEGORY_ID, amount, SHIFT_ID, CASHIER_ID);
    }

    private BigDecimal sumOfCommittedExpenses() {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(sum(amount), 0) FROM expense
             WHERE paid_from_shift_id = ? AND status = 'ACTIVE'
            """, BigDecimal.class, SHIFT_ID);
    }

    private BigDecimal storedExpensesAtClose() {
        return jdbcTemplate.queryForObject(
            "SELECT expenses_at_close FROM shift WHERE id = ?", BigDecimal.class, SHIFT_ID);
    }

    private BigDecimal storedExpectedCash() {
        return jdbcTemplate.queryForObject(
            "SELECT expected_cash FROM shift WHERE id = ?", BigDecimal.class, SHIFT_ID);
    }

    private BigDecimal openingCount() {
        return jdbcTemplate.queryForObject(
            "SELECT opening_count FROM shift WHERE id = ?", BigDecimal.class, SHIFT_ID);
    }

    /**
     * The classifier's own rule, read from committed state: an expense counts as late only when it
     * was created after the shift's {@code closed_at}. Both timestamps are in the same zone here —
     * the tenant and its branch share one — so the conversion
     * {@code ExpenseService.recordedAfterShiftClose} performs is an identity in this fixture and
     * the comparison below matches what the API returns.
     */
    private boolean recordedAfterShiftCloseFor(long expenseId) {
        return jdbcTemplate.queryForObject("""
            SELECT e.created_at > s.closed_at
              FROM expense e JOIN shift s ON s.id = e.paid_from_shift_id
             WHERE e.id = ?
            """, Boolean.class, expenseId);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out holding the expense/shift transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the transaction", ex);
        }
    }

    private void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Expense Race', 'EXP_RACE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, created_at)
            VALUES (?, ?, 'Race Branch', ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID, "BR_" + BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO device (id, tenant_id, branch_id, name, secret_key_hash, active, created_at)
            VALUES (?, ?, ?, 'Race Till', ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, DEVICE_ID, TENANT_ID, BRANCH_ID, "hash-" + DEVICE_ID);
        jdbcTemplate.update("""
            INSERT INTO expense_category (id, tenant_id, name, active, created_at)
            VALUES (?, ?, 'Race Category', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, CATEGORY_ID, TENANT_ID);

        Long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL",
            Long.class, RoleCode.OWNER.name());
        jdbcTemplate.update("""
            INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                               created_at, role_id)
            VALUES (?, ?, 'L008 Cashier', 'cashier_l008', 'x', 'ACTIVE', CURRENT_TIMESTAMP, ?)
            """, CASHIER_ID, TENANT_ID, roleId);
        grant("SHIFTS_CLOSE", "SHIFTS_VIEW", "EXPENSES_CREATE", "EXPENSES_VIEW");

        jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               forced_close, opening_count, status, opened_at, created_at)
            VALUES (?, ?, ?, CURRENT_DATE, ?, FALSE, 500, 'OPEN',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, SHIFT_ID, TENANT_ID, DEVICE_ID, CASHIER_ID);
    }

    private void grant(String... codes) {
        for (String code : codes) {
            Long permissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM permissions WHERE code = ?", Long.class, code);
            jdbcTemplate.update("""
                INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, TENANT_ID, CASHIER_ID, permissionId);
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM expense WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM expense_category WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM shift WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM device WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM user_permissions WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
    }
}
