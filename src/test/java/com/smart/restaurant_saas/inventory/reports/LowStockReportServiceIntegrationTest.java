package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.reports.dto.LowStockRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The low-stock predicate lives in the JPQL, so it is verified against real Postgres. Seeded ids
 * are in a dedicated high range and the test is transactional, so nothing survives the run.
 */
@SpringBootTest
@Transactional
class LowStockReportServiceIntegrationTest {

    private static final Long TENANT_ID = 993_001L;
    private static final Long BRANCH_ID = 993_101L;
    private static final Long UOM_ID = 993_201L;
    private static final Long CATEGORY_ID = 993_301L;
    private static final Long WAREHOUSE_ID = 993_401L;
    private static final Long INACTIVE_WAREHOUSE_ID = 993_402L;

    private static final Long BELOW_MIN_MATERIAL_ID = 993_501L;
    private static final Long AT_MIN_MATERIAL_ID = 993_502L;
    private static final Long NO_MIN_ZERO_QTY_MATERIAL_ID = 993_503L;
    private static final Long ABOVE_MIN_MATERIAL_ID = 993_504L;
    private static final Long INACTIVE_MATERIAL_ID = 993_505L;

    @Autowired
    private LowStockReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, 'Low Stock Tenant', 'LOW_STOCK_REPORT', 'ACTIVE', CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'LSR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'LSR-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, UOM_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, 'LSR-VEG', 'Vegetables', 'خضروات', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, CATEGORY_ID, TENANT_ID);

        insertWarehouse(WAREHOUSE_ID, "LSR-WH-1", "Main Warehouse", true);
        insertWarehouse(INACTIVE_WAREHOUSE_ID, "LSR-WH-2", "Retired Warehouse", false);

        insertMaterial(BELOW_MIN_MATERIAL_ID, "LSR-MAT-1", "Tomato", true);
        insertMaterial(AT_MIN_MATERIAL_ID, "LSR-MAT-2", "Onion", true);
        insertMaterial(NO_MIN_ZERO_QTY_MATERIAL_ID, "LSR-MAT-3", "Saffron", true);
        insertMaterial(ABOVE_MIN_MATERIAL_ID, "LSR-MAT-4", "Rice", true);
        insertMaterial(INACTIVE_MATERIAL_ID, "LSR-MAT-5", "Discontinued Spice", false);

        // Reported: below its minimum.
        insertBalance(993_601L, WAREHOUSE_ID, BELOW_MIN_MATERIAL_ID, "2", "5");
        // Not reported: exactly at the minimum is not below it.
        insertBalance(993_602L, WAREHOUSE_ID, AT_MIN_MATERIAL_ID, "5", "5");
        // Not reported: no minimum configured (stored as 0) even though quantity is 0 — the case
        // a COALESCE(min, 0) implementation would wrongly flag as low stock.
        insertBalance(993_603L, WAREHOUSE_ID, NO_MIN_ZERO_QTY_MATERIAL_ID, "0", "0");
        // Not reported: comfortably above the minimum.
        insertBalance(993_604L, WAREHOUSE_ID, ABOVE_MIN_MATERIAL_ID, "40", "10");
        // Not reported: inactive material, despite being below minimum.
        insertBalance(993_605L, WAREHOUSE_ID, INACTIVE_MATERIAL_ID, "1", "9");
        // Not reported: inactive warehouse, despite being below minimum.
        insertBalance(993_606L, INACTIVE_WAREHOUSE_ID, BELOW_MIN_MATERIAL_ID, "1", "9");
    }

    @Test
    void reportsOnlyBalancesBelowAConfiguredMinimum() {
        List<LowStockRow> rows = service.lowStock(TENANT_ID, null, null, null);

        assertThat(rows).extracting(LowStockRow::getMaterialId)
            .containsExactly(BELOW_MIN_MATERIAL_ID);
    }

    @Test
    void doesNotTreatAMissingMinimumAsZero() {
        List<LowStockRow> rows = service.lowStock(TENANT_ID, null, null, null);

        assertThat(rows).extracting(LowStockRow::getMaterialId)
            .doesNotContain(NO_MIN_ZERO_QTY_MATERIAL_ID);
    }

    @Test
    void computesShortfallAsMinimumMinusQuantity() {
        LowStockRow row = service.lowStock(TENANT_ID, BRANCH_ID, WAREHOUSE_ID, CATEGORY_ID)
            .getFirst();

        assertThat(row.getQuantity()).isEqualTo("2.000000");
        assertThat(row.getMinQuantity()).isEqualTo("5.000000");
        assertThat(row.getShortfall()).isEqualTo("3.000000");
        assertThat(row.getMaterialName()).isEqualTo("Tomato");
        assertThat(row.getCategoryNameAr()).isEqualTo("خضروات");
    }

    private void insertWarehouse(Long id, String code, String name, boolean active) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, BRANCH_ID, code, name, active);
    }

    private void insertMaterial(Long id, String code, String name, boolean active) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name, active);
    }

    private void insertBalance(Long id, Long warehouseId, Long materialId,
                               String quantity, String minimumQuantity) {
        jdbcTemplate.update("""
            INSERT INTO stock_balance (id, tenant_id, warehouse_id, material_id, uom_id,
                                       quantity, minimum_quantity, average_cost, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), 1, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, warehouseId, materialId, UOM_ID, quantity, minimumQuantity);
    }
}
