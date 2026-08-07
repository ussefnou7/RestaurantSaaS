package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.reports.dto.ShrinkageRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shrinkage aggregate — signed sums, the reference-type filter, and the absolute-value sort —
 * lives entirely in JPQL, so it is verified against real Postgres. Seeded ids are in a dedicated
 * high range and the test is transactional, so nothing survives the run.
 */
@SpringBootTest
@Transactional
class ShrinkageReportServiceIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long OTHER_TENANT_ID = 994_002L;
    private static final Long BRANCH_ID = 994_101L;

    private static final Long UOM_KG_ID = 994_201L;
    private static final Long UOM_G_ID = 994_202L;
    private static final Long UOM_LITRE_ID = 994_203L;

    private static final Long CATEGORY_ID = 994_301L;
    private static final Long OTHER_CATEGORY_ID = 994_302L;

    private static final Long WAREHOUSE_ID = 994_401L;
    private static final Long SECOND_WAREHOUSE_ID = 994_402L;

    /** Stock UOM grams, display UOM kilograms — proves the display-layer conversion. */
    private static final Long CHICKEN_ID = 994_501L;
    /** Identity UOM, used for the sign/sort cases. */
    private static final Long ICE_ID = 994_502L;
    /** Identity UOM, nets a shortage and a surplus together. */
    private static final Long RICE_ID = 994_503L;
    /** Stock UOM grams, display UOM litres — deliberately unconvertible. */
    private static final Long BROKEN_UOM_ID = 994_504L;
    /** Deactivated after its movements were recorded — the "steal it, then retire it" case. */
    private static final Long RETIRED_MATERIAL_ID = 994_506L;

    private static final Long RETIRED_WAREHOUSE_ID = 994_404L;

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 3, 31);

    private long nextTransactionId = 994_900L;

    @Autowired
    private ShrinkageReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "Shrinkage Tenant", "SHRINKAGE_REPORT");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "SHRINKAGE_REPORT_OTHER");

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'SHR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        insertUom(UOM_KG_ID, null, "SHR-KG", "Kilogram", "kg", "WEIGHT", "1");
        insertUom(UOM_G_ID, UOM_KG_ID, "SHR-G", "Gram", "g", "WEIGHT", "0.001");
        // Its own base, and a different physical type — no conversion path from grams.
        insertUom(UOM_LITRE_ID, null, "SHR-L", "Litre", "L", "VOLUME", "1");

        insertCategory(CATEGORY_ID, "SHR-MEAT", "Meat");
        insertCategory(OTHER_CATEGORY_ID, "SHR-DRY", "Dry Goods");

        insertWarehouse(WAREHOUSE_ID, TENANT_ID, "SHR-WH-1", "Main Warehouse");
        insertWarehouse(SECOND_WAREHOUSE_ID, TENANT_ID, "SHR-WH-2", "Second Warehouse");
        insertWarehouse(RETIRED_WAREHOUSE_ID, TENANT_ID, "SHR-WH-3", "Retired Warehouse");

        insertMaterial(CHICKEN_ID, TENANT_ID, CATEGORY_ID, UOM_G_ID, UOM_KG_ID, "SHR-M-1", "Chicken");
        insertMaterial(ICE_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "SHR-M-2", "Ice");
        insertMaterial(RICE_ID, TENANT_ID, OTHER_CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "SHR-M-3", "Rice");
        insertMaterial(BROKEN_UOM_ID, TENANT_ID, CATEGORY_ID, UOM_G_ID, UOM_LITRE_ID,
            "SHR-M-4", "Misconfigured Spice");
        insertMaterial(RETIRED_MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID,
            "SHR-M-6", "Retired Saffron");

        // Deactivated after the fact, which is exactly the sequence that matters: the movements
        // below were recorded while both were live.
        deactivateMaterial(RETIRED_MATERIAL_ID);
        deactivateWarehouse(RETIRED_WAREHOUSE_ID);
    }

    // =========================================================================
    // What belongs in the report
    // =========================================================================

    @Test
    void returnsOnlyPhysicalCountRows() {
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");
        // A waste write-off in the same window — the other report's rows must not leak in.
        wasteMovement(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-03-11", "EXPIRED");

        List<ShrinkageRow> rows = report();

        assertThat(rows).extracting(ShrinkageRow::getMaterialId).containsExactly(CHICKEN_ID);
    }

    @Test
    void neverLeaksOpeningBalanceRows() {
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");
        // Opening balances carry a NULL reference_type. Were the filter written as "not waste"
        // instead of "is a physical count", a warehouse's entire opening stock would surface here
        // as one enormous shortage.
        openingBalance(ICE_ID, WAREHOUSE_ID, "500", "250.00", "2026-03-02");

        List<ShrinkageRow> rows = report();

        assertThat(rows).extracting(ShrinkageRow::getMaterialId).containsExactly(CHICKEN_ID);
    }

    @Test
    void excludesMovementsOutsideTheWindowAtBothEnds() {
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-02-28");
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-04-01");
        // Late on the final day: a closed upper bound of dateTo 00:00 would silently drop this.
        countAdjustmentAt(RICE_ID, WAREHOUSE_ID, "OUT", "10", "50.00", "2026-03-31 23:30:00", null);

        List<ShrinkageRow> rows = report();

        assertThat(rows).extracting(ShrinkageRow::getMaterialId).containsExactly(RICE_ID);
    }

    @Test
    void isolatesTenants() {
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");

        Long otherWarehouseId = 994_403L;
        Long otherMaterialId = 994_505L;
        insertWarehouse(otherWarehouseId, OTHER_TENANT_ID, "SHR-WH-X", "Foreign Warehouse");
        insertMaterial(otherMaterialId, OTHER_TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID,
            "SHR-M-X", "Foreign Material");
        insertTransaction(OTHER_TENANT_ID, otherMaterialId, otherWarehouseId, "COUNT_ADJUSTMENT",
            "OUT", "999", "9999.00", "2026-03-10 08:00:00",
            PhysicalCountService.REFERENCE_TYPE, null);

        List<ShrinkageRow> rows = report();

        assertThat(rows).extracting(ShrinkageRow::getMaterialId).containsExactly(CHICKEN_ID);
        assertThat(service.shrinkage(OTHER_TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, false))
            .extracting(ShrinkageRow::getMaterialId)
            .containsExactly(otherMaterialId);
    }

    // =========================================================================
    // Deactivation must not erase history (D86 historical-report amendment)
    // =========================================================================

    @Test
    void reportsADeactivatedMaterialWithTheFlagSetFalse() {
        // The threat model: steal a material, then deactivate it. An active filter here would make
        // the deactivation the cover-up — the shortage would vanish from the one report meant to
        // surface it, with no row and nothing to indicate an omission.
        countAdjustment(RETIRED_MATERIAL_ID, WAREHOUSE_ID, "OUT", "40", "800.00", "2026-03-10");

        ShrinkageRow row = rowFor(report(), RETIRED_MATERIAL_ID);

        assertThat(row.getMaterialActive()).isFalse();
        assertThat(row.getNetQuantity()).isEqualTo("-40.000000");
        assertThat(row.getNetValue()).isEqualTo("-800.000000");
    }

    @Test
    void theFlagDoesNotChangeCountOrValue() {
        // Same movements against an active and an inactive material: the figures must be identical,
        // so the flag is purely descriptive and never a hidden filter or a weighting.
        countAdjustment(RETIRED_MATERIAL_ID, WAREHOUSE_ID, "OUT", "40", "800.00", "2026-03-10");
        countAdjustment(RETIRED_MATERIAL_ID, WAREHOUSE_ID, "IN", "15", "300.00", "2026-03-12");
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "40", "800.00", "2026-03-10");
        countAdjustment(ICE_ID, WAREHOUSE_ID, "IN", "15", "300.00", "2026-03-12");

        List<ShrinkageRow> rows = report();
        ShrinkageRow retired = rowFor(rows, RETIRED_MATERIAL_ID);
        ShrinkageRow active = rowFor(rows, ICE_ID);

        assertThat(retired.getMaterialActive()).isFalse();
        assertThat(active.getMaterialActive()).isTrue();
        assertThat(retired.getNetQuantity()).isEqualTo(active.getNetQuantity());
        assertThat(retired.getNetValue()).isEqualTo(active.getNetValue());
        assertThat(retired.getMovementCount()).isEqualTo(active.getMovementCount());
    }

    @Test
    void reportsMovementsFromADeactivatedWarehouse() {
        // No warehouse flag exists on the row — rows span warehouses — but the movements must still
        // be counted, or retiring a warehouse would erase everything that ever happened in it.
        countAdjustment(ICE_ID, RETIRED_WAREHOUSE_ID, "OUT", "25", "125.00", "2026-03-10");

        ShrinkageRow row = rowFor(report(), ICE_ID);

        assertThat(row.getNetValue()).isEqualTo("-125.000000");
        assertThat(row.getMaterialActive()).isTrue();
    }

    @Test
    void foldsActiveAndDeactivatedWarehousesIntoOneMaterialRow() {
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "10", "50.00", "2026-03-10");
        countAdjustment(ICE_ID, RETIRED_WAREHOUSE_ID, "OUT", "25", "125.00", "2026-03-11");

        List<ShrinkageRow> rows = report();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getNetQuantity()).isEqualTo("-35.000000");
        assertThat(rows.getFirst().getNetValue()).isEqualTo("-175.000000");
        assertThat(rows.getFirst().getMovementCount()).isEqualTo(2L);
    }

    // =========================================================================
    // Signs
    // =========================================================================

    @Test
    void preservesSignsAndNetsAMaterialThatHadBoth() {
        // Shortage of 30 then a surplus of 12 — nets to -18 kg and -90.00.
        countAdjustment(RICE_ID, WAREHOUSE_ID, "OUT", "30", "150.00", "2026-03-05");
        countAdjustment(RICE_ID, WAREHOUSE_ID, "IN", "12", "60.00", "2026-03-20");

        ShrinkageRow row = rowFor(report(), RICE_ID);

        assertThat(row.getNetQuantity()).isEqualTo("-18.000000");
        assertThat(row.getNetValue()).isEqualTo("-90.000000");
        assertThat(row.getMovementCount()).isEqualTo(2L);
    }

    @Test
    void reportsASurplusAsPositive() {
        countAdjustment(ICE_ID, WAREHOUSE_ID, "IN", "40", "20.00", "2026-03-12");

        ShrinkageRow row = rowFor(report(), ICE_ID);

        assertThat(row.getNetQuantity()).isEqualTo("40.000000");
        assertThat(row.getNetValue()).isEqualTo("20.000000");
    }

    @Test
    void negativesOnlyDropsANetSurplusButKeepsANetShortage() {
        countAdjustment(ICE_ID, WAREHOUSE_ID, "IN", "40", "20.00", "2026-03-12");
        countAdjustment(RICE_ID, WAREHOUSE_ID, "OUT", "30", "150.00", "2026-03-05");

        List<ShrinkageRow> rows =
            service.shrinkage(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, true);

        assertThat(rows).extracting(ShrinkageRow::getMaterialId).containsExactly(RICE_ID);
    }

    // =========================================================================
    // Sorting
    // =========================================================================

    @Test
    void sortsByAbsoluteValueSoALargePositiveOutranksASmallNegative() {
        // A big surplus is worth more attention than a trivial shortage — usually a wrong recipe
        // or a rushed count. Sorting by the signed value would bury it at the bottom.
        countAdjustment(ICE_ID, WAREHOUSE_ID, "IN", "40", "900.00", "2026-03-12");
        countAdjustment(RICE_ID, WAREHOUSE_ID, "OUT", "30", "20.00", "2026-03-05");

        List<ShrinkageRow> rows = report();

        assertThat(rows).extracting(ShrinkageRow::getMaterialId)
            .containsExactly(ICE_ID, RICE_ID);
        assertThat(rows.getFirst().getNetValue()).isEqualTo("900.000000");
    }

    @Test
    void ranksByValueNotByQuantity() {
        // 7 kg of chicken beats 70 kg of ice; only the value says so.
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-03-10");

        assertThat(report()).extracting(ShrinkageRow::getMaterialId)
            .containsExactly(CHICKEN_ID, ICE_ID);
    }

    // =========================================================================
    // UOM
    // =========================================================================

    @Test
    void reportsInDisplayUomWithItsSymbol() {
        // 7000 g in the ledger (stock UOM) must surface as 7 kg (display UOM), per D87/D88.
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");

        ShrinkageRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getNetQuantity()).isEqualTo("-7.000000");
        assertThat(row.getUomId()).isEqualTo(UOM_KG_ID);
        assertThat(row.getUomSymbol()).isEqualTo("kg");
        assertThat(row.getNetValue()).isEqualTo("-140.000000");
    }

    @Test
    void degradesAnUnconvertibleRowWithoutFailingTheReport() {
        countAdjustment(BROKEN_UOM_ID, WAREHOUSE_ID, "OUT", "500", "900.00", "2026-03-10");
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-03-10");

        List<ShrinkageRow> rows = report();

        ShrinkageRow degraded = rowFor(rows, BROKEN_UOM_ID);
        // Quantity and its unit go null together, so nothing can misread a bare number...
        assertThat(degraded.getNetQuantity()).isNull();
        assertThat(degraded.getUomId()).isNull();
        assertThat(degraded.getUomSymbol()).isNull();
        // ...but the value is money, needs no conversion, and stays exact.
        assertThat(degraded.getNetValue()).isEqualTo("-900.000000");
        assertThat(degraded.getMovementCount()).isEqualTo(1L);

        // The healthy row is untouched and the degraded row still sorts by its intact value.
        assertThat(rows).extracting(ShrinkageRow::getMaterialId)
            .containsExactly(BROKEN_UOM_ID, ICE_ID);
        assertThat(rowFor(rows, ICE_ID).getNetQuantity()).isEqualTo("-70.000000");
    }

    // =========================================================================
    // Filters and edges
    // =========================================================================

    @Test
    void filtersByWarehouseAndDefaultsToAllWarehouses() {
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-03-10");
        countAdjustment(RICE_ID, SECOND_WAREHOUSE_ID, "OUT", "30", "150.00", "2026-03-10");

        assertThat(service.shrinkage(TENANT_ID, WINDOW_FROM, WINDOW_TO, WAREHOUSE_ID, null, false))
            .extracting(ShrinkageRow::getMaterialId).containsExactly(ICE_ID);
        assertThat(report()).extracting(ShrinkageRow::getMaterialId)
            .containsExactlyInAnyOrder(ICE_ID, RICE_ID);
    }

    @Test
    void filtersByCategory() {
        countAdjustment(ICE_ID, WAREHOUSE_ID, "OUT", "70", "35.00", "2026-03-10");
        countAdjustment(RICE_ID, WAREHOUSE_ID, "OUT", "30", "150.00", "2026-03-10");

        assertThat(service.shrinkage(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, OTHER_CATEGORY_ID, false))
            .extracting(ShrinkageRow::getMaterialId).containsExactly(RICE_ID);
    }

    @Test
    void anEmptyRangeIsAnEmptyListNotAnError() {
        countAdjustment(CHICKEN_ID, WAREHOUSE_ID, "OUT", "7000", "140.00", "2026-03-10");

        assertThat(service.shrinkage(TENANT_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, false)).isEmpty();
    }

    @Test
    void rejectsAMissingDate() {
        // The dates bind as optional and are enforced here rather than by required = true, because
        // Spring's MissingServletRequestParameterException is unhandled and surfaces as a 500.
        assertThatThrownBy(() -> service.shrinkage(TENANT_ID, null, WINDOW_TO, null, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.shrinkage(TENANT_ID, WINDOW_FROM, null, null, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAnInvertedRangeRatherThanReturningEmpty() {
        // Silently returning empty would read as "no shrinkage this period" — a materially wrong
        // conclusion to hand someone.
        assertThatThrownBy(() -> service.shrinkage(TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<ShrinkageRow> report() {
        return service.shrinkage(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, false);
    }

    private static ShrinkageRow rowFor(List<ShrinkageRow> rows, Long materialId) {
        return rows.stream().filter(r -> r.getMaterialId().equals(materialId)).findFirst()
            .orElseThrow(() -> new AssertionError("no row for material " + materialId));
    }

    private void countAdjustment(Long materialId, Long warehouseId, String direction,
                                 String stockQuantity, String totalCost, String movementDate) {
        countAdjustmentAt(materialId, warehouseId, direction, stockQuantity, totalCost,
            movementDate + " 08:00:00", null);
    }

    private void countAdjustmentAt(Long materialId, Long warehouseId, String direction,
                                   String stockQuantity, String totalCost, String movementTimestamp,
                                   String reasonCode) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "COUNT_ADJUSTMENT", direction,
            stockQuantity, totalCost, movementTimestamp, PhysicalCountService.REFERENCE_TYPE,
            reasonCode);
    }

    private void wasteMovement(Long materialId, Long warehouseId, String direction,
                               String stockQuantity, String totalCost, String movementDate,
                               String reasonCode) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "WASTE", direction, stockQuantity,
            totalCost, movementDate + " 08:00:00", WasteService.REFERENCE_TYPE, reasonCode);
    }

    private void openingBalance(Long materialId, Long warehouseId, String stockQuantity,
                                String totalCost, String movementDate) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "OPENING_BALANCE", "IN",
            stockQuantity, totalCost, movementDate + " 08:00:00", null, null);
    }

    private void insertTransaction(Long tenantId, Long materialId, Long warehouseId,
                                   String transactionType, String direction, String stockQuantity,
                                   String totalCost, String movementTimestamp, String referenceType,
                                   String reasonCode) {
        Long stockUomId = jdbcTemplate.queryForObject(
            "SELECT stock_uom_id FROM material WHERE id = ?", Long.class, materialId);
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id, total_cost,
                reference_type, reason_code, transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), ?,
                    CAST(? AS numeric), ?, ?, CAST(? AS timestamp), CAST(? AS timestamp),
                    CURRENT_TIMESTAMP)
            """,
            nextTransactionId++, tenantId, warehouseId, materialId, transactionType, direction,
            stockQuantity, stockUomId, stockQuantity, stockUomId, totalCost, referenceType,
            reasonCode, movementTimestamp, movementTimestamp);
    }

    private void insertTenant(Long id, String name, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, name, code);
    }

    private void insertUom(Long id, Long baseUomId, String code, String name, String symbol,
                           String type, String factorToBase) {
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS numeric), TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, baseUomId, code, name, symbol, type, factorToBase);
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

    private void deactivateMaterial(Long id) {
        jdbcTemplate.update("UPDATE material SET active = FALSE WHERE id = ?", id);
    }

    private void deactivateWarehouse(Long id) {
        jdbcTemplate.update("UPDATE warehouse SET active = FALSE WHERE id = ?", id);
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
