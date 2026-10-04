package com.smart.restaurant_saas.pos.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * Ledger L007 — competing shift closes. The first successful close must be immutable.
 *
 * <p><b>Why this test exists and the E2E attempt did not settle it.</b> Two closes fired as
 * parallel {@code curl} requests produced the right answer (one 200, one 409, the first cashier's
 * count kept), but a pass there is weak evidence: nothing shows the two requests ever overlapped
 * inside the window between {@code ShiftService} reading the shift and writing {@code closedAt}. If
 * they ran back to back the lock was never exercised and the 409 came from committed state, which
 * an unlocked read would also have produced. Only a failure would have been conclusive.
 *
 * <p><b>And the mock tests do not cover it either.</b> {@code ShiftServiceTest} stubs
 * {@code findByIdAndTenantIdForUpdate}, so swapping that call for the unlocked
 * {@code findByIdAndTenantId} makes those tests go red — but they go red because a Mockito stub
 * stopped matching, not because anything about serialisation was asserted. The guard-reversion
 * audit (L006) recorded that as coverage for an unrelated reason.
 *
 * <p><b>What makes this deterministic.</b> The first close is impersonated by a transaction that
 * writes the shift row and then <em>stops</em>, holding its row lock on a latch. The overlap is
 * therefore constructed, not hoped for: the second close provably runs while the first is
 * in-flight and uncommitted. {@link #secondCloseBlocksWhileTheFirstHoldsTheShiftRowLock} asserts
 * the second request is still blocked after 300 ms, which is what "it reached the critical window
 * and waited" looks like from outside.
 *
 * <p>The load-bearing assertion is the stored {@code closing_count}, not the status code. With the
 * row lock removed the second close reads the stale OPEN row, sails past the already-closed check,
 * and overwrites the first cashier's counted figure — so the count assertion fails even in the
 * variant where the second request still blocks later, at flush time, on the same row.
 *
 * <p>No {@code @Transactional}: a test transaction would serialise both threads onto one
 * connection and the race could not occur at all. State is asserted through {@link JdbcTemplate}
 * against committed rows, and torn down by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShiftCloseConcurrencyIntegrationTest {

    private static final long BASE = 975_000L;
    private static final long TENANT_ID = BASE;
    private static final long BRANCH_ID = BASE + 1;
    private static final long DEVICE_ID = BASE + 2;
    private static final long CASHIER_ID = BASE + 3;
    private static final long SHIFT_ID = BASE + 4;

    /** The figure the first close commits. The second close must not be able to replace it. */
    private static final String FIRST_COUNT = "777.000000";
    /** The figure the second, losing close offers. It must never reach the row. */
    private static final String SECOND_COUNT = "999";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private PlatformTransactionManager transactionManager;

    private String cashierToken;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        cleanUp();
        seed();
        cashierToken = jwtService.generateAccessToken(
            CASHIER_ID, TENANT_ID, "cashier_race", RoleCode.OWNER.name(), DEVICE_ID);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        cleanUp();
    }

    @Test
    void secondCloseBlocksWhileTheFirstHoldsTheShiftRowLock() throws Exception {
        CountDownLatch firstCloseHasTheRow = new CountDownLatch(1);
        CountDownLatch releaseFirstClose = new CountDownLatch(1);

        Future<?> firstClose = executor.submit(() -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> {
                commitFirstCloseFigures();
                firstCloseHasTheRow.countDown();
                await(releaseFirstClose);
            }));

        assertThat(firstCloseHasTheRow.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Integer> secondCloseStatus = executor.submit(() -> closeShift(SECOND_COUNT));

        // The proof of overlap. The second close is inside closeShift and has not returned,
        // because it is waiting on the row the first close holds. Without the row lock at
        // ShiftService.java:163 it would read the uncommitted-away OPEN row and carry on.
        assertThatThrownBy(() -> secondCloseStatus.get(300, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);

        releaseFirstClose.countDown();
        firstClose.get(5, TimeUnit.SECONDS);

        assertThat(secondCloseStatus.get(5, TimeUnit.SECONDS)).isEqualTo(409);
        // The real invariant: the first cashier's counted cash survived.
        assertThat(storedClosingCount()).isEqualByComparingTo(FIRST_COUNT);
        assertThat(storedStatus()).isEqualTo("CLOSED");
        assertThat(closedByUserId()).isEqualTo(CASHIER_ID);
    }

    /**
     * The same race with the loser arriving after the winner has committed. Sequential by
     * construction, so it proves less than the test above — it is here because it is the case the
     * already-closed check at {@code ShiftService.java:174} decides on its own, with no
     * contention, and a regression that broke only this path would otherwise go unnoticed.
     */
    @Test
    void aCloseArrivingAfterACommittedCloseIsRejectedAndChangesNothing() throws Exception {
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> commitFirstCloseFigures());

        assertThat(closeShift(SECOND_COUNT)).isEqualTo(409);
        assertThat(storedClosingCount()).isEqualByComparingTo(FIRST_COUNT);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * What a winning {@code closeShift} leaves behind, written as one statement so the row lock is
     * taken and held for the rest of the caller's transaction. Every close column is set together:
     * {@code chk_shift_close_fields} rejects a partial close, and {@code chk_shift_forced_close}
     * rejects {@code forced_close} unless the closer differs from the opener — the cashier closes
     * their own shift here, so it stays false.
     */
    private void commitFirstCloseFigures() {
        jdbcTemplate.update("""
            UPDATE shift
               SET status = 'CLOSED',
                   closing_count = ?,
                   expected_cash = 100,
                   variance = ? - 100,
                   expenses_at_close = 0,
                   closed_by_user_id = ?,
                   forced_close = FALSE,
                   closed_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = ?
            """, new BigDecimal(FIRST_COUNT), new BigDecimal(FIRST_COUNT), CASHIER_ID, SHIFT_ID);
    }

    private int closeShift(String closingCount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shifts/" + SHIFT_ID + "/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"closingCount\":" + closingCount + "}"))
            .andReturn();
        return result.getResponse().getStatus();
    }

    private BigDecimal storedClosingCount() {
        return jdbcTemplate.queryForObject(
            "SELECT closing_count FROM shift WHERE id = ?", BigDecimal.class, SHIFT_ID);
    }

    private String storedStatus() {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM shift WHERE id = ?", String.class, SHIFT_ID);
    }

    private Long closedByUserId() {
        return jdbcTemplate.queryForObject(
            "SELECT closed_by_user_id FROM shift WHERE id = ?", Long.class, SHIFT_ID);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out holding the shift row lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the shift row lock", ex);
        }
    }

    private void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Shift Race', 'SHIFT_RACE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
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

        Long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND tenant_id IS NULL",
            Long.class, RoleCode.OWNER.name());
        jdbcTemplate.update("""
            INSERT INTO users (id, tenant_id, full_name, username, password_hash, status,
                               created_at, role_id)
            VALUES (?, ?, 'Race Cashier', 'cashier_race', 'x', 'ACTIVE', CURRENT_TIMESTAMP, ?)
            """, CASHIER_ID, TENANT_ID, roleId);
        grant("SHIFTS_CLOSE", "SHIFTS_VIEW");

        jdbcTemplate.update("""
            INSERT INTO shift (id, tenant_id, device_id, business_date, opened_by_user_id,
                               forced_close, opening_count, status, opened_at, created_at)
            VALUES (?, ?, ?, CURRENT_DATE, ?, FALSE, 100, 'OPEN',
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

    /** Children first; nothing here runs in a rolled-back transaction. */
    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM expense WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM shift WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM device WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM user_permissions WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
    }
}
