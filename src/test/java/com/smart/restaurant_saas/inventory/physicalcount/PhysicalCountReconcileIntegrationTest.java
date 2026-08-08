package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountLineResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PhysicalCountReconcileIntegrationTest {

    private static final Long TENANT_ID = 995_001L;
    private static final Long BRANCH_ID = 995_101L;
    private static final Long KG_ID = 995_201L;
    private static final Long BAG_ID = 995_202L;
    private static final Long CATEGORY_ID = 995_301L;
    private static final Long WAREHOUSE_ID = 995_401L;
    private static final Long KG_MATERIAL_ID = 995_501L;
    private static final Long BAG_MATERIAL_ID = 995_502L;
    private static final Long COUNT_ID = 995_601L;
    private static final Long LINE_ID = 995_701L;
    private static final Long OPENING_TX_ID = 995_801L;
    private static final Long BALANCE_ID = 995_901L;
    private static final Long BATCH_ID = 996_001L;
    private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime COUNTED_AT = FROZEN_AT.plusHours(2);

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private InventoryLedgerService ledgerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Count Variance Tenant', 'COUNT_VARIANCE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'CV-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'CV-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, KG_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, ?, 'CV-BAG', 'Five kilogram bag', 'bag', 'WEIGHT',
                    5, 5, TRUE, CURRENT_TIMESTAMP)
            """, BAG_ID, TENANT_ID, KG_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'CV-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'CV-WH-1', 'Main Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        insertMaterial(KG_MATERIAL_ID, KG_ID, "CV-FLOUR-KG", "Flour kg");
        insertMaterial(BAG_MATERIAL_ID, BAG_ID, "CV-FLOUR-BAG", "Flour bags");
    }

    @Test
    void workedExampleLeavesBalanceAtNinetyFiveAndWritesNoCountMovement() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "95", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "95", "100", "95", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "OUT", "5", KG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(balanceQuantity()).isEqualByComparingTo("95.000000");
        assertThat(countMovementCount()).isZero();
        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("95.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("0.000000");
    }

    @Test
    void movementRegisteredAfterFreezeButDatedBeforeFreezeIsIncludedWithoutVariance() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "120", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "120", "100", "100", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "IN", "20", KG_ID, "20", KG_ID,
            FROZEN_AT.minusDays(1), FROZEN_AT.plusMinutes(30));

        PhysicalCountLineResponse readLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();
        PhysicalCountLineResponse reconciledLine = service.reconcile(COUNT_ID, TENANT_ID, 77L)
            .getLines().getFirst();
        entityManager.flush();

        assertThat(readLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("120.000000");
        assertThat(readLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(reconciledLine.getAdjustedExpectedQuantity())
            .isEqualByComparingTo(readLine.getAdjustedExpectedQuantity());
        assertThat(reconciledLine.getVariance()).isEqualByComparingTo(readLine.getVariance());
        assertThat(countMovementCount()).isZero();
    }

    @Test
    void movementRegisteredAfterFreezeButDatedAtStartOfFreezeDayIsIncluded() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "120", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "120", "100", "100", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "IN", "20", KG_ID, "20", KG_ID,
            FROZEN_AT.toLocalDate().atStartOfDay(), FROZEN_AT.plusMinutes(30));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("120.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("0.000000");
        assertThat(countMovementCount()).isZero();
    }

    @Test
    void movementRegisteredBeforeFreezeButDatedAfterFreezeIsExcluded() {
        seedCount(KG_MATERIAL_ID, KG_ID, "120", "120", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "120", "100", "100", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "IN", "20", KG_ID, "20", KG_ID,
            FROZEN_AT.plusHours(1), FROZEN_AT.minusMinutes(30));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("120.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("0.000000");
        assertThat(countMovementCount()).isZero();
    }

    @Test
    void movementRegisteredAfterFreezeButDatedAfterCountIsExcluded() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "100", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "120", "100", "100", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "IN", "20", KG_ID, "20", KG_ID,
            COUNTED_AT.plusHours(1), FROZEN_AT.plusMinutes(30));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("100.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("0.000000");
        assertThat(countMovementCount()).isZero();
    }

    @Test
    void detailReadMatchesReconcileWithoutPersistingBeforeTheWrite() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "95", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "95", "100", "95", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "OUT", "5", KG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        PhysicalCountLineResponse readLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(readLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(readLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(readLine.getAdjustedExpectedQuantityProvisional()).isFalse();
        assertThat(lineValue("adjusted_expected_quantity")).isNull();

        PhysicalCountLineResponse reconciledLine = service.reconcile(COUNT_ID, TENANT_ID, 77L)
            .getLines().getFirst();
        entityManager.flush();

        assertThat(reconciledLine.getAdjustedExpectedQuantity())
            .isEqualByComparingTo(readLine.getAdjustedExpectedQuantity());
        assertThat(reconciledLine.getVariance()).isEqualByComparingTo(readLine.getVariance());
        assertThat(lineValue("adjusted_expected_quantity"))
            .isEqualByComparingTo(readLine.getAdjustedExpectedQuantity());
    }

    @Test
    void reconciledDetailKeepsStoredExpectationAfterLaterWarehouseMovement() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "95", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "95", "100", "95", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "OUT", "5", KG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));
        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();
        insertMovement(996_102L, KG_MATERIAL_ID, "OUT", "20", KG_ID, "20", KG_ID,
            COUNTED_AT.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        PhysicalCountLineResponse responseLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();

        assertThat(responseLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("95.000000");
        assertThat(responseLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(responseLine.getAdjustedExpectedQuantityProvisional()).isFalse();
    }

    @Test
    void shortageAfterLegitimateSalePostsTwoAndLeavesBalanceAtNinetyThree() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "93", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "95", "100", "95", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "OUT", "5", KG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(balanceQuantity()).isEqualByComparingTo("93.000000");
        assertThat(batchRemaining()).isEqualByComparingTo("93.000000");
        assertThat(countMovementCount()).isEqualTo(1);
        assertThat(countMovementValue("stock_quantity")).isEqualByComparingTo("2.000000");
        assertThat(countMovementText("direction")).isEqualTo("OUT");
        assertThat(countMovementTimestamp("movement_date")).isEqualTo(COUNTED_AT);
        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("95.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("-2.000000");
    }

    @Test
    void movementAfterCountIsIgnoredAndCorrectionAppliesOnTopOfCurrentBalance() {
        seedCount(KG_MATERIAL_ID, KG_ID, "100", "93", COUNTED_AT);
        seedStock(KG_MATERIAL_ID, KG_ID, "85", "100", "85", "5");
        insertMovement(996_101L, KG_MATERIAL_ID, "OUT", "5", KG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));
        insertMovement(996_102L, KG_MATERIAL_ID, "OUT", "10", KG_ID, "10", KG_ID,
            COUNTED_AT.plusHours(1));

        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(lineValue("variance")).isEqualByComparingTo("-2.000000");
        assertThat(countMovementValue("stock_quantity")).isEqualByComparingTo("2.000000");
        assertThat(balanceQuantity()).isEqualByComparingTo("83.000000");
        assertThat(batchRemaining()).isEqualByComparingTo("83.000000");
    }

    @Test
    void enteredBagMovementNetsInDisplayBagsAfterOneStockUomConversion() {
        seedCount(BAG_MATERIAL_ID, BAG_ID, "20", "19", COUNTED_AT);
        seedStock(BAG_MATERIAL_ID, BAG_ID, "19", "20", "19", "5");
        insertMovement(996_101L, BAG_MATERIAL_ID, "OUT", "1", BAG_ID, "5", KG_ID,
            FROZEN_AT.plusHours(1));

        PhysicalCountLineResponse readLine = service.findById(COUNT_ID, TENANT_ID)
            .getLines().getFirst();
        service.reconcile(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(readLine.getUomId()).isEqualTo(BAG_ID);
        assertThat(readLine.getAdjustedExpectedQuantity()).isEqualByComparingTo("19.000000");
        assertThat(readLine.getVariance()).isEqualByComparingTo("0.000000");
        assertThat(lineValue("adjusted_expected_quantity")).isEqualByComparingTo("19.000000");
        assertThat(lineValue("variance")).isEqualByComparingTo("0.000000");
        assertThat(balanceQuantity()).isEqualByComparingTo("19.000000");
        assertThat(countMovementCount()).isZero();
    }

    @Test
    void countFreezeCopiesTheDisplayUomBalanceProducedByTheLedger() {
        jdbcTemplate.update("""
            INSERT INTO physical_count (
                id, tenant_id, warehouse_id, code, status, scheduled_date,
                has_large_variance, created_at)
            VALUES (?, ?, ?, 'PC-CV-1', 'DRAFT', DATE '2026-07-01', FALSE, CURRENT_TIMESTAMP)
            """, COUNT_ID, TENANT_ID, WAREHOUSE_ID);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (
                id, tenant_id, physical_count_id, material_id, uom_id,
                expected_quantity, unit_cost_at_freeze, action_taken, created_at)
            VALUES (?, ?, ?, ?, ?, 0, 0, 'PENDING', CURRENT_TIMESTAMP)
            """, LINE_ID, TENANT_ID, COUNT_ID, BAG_MATERIAL_ID, BAG_ID);

        var transaction = ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(BAG_MATERIAL_ID)
            .transactionType(InventoryTransactionType.OPENING_BALANCE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(new BigDecimal("5.000000"))
            .enteredUomId(KG_ID)
            .enteredUnitCost(new BigDecimal("2.000000"))
            .movementDate(FROZEN_AT.minusHours(1))
            .createdBy(77L)
            .build());
        entityManager.flush();

        assertThat(transaction.getStockQuantity()).isEqualByComparingTo("5.000000");
        assertThat(transaction.getStockUom().getId()).isEqualTo(KG_ID);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM stock_balance
            WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, BAG_MATERIAL_ID))
            .isEqualByComparingTo("1.000000");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT uom_id
            FROM stock_balance
            WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, Long.class, TENANT_ID, WAREHOUSE_ID, BAG_MATERIAL_ID))
            .isEqualTo(BAG_ID);

        service.start(COUNT_ID, TENANT_ID, 77L);
        entityManager.flush();

        assertThat(lineValue("expected_quantity")).isEqualByComparingTo("1.000000");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT uom_id FROM physical_count_line WHERE id = ?",
            Long.class, LINE_ID)).isEqualTo(BAG_ID);
    }

    private void insertMaterial(Long materialId, Long displayUomId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, materialId, TENANT_ID, CATEGORY_ID, KG_ID, displayUomId, code, name);
    }

    private void seedCount(Long materialId, Long lineUomId, String expectedQuantity,
                           String countedQuantity, LocalDateTime countedAt) {
        jdbcTemplate.update("""
            INSERT INTO physical_count (id, tenant_id, warehouse_id, code, status, scheduled_date,
                                        started_at, frozen_at, has_large_variance, created_at)
            VALUES (?, ?, ?, 'PC-CV-1', 'IN_PROGRESS', DATE '2026-07-01',
                    ?, ?, FALSE, CURRENT_TIMESTAMP)
            """, COUNT_ID, TENANT_ID, WAREHOUSE_ID, FROZEN_AT, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, counted_quantity, unit_cost_at_freeze,
                                             counted_at, action_taken, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), 5, ?,
                    'PENDING', CURRENT_TIMESTAMP)
            """, LINE_ID, TENANT_ID, COUNT_ID, materialId, lineUomId,
            expectedQuantity, countedQuantity, countedAt);
    }

    private void seedStock(Long materialId, Long balanceUomId, String currentQuantity,
                           String originalBatchQuantity, String remainingBatchQuantity,
                           String displayUnitCost) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id,
                unit_cost, total_cost, transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'OPENING_BALANCE', 'IN', 100, ?, 100, ?, 1, 100,
                    ?, ?, ?)
            """, OPENING_TX_ID, TENANT_ID, WAREHOUSE_ID, materialId, KG_ID, KG_ID,
            FROZEN_AT.minusDays(1), FROZEN_AT.minusDays(1), FROZEN_AT.minusDays(1));
        jdbcTemplate.update("""
            INSERT INTO stock_balance (
                id, tenant_id, warehouse_id, material_id, quantity, uom_id, average_cost,
                minimum_quantity, version, opening_quantity, created_at)
            VALUES (?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), 0, 0,
                    CAST(? AS numeric), CURRENT_TIMESTAMP)
            """, BALANCE_ID, TENANT_ID, WAREHOUSE_ID, materialId, currentQuantity, balanceUomId,
            displayUnitCost, originalBatchQuantity);
        jdbcTemplate.update("""
            INSERT INTO stock_batch (
                id, tenant_id, stock_balance_id, original_quantity, remaining_quantity,
                unit_cost, movement_date, source_transaction_id, status, created_at)
            VALUES (?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), CAST(? AS numeric),
                    ?, ?, 'OPEN', CURRENT_TIMESTAMP)
            """, BATCH_ID, TENANT_ID, BALANCE_ID, originalBatchQuantity, remainingBatchQuantity,
            displayUnitCost, FROZEN_AT.minusDays(1), OPENING_TX_ID);
    }

    private void insertMovement(Long id, Long materialId, String direction,
                                String enteredQuantity, Long enteredUomId,
                                String stockQuantity, Long stockUomId,
                                LocalDateTime movementDate) {
        insertMovement(id, materialId, direction, enteredQuantity, enteredUomId,
            stockQuantity, stockUomId, movementDate, LocalDateTime.now());
    }

    private void insertMovement(Long id, Long materialId, String direction,
                                String enteredQuantity, Long enteredUomId,
                                String stockQuantity, Long stockUomId,
                                LocalDateTime movementDate, LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id,
                transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'MANUAL_CONSUMPTION', ?, CAST(? AS numeric), ?,
                    CAST(? AS numeric), ?, ?, ?, ?)
            """, id, TENANT_ID, WAREHOUSE_ID, materialId, direction, enteredQuantity,
            enteredUomId, stockQuantity, stockUomId, createdAt, movementDate, createdAt);
    }

    private BigDecimal balanceQuantity() {
        return jdbcTemplate.queryForObject(
            "SELECT quantity FROM stock_balance WHERE id = ?", BigDecimal.class, BALANCE_ID);
    }

    private BigDecimal batchRemaining() {
        return jdbcTemplate.queryForObject(
            "SELECT remaining_quantity FROM stock_batch WHERE id = ?", BigDecimal.class, BATCH_ID);
    }

    private Integer countMovementCount() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'PHYSICAL_COUNT' AND reference_id = ?
            """, Integer.class, TENANT_ID, COUNT_ID);
    }

    private BigDecimal countMovementValue(String column) {
        return jdbcTemplate.queryForObject("""
            SELECT %s FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'PHYSICAL_COUNT' AND reference_id = ?
            """.formatted(column), BigDecimal.class, TENANT_ID, COUNT_ID);
    }

    private String countMovementText(String column) {
        return jdbcTemplate.queryForObject("""
            SELECT %s FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'PHYSICAL_COUNT' AND reference_id = ?
            """.formatted(column), String.class, TENANT_ID, COUNT_ID);
    }

    private LocalDateTime countMovementTimestamp(String column) {
        return jdbcTemplate.queryForObject("""
            SELECT %s FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'PHYSICAL_COUNT' AND reference_id = ?
            """.formatted(column), LocalDateTime.class, TENANT_ID, COUNT_ID);
    }

    private BigDecimal lineValue(String column) {
        return jdbcTemplate.queryForObject(
            "SELECT %s FROM physical_count_line WHERE id = ?".formatted(column),
            BigDecimal.class, LINE_ID);
    }
}
