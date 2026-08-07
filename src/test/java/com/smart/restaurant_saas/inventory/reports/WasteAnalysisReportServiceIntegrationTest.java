package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.reports.dto.WasteAnalysisRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waste analysis shares its query shape with the shrinkage report, so this suite concentrates on
 * what actually differs — the reason in the grouping key, the reason filter, and the UNSPECIFIED
 * bucket — plus the leak/sign/UOM cases that must be proven independently for this reference type.
 * Verified against real Postgres; ids are in a dedicated high range and the test is transactional.
 */
@SpringBootTest
@Transactional
class WasteAnalysisReportServiceIntegrationTest {

    private static final Long TENANT_ID = 995_001L;
    private static final Long OTHER_TENANT_ID = 995_002L;
    private static final Long BRANCH_ID = 995_101L;

    private static final Long UOM_KG_ID = 995_201L;
    private static final Long UOM_G_ID = 995_202L;
    private static final Long UOM_LITRE_ID = 995_203L;

    private static final Long CATEGORY_ID = 995_301L;

    private static final Long WAREHOUSE_ID = 995_401L;
    private static final Long SECOND_WAREHOUSE_ID = 995_402L;

    /** Stock UOM grams, display UOM kilograms. */
    private static final Long CHICKEN_ID = 995_501L;
    /** Identity UOM. */
    private static final Long LETTUCE_ID = 995_502L;
    /** Stock UOM grams, display UOM litres — deliberately unconvertible. */
    private static final Long BROKEN_UOM_ID = 995_503L;

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 3, 31);

    private long nextTransactionId = 995_900L;

    @Autowired
    private WasteAnalysisReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "Waste Tenant", "WASTE_REPORT");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "WASTE_REPORT_OTHER");

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'WAR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        insertUom(UOM_KG_ID, null, "WAR-KG", "Kilogram", "kg", "WEIGHT", "1");
        insertUom(UOM_G_ID, UOM_KG_ID, "WAR-G", "Gram", "g", "WEIGHT", "0.001");
        insertUom(UOM_LITRE_ID, null, "WAR-L", "Litre", "L", "VOLUME", "1");

        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, 'WAR-FRESH', 'Fresh', 'طازج', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, CATEGORY_ID, TENANT_ID);

        insertWarehouse(WAREHOUSE_ID, TENANT_ID, "WAR-WH-1", "Main Warehouse");
        insertWarehouse(SECOND_WAREHOUSE_ID, TENANT_ID, "WAR-WH-2", "Second Warehouse");

        insertMaterial(CHICKEN_ID, TENANT_ID, UOM_G_ID, UOM_KG_ID, "WAR-M-1", "Chicken");
        insertMaterial(LETTUCE_ID, TENANT_ID, UOM_KG_ID, UOM_KG_ID, "WAR-M-2", "Lettuce");
        insertMaterial(BROKEN_UOM_ID, TENANT_ID, UOM_G_ID, UOM_LITRE_ID, "WAR-M-3", "Misconfigured");
    }

    // =========================================================================
    // The reason breakdown — what makes this a separate report
    // =========================================================================

    @Test
    void splitsOneMaterialIntoOneRowPerReason() {
        // "80 kg wasted" prompts nothing; "80 kg wasted, 60 of it expired" prompts a purchasing
        // change. That split is the whole point of the report.
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        waste(CHICKEN_ID, WAREHOUSE_ID, "20000", "310.00", "2026-03-06", "SPOILED");

        List<WasteAnalysisRow> rows = report();

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.getMaterialId()).isEqualTo(CHICKEN_ID));
        assertThat(rows).extracting(WasteAnalysisRow::getReasonCode)
            .containsExactly("EXPIRED", "SPOILED");
        assertThat(rowFor(rows, "EXPIRED").getNetQuantity()).isEqualTo("-60.000000");
        assertThat(rowFor(rows, "SPOILED").getNetQuantity()).isEqualTo("-20.000000");
    }

    @Test
    void reasonRowsSumToTheMaterialsOverallNet() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        waste(CHICKEN_ID, WAREHOUSE_ID, "20000", "310.00", "2026-03-06", "SPOILED");

        List<WasteAnalysisRow> rows = report();

        // Splitting by reason must not lose or double-count anything: the parts still make the
        // whole, which is what lets a frontend total the column it renders.
        assertThat(sumOf(rows, WasteAnalysisRow::getNetValue))
            .isEqualByComparingTo(new BigDecimal("-1240.000000"));
        assertThat(sumOf(rows, WasteAnalysisRow::getNetQuantity))
            .isEqualByComparingTo(new BigDecimal("-80.000000"));
        assertThat(rows).extracting(WasteAnalysisRow::getMovementCount).containsOnly(1L);
    }

    @Test
    void filtersByReasonCode() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        waste(CHICKEN_ID, WAREHOUSE_ID, "20000", "310.00", "2026-03-06", "SPOILED");

        List<WasteAnalysisRow> rows = service.wasteAnalysis(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, "EXPIRED", false);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getReasonCode()).isEqualTo("EXPIRED");
            assertThat(row.getNetValue()).isEqualTo("-930.000000");
        });
    }

    @Test
    void groupsAReasonlessRowUnderUnspecifiedRatherThanDroppingIt() {
        // waste_document.reason_code has been NOT NULL since V11, so this should be unreachable.
        // Pinned anyway: the alternative failure mode is silent, and a dropped row would make the
        // rendered column stop agreeing with reality.
        waste(LETTUCE_ID, WAREHOUSE_ID, "5", "25.00", "2026-03-07", null);
        waste(LETTUCE_ID, WAREHOUSE_ID, "3", "15.00", "2026-03-08", "DAMAGED");

        List<WasteAnalysisRow> rows = report();

        assertThat(rows).extracting(WasteAnalysisRow::getReasonCode)
            .containsExactlyInAnyOrder("UNSPECIFIED", "DAMAGED");
        assertThat(rowFor(rows, "UNSPECIFIED").getNetValue()).isEqualTo("-25.000000");
        assertThat(sumOf(rows, WasteAnalysisRow::getNetValue))
            .isEqualByComparingTo(new BigDecimal("-40.000000"));
    }

    @Test
    void unspecifiedIsSelectableAsAReasonFilter() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "5", "25.00", "2026-03-07", null);
        waste(LETTUCE_ID, WAREHOUSE_ID, "3", "15.00", "2026-03-08", "DAMAGED");

        assertThat(service.wasteAnalysis(
                TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, "UNSPECIFIED", false))
            .singleElement()
            .satisfies(row -> assertThat(row.getNetValue()).isEqualTo("-25.000000"));
    }

    // =========================================================================
    // Leak-proofing
    // =========================================================================

    @Test
    void returnsOnlyWasteRows() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        // A physical-count correction in the same window — the other report's rows must not leak in.
        insertTransaction(TENANT_ID, LETTUCE_ID, WAREHOUSE_ID, "COUNT_ADJUSTMENT", "OUT",
            "70", "35.00", "2026-03-11 08:00:00", PhysicalCountService.REFERENCE_TYPE, null);

        assertThat(report()).extracting(WasteAnalysisRow::getMaterialId).containsExactly(CHICKEN_ID);
    }

    @Test
    void neverLeaksOpeningBalanceRows() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        insertTransaction(TENANT_ID, LETTUCE_ID, WAREHOUSE_ID, "OPENING_BALANCE", "IN",
            "500", "250.00", "2026-03-02 08:00:00", null, null);

        assertThat(report()).extracting(WasteAnalysisRow::getMaterialId).containsExactly(CHICKEN_ID);
    }

    @Test
    void isolatesTenants() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");

        Long otherWarehouseId = 995_403L;
        Long otherMaterialId = 995_504L;
        insertWarehouse(otherWarehouseId, OTHER_TENANT_ID, "WAR-WH-X", "Foreign Warehouse");
        insertMaterial(otherMaterialId, OTHER_TENANT_ID, UOM_KG_ID, UOM_KG_ID, "WAR-M-X", "Foreign");
        insertTransaction(OTHER_TENANT_ID, otherMaterialId, otherWarehouseId, "WASTE", "OUT",
            "999", "9999.00", "2026-03-05 08:00:00", WasteService.REFERENCE_TYPE, "THEFT");

        assertThat(report()).extracting(WasteAnalysisRow::getMaterialId).containsExactly(CHICKEN_ID);
        assertThat(service.wasteAnalysis(
                OTHER_TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null, false))
            .extracting(WasteAnalysisRow::getMaterialId).containsExactly(otherMaterialId);
    }

    // =========================================================================
    // Signs, sorting, UOM, edges
    // =========================================================================

    @Test
    void preservesSignsAndNetsAReversedWriteOffToZero() {
        // A POSTED waste document cannot be cancelled today, so no reversal exists in practice.
        // If one ever did, the signed sum must net it away rather than report a loss that was
        // undone — which is why no reversesTransactionId guard is applied.
        waste(LETTUCE_ID, WAREHOUSE_ID, "5", "25.00", "2026-03-07", "DAMAGED");
        insertTransaction(TENANT_ID, LETTUCE_ID, WAREHOUSE_ID, "WASTE", "IN",
            "5", "25.00", "2026-03-08 08:00:00", WasteService.REFERENCE_TYPE, "DAMAGED");

        WasteAnalysisRow row = rowFor(report(), "DAMAGED");

        assertThat(row.getNetQuantity()).isEqualTo("0.000000");
        assertThat(row.getNetValue()).isEqualTo("0.000000");
        assertThat(row.getMovementCount()).isEqualTo(2L);
    }

    @Test
    void sortsByAbsoluteValueDescending() {
        waste(LETTUCE_ID, WAREHOUSE_ID, "3", "15.00", "2026-03-08", "DAMAGED");
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");

        assertThat(report()).extracting(WasteAnalysisRow::getMaterialId)
            .containsExactly(CHICKEN_ID, LETTUCE_ID);
    }

    @Test
    void reportsInDisplayUomWithItsSymbol() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");

        WasteAnalysisRow row = rowFor(report(), "EXPIRED");

        assertThat(row.getNetQuantity()).isEqualTo("-60.000000");
        assertThat(row.getUomId()).isEqualTo(UOM_KG_ID);
        assertThat(row.getUomSymbol()).isEqualTo("kg");
    }

    @Test
    void degradesAnUnconvertibleRowWithoutFailingTheReport() {
        waste(BROKEN_UOM_ID, WAREHOUSE_ID, "500", "900.00", "2026-03-05", "OTHER");
        waste(LETTUCE_ID, WAREHOUSE_ID, "3", "15.00", "2026-03-08", "DAMAGED");

        List<WasteAnalysisRow> rows = report();

        WasteAnalysisRow degraded = rowFor(rows, "OTHER");
        assertThat(degraded.getNetQuantity()).isNull();
        assertThat(degraded.getUomId()).isNull();
        assertThat(degraded.getUomSymbol()).isNull();
        assertThat(degraded.getNetValue()).isEqualTo("-900.000000");
        // Value is intact, so the degraded row still sorts on it.
        assertThat(rows).extracting(WasteAnalysisRow::getMaterialId)
            .containsExactly(BROKEN_UOM_ID, LETTUCE_ID);
    }

    @Test
    void filtersByWarehouseAndDefaultsToAllWarehouses() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");
        waste(LETTUCE_ID, SECOND_WAREHOUSE_ID, "3", "15.00", "2026-03-08", "DAMAGED");

        assertThat(service.wasteAnalysis(
                TENANT_ID, WINDOW_FROM, WINDOW_TO, WAREHOUSE_ID, null, null, false))
            .extracting(WasteAnalysisRow::getMaterialId).containsExactly(CHICKEN_ID);
        assertThat(report()).extracting(WasteAnalysisRow::getMaterialId)
            .containsExactlyInAnyOrder(CHICKEN_ID, LETTUCE_ID);
    }

    @Test
    void anEmptyRangeIsAnEmptyListNotAnError() {
        waste(CHICKEN_ID, WAREHOUSE_ID, "60000", "930.00", "2026-03-05", "EXPIRED");

        assertThat(service.wasteAnalysis(TENANT_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null, false)).isEmpty();
    }

    @Test
    void rejectsAMissingDate() {
        assertThatThrownBy(() -> service.wasteAnalysis(
                TENANT_ID, null, WINDOW_TO, null, null, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() -> service.wasteAnalysis(
                TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, null, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<WasteAnalysisRow> report() {
        return service.wasteAnalysis(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null, false);
    }

    private static WasteAnalysisRow rowFor(List<WasteAnalysisRow> rows, String reasonCode) {
        return rows.stream().filter(r -> reasonCode.equals(r.getReasonCode())).findFirst()
            .orElseThrow(() -> new AssertionError("no row for reason " + reasonCode));
    }

    private static BigDecimal sumOf(List<WasteAnalysisRow> rows,
                                    java.util.function.Function<WasteAnalysisRow, String> field) {
        return rows.stream().map(field).map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void waste(Long materialId, Long warehouseId, String stockQuantity, String totalCost,
                       String movementDate, String reasonCode) {
        insertTransaction(TENANT_ID, materialId, warehouseId, "WASTE", "OUT", stockQuantity,
            totalCost, movementDate + " 08:00:00", WasteService.REFERENCE_TYPE, reasonCode);
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

    private void insertWarehouse(Long id, Long tenantId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, tenantId.equals(TENANT_ID) ? BRANCH_ID : null, code, name);
    }

    private void insertMaterial(Long id, Long tenantId, Long stockUomId, Long displayUomId,
                                String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'مادة', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, CATEGORY_ID, stockUomId, displayUomId, code, name);
    }
}
