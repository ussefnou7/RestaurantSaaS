package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.batch.dto.StockBatchResponse;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StockBatchOrderingIntegrationTest {

    private static final Long TENANT_ID = 997_001L;
    private static final Long BRANCH_ID = 997_101L;
    private static final Long UOM_ID = 997_201L;
    private static final Long CATEGORY_ID = 997_301L;
    private static final Long WAREHOUSE_ID = 997_401L;
    private static final Long MATERIAL_ID = 997_501L;
    private static final Long USER_ID = 997_601L;
    private static final LocalDateTime JANUARY_25 = LocalDateTime.of(2026, 1, 25, 0, 0);
    private static final LocalDateTime JANUARY_31 = LocalDateTime.of(2026, 1, 31, 0, 0);

    @Autowired
    private InventoryLedgerService ledgerService;

    @Autowired
    private StockBalanceService stockBalanceService;

    @Autowired
    private StockBatchRepository stockBatchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, 'Batch Ordering Tenant', 'BATCH_ORDERING', 'ACTIVE', CURRENT_TIMESTAMP)
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Batch Branch', 'BATCH-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'BATCH-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'BATCH-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'BATCH-WH-1', 'Batch Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'BATCH-FLOUR', 'Flour', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);
    }

    @Test
    void consumesOlderMovementDateBeforeLowerId() {
        StockBatch lowerIdNewerBatch = openBatch("5.000000", JANUARY_31);
        StockBatch higherIdOlderBatch = openBatch("5.000000", JANUARY_25);

        assertThat(lowerIdNewerBatch.getId()).isLessThan(higherIdOlderBatch.getId());

        consume("3.000000");

        assertThat(batchRemaining(higherIdOlderBatch.getId())).isEqualByComparingTo("2.000000");
        assertThat(batchRemaining(lowerIdNewerBatch.getId())).isEqualByComparingTo("5.000000");
    }

    @Test
    void sameMovementDateUsesIdAsDeterministicTiebreaker() {
        StockBatch firstBatch = openBatch("4.000000", JANUARY_25);
        StockBatch secondBatch = openBatch("4.000000", JANUARY_25);

        consume("4.000000");

        assertThat(batchRemaining(firstBatch.getId())).isEqualByComparingTo("0.000000");
        assertThat(batchRemaining(secondBatch.getId())).isEqualByComparingTo("4.000000");
    }

    @Test
    void retroactiveBatchLeadsOnlyFutureConsumption() {
        StockBatch newerBatch = openBatch("10.000000", JANUARY_31);

        consume("4.000000");
        assertThat(batchRemaining(newerBatch.getId())).isEqualByComparingTo("6.000000");

        StockBatch retroactiveBatch = openBatch("5.000000", JANUARY_25);
        consume("3.000000");

        assertThat(batchRemaining(retroactiveBatch.getId())).isEqualByComparingTo("2.000000");
        assertThat(batchRemaining(newerBatch.getId())).isEqualByComparingTo("6.000000");
    }

    @Test
    void batchListingUsesSameOrderAsConsumption() {
        StockBatch lowerIdNewerBatch = openBatch("5.000000", JANUARY_31);
        StockBatch higherIdOlderBatch = openBatch("5.000000", JANUARY_25);
        Long balanceId = higherIdOlderBatch.getStockBalance().getId();

        entityManager.flush();
        entityManager.clear();

        List<Long> listedBatchIds = stockBalanceService
            .findBatchesForBalance(balanceId, TENANT_ID)
            .stream()
            .map(StockBatchResponse::getId)
            .toList();

        assertThat(listedBatchIds)
            .containsExactly(higherIdOlderBatch.getId(), lowerIdNewerBatch.getId());
    }

    private StockBatch openBatch(String quantity, LocalDateTime movementDate) {
        InventoryTransaction transaction = ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(MATERIAL_ID)
            .transactionType(InventoryTransactionType.PURCHASE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(new BigDecimal(quantity))
            .enteredUomId(UOM_ID)
            .enteredUnitCost(new BigDecimal("2.000000"))
            .movementDate(movementDate)
            .createdBy(USER_ID)
            .build());
        entityManager.flush();
        return stockBatchRepository
            .findByTenantIdAndSourceTransactionId(TENANT_ID, transaction.getId())
            .orElseThrow();
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
            .movementDate(JANUARY_31.plusDays(1))
            .createdBy(USER_ID)
            .build());
        entityManager.flush();
    }

    private BigDecimal batchRemaining(Long batchId) {
        return jdbcTemplate.queryForObject(
            "SELECT remaining_quantity FROM stock_batch WHERE id = ?",
            BigDecimal.class, batchId);
    }
}
