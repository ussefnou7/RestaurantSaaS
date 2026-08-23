package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pins the eviction ordering for the UOM lookup version (D111).
 *
 * <p>Deliberately not {@code @Transactional}: the whole point is that the transaction commits, so
 * the after-commit hook actually fires. A rolled-back test would pass against either ordering.
 */
@SpringBootTest
class UomLookupVersionEvictionIntegrationTest {

    private static final Long TENANT_ID = 977_001L;
    private static final Long SEED_UOM_ID = 977_101L;
    private static final Long ADDED_UOM_ID = 977_102L;

    @Autowired
    private UomLookupVersionService versionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService otherConnection;

    @BeforeEach
    void setUp() {
        cleanUp();
        otherConnection = Executors.newSingleThreadExecutor();
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'UOM Eviction Tenant', 'UOM_EVICT_TENANT', 'ACTIVE', CURRENT_TIMESTAMP,
                    'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, name_ar, symbol, symbol_ar,
                             type, factor_to_base, entered_factor, active, created_at, updated_at)
            VALUES (?, ?, NULL, 'EVICT_SEED', 'Evict Seed', 'بذرة', 'es', 'ب-ذ', 'COUNT',
                    1, 1, TRUE, TIMESTAMP '2026-01-01 08:00:00', NULL)
            """, SEED_UOM_ID, TENANT_ID);
        versionService.evictTenant(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        if (otherConnection != null) {
            otherConnection.shutdownNow();
        }
        cleanUp();
        versionService.evictTenant(TENANT_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM uom WHERE id IN (?, ?)", SEED_UOM_ID, ADDED_UOM_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
    }

    /**
     * A read on another connection, landing between the eviction and the commit, must not leave the
     * pre-commit version cached forever.
     *
     * <p>Against eviction-before-commit only, the concurrent read re-caches the old version and
     * nothing evicts it afterwards, so the assertion at the end fails and the lookup would go on
     * serving stale rows and stale {@code 304}s indefinitely.
     */
    @Test
    void concurrentReadDuringWriteDoesNotLeaveStaleVersionCached() throws Exception {
        String versionBefore = versionService.versionForTenant(TENANT_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("""
                INSERT INTO uom (id, tenant_id, base_uom_id, code, name, name_ar, symbol, symbol_ar,
                                 type, factor_to_base, entered_factor, active, created_at, updated_at)
                VALUES (?, ?, NULL, 'EVICT_ADDED', 'Evict Added', 'مضاف', 'ea', 'م-ض', 'COUNT',
                        1, 1, TRUE, TIMESTAMP '2026-02-01 08:00:00', NULL)
                """, ADDED_UOM_ID, TENANT_ID);

            versionService.evictTenantAfterCommit(TENANT_ID);

            // Another connection reads while the insert is still uncommitted. It sees the old rows
            // and re-populates the cache with the pre-commit version.
            String seenByOther = onOtherConnection(() -> versionService.versionForTenant(TENANT_ID));
            assertThat(seenByOther)
                .as("the concurrent reader should not see the uncommitted row")
                .isEqualTo(versionBefore);
        });

        assertThat(versionService.versionForTenant(TENANT_ID))
            .as("after commit the cached version must reflect the committed row")
            .isNotEqualTo(versionBefore);
    }

    private <T> T onOtherConnection(Callable<T> work) {
        Future<T> future = otherConnection.submit(work);
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException("concurrent read failed", ex);
        }
    }
}
