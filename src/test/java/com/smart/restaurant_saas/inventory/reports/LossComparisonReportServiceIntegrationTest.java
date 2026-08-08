package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.reports.dto.LossComparisonRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The comparison folds two differently-signed aggregates out of one LEFT JOIN and partitions
 * zero-activity rows to the end — all of it in JPQL, so it is verified against real Postgres.
 * Seeded ids are in a dedicated high range and the test is transactional.
 */
@SpringBootTest
@Transactional
class LossComparisonReportServiceIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long OTHER_TENANT_ID = 996_002L;
    private static final Long BRANCH_ID = 996_101L;

    private static final Long UOM_KG_ID = 996_201L;
    private static final Long UOM_G_ID = 996_202L;

    private static final Long CATEGORY_ID = 996_301L;
    private static final Long OTHER_CATEGORY_ID = 996_302L;

    private static final Long WAREHOUSE_ID = 996_401L;
    private static final Long SECOND_WAREHOUSE_ID = 996_402L;

    /** Waste only. */
    private static final Long LETTUCE_ID = 996_501L;
    /** Shrinkage only. */
    private static final Long SAFFRON_ID = 996_502L;
    /** Both. */
    private static final Long CHICKEN_ID = 996_503L;
    /** Neither — the "came back clean" case. */
    private static final Long SALT_ID = 996_504L;
    /** Stock UOM grams, display UOM kilograms. */
    private static final Long RICE_ID = 996_505L;

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 3, 31);

    private long nextTransactionId = 996_900L;

    @Autowired
    private LossComparisonReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "Comparison Tenant", "LOSS_COMPARISON");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "LOSS_COMPARISON_OTHER");

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'LCR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        insertUom(UOM_KG_ID, null, "LCR-KG", "Kilogram", "kg", "1");
        insertUom(UOM_G_ID, UOM_KG_ID, "LCR-G", "Gram", "g", "0.001");

        insertCategory(CATEGORY_ID, "LCR-FRESH", "Fresh");
        insertCategory(OTHER_CATEGORY_ID, "LCR-DRY", "Dry");

        insertWarehouse(WAREHOUSE_ID, TENANT_ID, "LCR-WH-1", "Main Warehouse");
        insertWarehouse(SECOND_WAREHOUSE_ID, TENANT_ID, "LCR-WH-2", "Second Warehouse");

        insertMaterial(LETTUCE_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "LCR-M-1", "Lettuce");
        insertMaterial(SAFFRON_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "LCR-M-2", "Saffron");
        insertMaterial(CHICKEN_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "LCR-M-3", "Chicken");
        insertMaterial(SALT_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "LCR-M-4", "Salt");
        insertMaterial(RICE_ID, TENANT_ID, OTHER_CATEGORY_ID, UOM_G_ID, UOM_KG_ID, "LCR-M-5", "Rice");
    }

    // =========================================================================
    // The four quadrants
    // =========================================================================

    @Test
    void reportsWasteOnlyShrinkageOnlyBothAndNeither() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");
        countShortage(SAFFRON_ID, WAREHOUSE_ID, "4", "400.00", "2026-03-06");
        waste(CHICKEN_ID, WAREHOUSE_ID, "6", "120.00", "2026-03-07");
        countShortage(CHICKEN_ID, WAREHOUSE_ID, "3", "60.00", "2026-03-08");
        // SALT gets nothing at all.

        List<LossComparisonRow> rows = report();

        LossComparisonRow wasteOnly = rowFor(rows, LETTUCE_ID);
        assertThat(wasteOnly.getWasteValue()).isEqualTo("100.000000");
        assertThat(wasteOnly.getShrinkageValue()).isEqualTo("0.000000");

        LossComparisonRow shrinkageOnly = rowFor(rows, SAFFRON_ID);
        assertThat(shrinkageOnly.getWasteValue()).isEqualTo("0.000000");
        assertThat(shrinkageOnly.getShrinkageValue()).isEqualTo("-400.000000");

        LossComparisonRow both = rowFor(rows, CHICKEN_ID);
        assertThat(both.getWasteValue()).isEqualTo("120.000000");
        assertThat(both.getShrinkageValue()).isEqualTo("-60.000000");

        LossComparisonRow neither = rowFor(rows, SALT_ID);
        assertThat(neither.getWasteValue()).isEqualTo("0.000000");
        assertThat(neither.getShrinkageValue()).isEqualTo("0.000000");
        assertThat(neither.getTotalValue()).isEqualTo("0.000000");
    }

    @Test
    void wasteIsAPositiveMagnitudeAndShrinkageKeepsItsSignInTheSameRowSet() {
        // Waste is always an outflow: a minus on every row would add nothing.
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");
        // A shortage is negative...
        countShortage(SAFFRON_ID, WAREHOUSE_ID, "4", "400.00", "2026-03-06");
        // ...and a surplus is positive, because it reveals a wrong recipe or a rushed count.
        countSurplus(CHICKEN_ID, WAREHOUSE_ID, "2", "250.00", "2026-03-07");

        List<LossComparisonRow> rows = report();

        assertThat(rowFor(rows, LETTUCE_ID).getWasteQuantity()).isEqualTo("10.000000");
        assertThat(rowFor(rows, LETTUCE_ID).getWasteValue()).isEqualTo("100.000000");
        assertThat(rowFor(rows, SAFFRON_ID).getShrinkageQuantity()).isEqualTo("-4.000000");
        assertThat(rowFor(rows, SAFFRON_ID).getShrinkageValue()).isEqualTo("-400.000000");
        assertThat(rowFor(rows, CHICKEN_ID).getShrinkageQuantity()).isEqualTo("2.000000");
        assertThat(rowFor(rows, CHICKEN_ID).getShrinkageValue()).isEqualTo("250.000000");
    }

    @Test
    void totalValueIsLossPositiveSoASurplusReducesIt() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "6", "120.00", "2026-03-07");
        countSurplus(CHICKEN_ID, WAREHOUSE_ID, "2", "50.00", "2026-03-08");

        LossComparisonRow row = rowFor(report(), CHICKEN_ID);

        // 120 wasted, but 50 of surplus turned up: the net damage is 70, not 170.
        assertThat(row.getTotalValue()).isEqualTo("70.000000");
    }

    @Test
    void totalValueGoesNegativeWhenTheSurplusExceedsTheWaste() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "1", "20.00", "2026-03-07");
        countSurplus(CHICKEN_ID, WAREHOUSE_ID, "5", "300.00", "2026-03-08");

        assertThat(rowFor(report(), CHICKEN_ID).getTotalValue()).isEqualTo("-280.000000");
    }

    // =========================================================================
    // Zero rows sort last
    // =========================================================================

    @Test
    void zeroActivityRowsSortAfterEveryRowWithActivity() {
        // A plain ORDER BY ABS(totalValue) would drop the clean rows into the middle, between the
        // negative totals and the positive ones — the least readable place they could land.
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");
        countSurplus(CHICKEN_ID, WAREHOUSE_ID, "5", "300.00", "2026-03-08");

        List<LossComparisonRow> rows = report();
        List<Long> order = rows.stream().map(LossComparisonRow::getMaterialId).toList();

        // CHICKEN totals -300 (surplus), LETTUCE +100; by absolute value CHICKEN leads.
        assertThat(order.subList(0, 2)).containsExactly(CHICKEN_ID, LETTUCE_ID);
        // Everything after is a clean material.
        assertThat(order.subList(2, order.size()))
            .containsExactlyInAnyOrder(SAFFRON_ID, SALT_ID, RICE_ID);
        assertThat(rows.subList(2, rows.size()))
            .allSatisfy(row -> assertThat(row.getTotalValue()).isEqualTo("0.000000"));
    }

    @Test
    void aCleanMaterialIsReportedRatherThanOmitted() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");

        // Filtering by category is how a user asks "how did Fresh do?" — Salt coming back clean is
        // the answer, not an omission.
        List<LossComparisonRow> rows =
            service.lossComparison(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, CATEGORY_ID);

        assertThat(rows).extracting(LossComparisonRow::getMaterialId)
            .containsExactlyInAnyOrder(LETTUCE_ID, SAFFRON_ID, CHICKEN_ID, SALT_ID);
        assertThat(rows.getFirst().getMaterialId()).isEqualTo(LETTUCE_ID);
    }

    // =========================================================================
    // Leak-proofing, UOM, filters
    // =========================================================================

    @Test
    void neverLeaksOpeningBalanceRows() {
        // Opening balances carry a NULL reference_type; the IN predicate admits only the two
        // document types, so a warehouse's opening stock cannot read as an enormous loss.
        insertTransaction(TENANT_ID, LETTUCE_ID, WAREHOUSE_ID, "OPENING_BALANCE", "IN",
            "500", "5000.00", "2026-03-02 08:00:00", null);

        assertThat(rowFor(report(), LETTUCE_ID).getTotalValue()).isEqualTo("0.000000");
    }

    @Test
    void excludesMovementsOutsideTheWindow() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-02-28");
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-04-01");

        assertThat(rowFor(report(), LETTUCE_ID).getWasteValue()).isEqualTo("0.000000");
    }

    @Test
    void reportsQuantitiesInDisplayUom() {
        // 7000 g in the ledger must surface as 7 kg, with both columns sharing the one unit.
        insertTransaction(TENANT_ID, RICE_ID, WAREHOUSE_ID, "WASTE", "OUT",
            "7000", "140.00", "2026-03-10 08:00:00", WasteService.REFERENCE_TYPE);
        insertTransaction(TENANT_ID, RICE_ID, WAREHOUSE_ID, "COUNT_ADJUSTMENT", "OUT",
            "2000", "40.00", "2026-03-11 08:00:00", PhysicalCountService.REFERENCE_TYPE);

        LossComparisonRow row = rowFor(report(), RICE_ID);

        assertThat(row.getWasteQuantity()).isEqualTo("7.000000");
        assertThat(row.getShrinkageQuantity()).isEqualTo("-2.000000");
        assertThat(row.getUomId()).isEqualTo(UOM_KG_ID);
        assertThat(row.getUomSymbol()).isEqualTo("kg");
    }

    @Test
    void isolatesTenants() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");

        Long otherWarehouseId = 996_403L;
        Long otherMaterialId = 996_506L;
        insertWarehouse(otherWarehouseId, OTHER_TENANT_ID, "LCR-WH-X", "Foreign Warehouse");
        insertMaterial(otherMaterialId, OTHER_TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID,
            "LCR-M-X", "Foreign");
        insertTransaction(OTHER_TENANT_ID, otherMaterialId, otherWarehouseId, "WASTE", "OUT",
            "999", "9999.00", "2026-03-05 08:00:00", WasteService.REFERENCE_TYPE);

        assertThat(report()).extracting(LossComparisonRow::getMaterialId)
            .doesNotContain(otherMaterialId);
        assertThat(service.lossComparison(OTHER_TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null))
            .extracting(LossComparisonRow::getMaterialId).containsExactly(otherMaterialId);
    }

    @Test
    void filtersByWarehouseWithoutDroppingTheMaterialFromTheReport() {
        waste(LETTUCE_ID, SECOND_WAREHOUSE_ID, "10", "100.00", "2026-03-05");

        // Scoped to the main warehouse the loss is not counted — but the material still appears,
        // now as a clean row, because the warehouse predicate lives in the join, not the WHERE.
        List<LossComparisonRow> scoped =
            service.lossComparison(TENANT_ID, WINDOW_FROM, WINDOW_TO, WAREHOUSE_ID, null);
        assertThat(rowFor(scoped, LETTUCE_ID).getWasteValue()).isEqualTo("0.000000");

        assertThat(rowFor(report(), LETTUCE_ID).getWasteValue()).isEqualTo("100.000000");
    }

    @Test
    void filtersByCategory() {
        assertThat(service.lossComparison(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, OTHER_CATEGORY_ID))
            .extracting(LossComparisonRow::getMaterialId).containsExactly(RICE_ID);
    }

    @Test
    void reportsDeactivatedMaterialsWithTheFlagFalse() {
        jdbcTemplate.update("UPDATE material SET active = FALSE WHERE id = ?", SAFFRON_ID);
        countShortage(SAFFRON_ID, WAREHOUSE_ID, "4", "400.00", "2026-03-06");

        LossComparisonRow row = rowFor(report(), SAFFRON_ID);

        assertThat(row.getMaterialActive()).isFalse();
        assertThat(row.getShrinkageValue()).isEqualTo("-400.000000");
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() ->
            service.lossComparison(TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    @Test
    void anEmptyWindowStillReportsEveryMaterialAsClean() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "10", "100.00", "2026-03-05");

        List<LossComparisonRow> rows = service.lossComparison(
            TENANT_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null);

        // Not empty: the materials exist, they simply had no loss. Every row reads zero.
        assertThat(rows).hasSize(5);
        assertThat(rows).allSatisfy(row -> assertThat(row.getTotalValue()).isEqualTo("0.000000"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<LossComparisonRow> report() {
        return service.lossComparison(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null);
    }

    private static LossComparisonRow rowFor(List<LossComparisonRow> rows, Long materialId) {
        return rows.stream().filter(r -> r.getMaterialId().equals(materialId)).findFirst()
            .orElseThrow(() -> new AssertionError("no row for material " + materialId));
    }

    private void waste(Long materialId, Long warehouseId, String qty, String cost, String date) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "WASTE", "OUT", qty, cost,
            date + " 08:00:00", WasteService.REFERENCE_TYPE);
    }

    private void countShortage(Long materialId, Long warehouseId, String qty, String cost, String date) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "COUNT_ADJUSTMENT", "OUT", qty, cost,
            date + " 08:00:00", PhysicalCountService.REFERENCE_TYPE);
    }

    private void countSurplus(Long materialId, Long warehouseId, String qty, String cost, String date) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "COUNT_ADJUSTMENT", "IN", qty, cost,
            date + " 08:00:00", PhysicalCountService.REFERENCE_TYPE);
    }

    private void insertTransaction(Long tenantId, Long materialId, Long warehouseId,
                                   String transactionType, String direction, String stockQuantity,
                                   String totalCost, String movementTimestamp, String referenceType) {
        Long stockUomId = jdbcTemplate.queryForObject(
            "SELECT stock_uom_id FROM material WHERE id = ?", Long.class, materialId);
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id, total_cost,
                reference_type, transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), ?,
                    CAST(? AS numeric), ?, CAST(? AS timestamp), CAST(? AS timestamp),
                    CURRENT_TIMESTAMP)
            """,
            nextTransactionId++, tenantId, warehouseId, materialId, transactionType, direction,
            stockQuantity, stockUomId, stockQuantity, stockUomId, totalCost, referenceType,
            movementTimestamp, movementTimestamp);
    }

    private void insertTenant(Long id, String name, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, id, name, code);
    }

    private void insertUom(Long id, Long baseUomId, String code, String name, String symbol,
                           String factorToBase) {
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'WEIGHT', CAST(? AS numeric), CAST(? AS numeric), TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, baseUomId, code, name, symbol, factorToBase, factorToBase);
    }

    private void insertCategory(Long id, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, 'تصنيف', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, code, name);
    }

    private void insertWarehouse(Long id, Long tenantId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, tenantId.equals(TENANT_ID) ? BRANCH_ID : null, code, name);
    }

    private void insertMaterial(Long id, Long tenantId, Long categoryId, Long stockUomId,
                                Long displayUomId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'مادة', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, categoryId, stockUomId, displayUomId, code, name);
    }
}
