package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The basis of {@code stock_balance.average_cost}: value-weighted over the <b>remaining</b>
 * quantity of <b>OPEN</b> batches, and nothing else.
 *
 * <p>Written for ledger L006. The formula lives in {@code StockBatchRepository.sumOpenBatchTotals}
 * as JPQL, and the guard-reversion audit found it uncovered: the sums could be switched from
 * {@code remainingQuantity} to {@code originalQuantity} — turning the figure into an average over
 * every purchase ever made, including stock long since consumed — and the whole suite stayed green.
 * {@code StockBalanceServiceTest} could not catch it because it mocks the repository, so the query
 * itself was never executed against a database by any assertion.
 *
 * <p>The distinction is not academic. Average cost is the valuation basis for FIFO shortfall
 * remainders, for physical-count surplus batches, and for the {@code unitCostAtFreeze} a count
 * stamps — so a wrong basis silently misprices stock on every one of those paths. It also only
 * shows up once stock has been consumed: before any consumption the two formulas agree exactly,
 * which is why a test has to consume before asserting.
 */
@SpringBootTest
@Transactional
class StockBalanceAverageCostIntegrationTest {

    private static final Long TENANT_ID = 986_001L;
    private static final Long BRANCH_ID = 986_101L;
    private static final Long UOM_ID = 986_201L;
    private static final Long CATEGORY_ID = 986_301L;
    private static final Long WAREHOUSE_ID = 986_401L;
    private static final Long MATERIAL_ID = 986_501L;
    private static final Long USER_ID = 986_601L;

    private static final LocalDateTime CHEAP_BATCH_DATE = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime DEAR_BATCH_DATE = LocalDateTime.of(2026, 3, 8, 0, 0);

    @Autowired private InventoryLedgerService ledgerService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Avg Cost Tenant', 'AVG_COST', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Avg Branch', 'AVG-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             entered_factor, active, created_at)
            VALUES (?, ?, 'AVG-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'AVG-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'AVG-WH-1', 'Avg Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'AVG-FLOUR', 'Flour', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);
    }

    /**
     * Ten at 2 then ten at 8 average to 5. Consume ten: FIFO empties the cheap batch, so the only
     * stock left is the dear one and the average must be 8 — not 5.
     *
     * <p>5 is the answer an average over original quantities gives, because the consumed batch
     * keeps its weight forever. That is the exact difference this test exists to catch.
     */
    @Test
    void averageIgnoresBatchesFifoHasAlreadyConsumed() {
        purchase("10.000000", "2.000000", CHEAP_BATCH_DATE);
        purchase("10.000000", "8.000000", DEAR_BATCH_DATE);

        assertThat(storedAverageCost()).isEqualByComparingTo("5.000000");

        consume("10.000000");

        assertThat(storedAverageCost()).isEqualByComparingTo("8.000000");
        assertThat(storedQuantity()).isEqualByComparingTo("10.000000");
        assertThat(openBatchCount()).isEqualTo(1);
    }

    /**
     * A partially consumed batch is weighted by what is left of it, not by what arrived. Eating 5
     * of the 10 cheap units leaves 5 at 2 and 10 at 8: (10 + 80) / 15 = 6.
     */
    @Test
    void averageWeightsAPartiallyConsumedBatchByItsRemainder() {
        purchase("10.000000", "2.000000", CHEAP_BATCH_DATE);
        purchase("10.000000", "8.000000", DEAR_BATCH_DATE);

        consume("5.000000");

        assertThat(storedAverageCost()).isEqualByComparingTo("6.000000");
        assertThat(openBatchCount()).isEqualTo(2);
    }

    /**
     * Zero-stock carry-forward: with every batch consumed there is nothing to derive an average
     * from, and the last known value is kept rather than reset to zero — a stored zero would value
     * returning stock at nothing (see {@code recalculateFromOpenBatches}).
     *
     * <p>"Last known" means the average as it stood <em>before</em> the emptying movement, not the
     * cost of whichever batch emptied last. Both batches go in one movement here, so the value
     * carried forward is the 5 that held while both were open — the recalculation simply declines
     * to run and leaves the column untouched. Worth pinning explicitly: 8 is the intuitive guess
     * and it is wrong.
     */
    @Test
    void averageIsCarriedForwardWhenNoOpenBatchRemains() {
        purchase("10.000000", "2.000000", CHEAP_BATCH_DATE);
        purchase("10.000000", "8.000000", DEAR_BATCH_DATE);

        assertThat(storedAverageCost()).isEqualByComparingTo("5.000000");

        consume("20.000000");

        assertThat(openBatchCount()).isZero();
        assertThat(storedQuantity()).isEqualByComparingTo("0.000000");
        assertThat(storedAverageCost()).isEqualByComparingTo("5.000000");
    }

    // ------------------------------------------------------------------ helpers

    private void purchase(String quantity, String unitCost, LocalDateTime movementDate) {
        ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(MATERIAL_ID)
            .transactionType(InventoryTransactionType.PURCHASE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(new BigDecimal(quantity))
            .enteredUomId(UOM_ID)
            .enteredUnitCost(new BigDecimal(unitCost))
            .movementDate(movementDate)
            .createdBy(USER_ID)
            .build());
        entityManager.flush();
    }

    private void consume(String quantity) {
        ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(MATERIAL_ID)
            .transactionType(InventoryTransactionType.MANUAL_CONSUMPTION)
            .direction(InventoryTransactionDirection.OUT)
            .enteredQuantity(new BigDecimal(quantity))
            .enteredUomId(UOM_ID)
            .movementDate(DEAR_BATCH_DATE.plusDays(1))
            .createdBy(USER_ID)
            .build());
        entityManager.flush();
    }

    private BigDecimal storedAverageCost() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
            SELECT average_cost FROM stock_balance
             WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
    }

    private BigDecimal storedQuantity() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
            SELECT quantity FROM stock_balance
             WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
    }

    private int openBatchCount() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
            SELECT count(*) FROM stock_batch b
              JOIN stock_balance sb ON sb.id = b.stock_balance_id
             WHERE sb.tenant_id = ? AND sb.material_id = ?
               AND b.status = 'OPEN' AND b.remaining_quantity > 0
            """, Integer.class, TENANT_ID, MATERIAL_ID);
    }
}
