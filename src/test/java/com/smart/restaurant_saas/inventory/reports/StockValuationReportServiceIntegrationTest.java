package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.restaurant_saas.inventory.reports.dto.StockValuationRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the report against real Postgres: the active-only filter lives in the JPQL, so it can
 * only be verified end-to-end. Seeded ids are in a dedicated high range and the test is
 * transactional, so nothing survives the run.
 */
@SpringBootTest
@Transactional
class StockValuationReportServiceIntegrationTest {

    private static final Long TENANT_ID = 992_001L;
    private static final Long BRANCH_ID = 992_101L;
    private static final Long UOM_ID = 992_201L;
    private static final Long CATEGORY_ID = 992_301L;

    private static final Long ACTIVE_WAREHOUSE_ID = 992_401L;
    private static final Long INACTIVE_WAREHOUSE_ID = 992_402L;
    private static final Long BRANCHLESS_WAREHOUSE_ID = 992_403L;

    private static final Long ACTIVE_MATERIAL_ID = 992_501L;
    private static final Long INACTIVE_MATERIAL_ID = 992_502L;

    @Autowired
    private StockValuationReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Constructed locally: this application context exposes no ObjectMapper bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Stock Valuation Tenant', 'STOCK_VALUATION_REPORT', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'SVR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'SVR-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, UOM_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, 'SVR-VEG', 'Vegetables', 'خضروات', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, CATEGORY_ID, TENANT_ID);

        insertWarehouse(ACTIVE_WAREHOUSE_ID, BRANCH_ID, "SVR-WH-1", "Main Warehouse", "المستودع الرئيسي", true);
        insertWarehouse(INACTIVE_WAREHOUSE_ID, BRANCH_ID, "SVR-WH-2", "Retired Warehouse", null, false);
        insertWarehouse(BRANCHLESS_WAREHOUSE_ID, null, "SVR-WH-3", "Central Warehouse", null, true);

        insertMaterial(ACTIVE_MATERIAL_ID, "SVR-MAT-1", "Tomato", "طماطم", true);
        insertMaterial(INACTIVE_MATERIAL_ID, "SVR-MAT-2", "Discontinued Spice", null, false);

        // Active material in the active warehouse — the only row expected in the default report,
        // besides the branch-less warehouse row.
        insertBalance(992_601L, ACTIVE_WAREHOUSE_ID, ACTIVE_MATERIAL_ID, "12.5", "4");
        // Excluded: inactive material.
        insertBalance(992_602L, ACTIVE_WAREHOUSE_ID, INACTIVE_MATERIAL_ID, "9", "3");
        // Excluded: inactive warehouse.
        insertBalance(992_603L, INACTIVE_WAREHOUSE_ID, ACTIVE_MATERIAL_ID, "7", "2");
        // Included: warehouse with no branch must survive the LEFT JOIN when no branch filter is set.
        insertBalance(992_604L, BRANCHLESS_WAREHOUSE_ID, ACTIVE_MATERIAL_ID, "1", "1");
    }

    @Test
    void excludesInactiveMaterialsAndInactiveWarehouses() {
        List<StockValuationRow> rows = service.stockValuation(TENANT_ID, null, null, null);

        assertThat(rows).extracting(StockValuationRow::getWarehouseId, StockValuationRow::getMaterialId)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(ACTIVE_WAREHOUSE_ID, ACTIVE_MATERIAL_ID),
                org.assertj.core.groups.Tuple.tuple(BRANCHLESS_WAREHOUSE_ID, ACTIVE_MATERIAL_ID));
    }

    @Test
    void keepsBranchlessWarehousesWhenNoBranchFilterIsSupplied() {
        List<StockValuationRow> rows = service.stockValuation(TENANT_ID, null, null, null);

        assertThat(rows).extracting(StockValuationRow::getWarehouseId)
            .contains(BRANCHLESS_WAREHOUSE_ID);
    }

    @Test
    void branchFilterNarrowsToWarehousesOfThatBranch() {
        List<StockValuationRow> rows = service.stockValuation(TENANT_ID, BRANCH_ID, null, null);

        assertThat(rows).extracting(StockValuationRow::getWarehouseId)
            .containsExactly(ACTIVE_WAREHOUSE_ID);
    }

    @Test
    void serializesQuantityCostAndValueAsJsonStrings() throws Exception {
        StockValuationRow row = service.stockValuation(TENANT_ID, null, ACTIVE_WAREHOUSE_ID, null)
            .getFirst();

        assertThat(row.getQuantity()).isEqualTo("12.500000");
        assertThat(row.getAverageCost()).isEqualTo("4.000000");
        assertThat(row.getTotalValue()).isEqualTo("50.000000");
        assertThat(row.getWarehouseNameAr()).isEqualTo("المستودع الرئيسي");
        assertThat(row.getCategoryNameAr()).isEqualTo("خضروات");

        String json = objectMapper.writeValueAsString(row);
        assertThat(json)
            .contains("\"quantity\":\"12.500000\"")
            .contains("\"averageCost\":\"4.000000\"")
            .contains("\"totalValue\":\"50.000000\"");
    }

    private void insertWarehouse(Long id, Long branchId, String code, String name, String nameAr, boolean active) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, name_ar, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'CENTRAL', ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, branchId, code, name, nameAr, active);
    }

    private void insertMaterial(Long id, String code, String name, String nameAr, boolean active) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name, nameAr, active);
    }

    private void insertBalance(Long id, Long warehouseId, Long materialId, String quantity, String averageCost) {
        jdbcTemplate.update("""
            INSERT INTO stock_balance (id, tenant_id, warehouse_id, material_id, uom_id,
                                       quantity, average_cost, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, warehouseId, materialId, UOM_ID, quantity, averageCost);
    }
}
