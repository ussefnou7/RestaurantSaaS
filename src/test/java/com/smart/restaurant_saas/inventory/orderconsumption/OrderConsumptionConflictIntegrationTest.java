package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionConflictIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long BRANCH_ID = 996_101L;
    private static final Long UOM_ID = 996_201L;
    private static final Long CATEGORY_ID = 996_301L;
    private static final Long WAREHOUSE_ID = 996_401L;
    private static final Long MENU_CATEGORY_ID = 996_601L;
    private static final Long ORDER_ID = 996_901L;
    private static final Long DOC_ID = 997_101L;
    private static final Long USER_ID = 997_301L;
    private static final List<Long> MATERIAL_IDS = List.of(996_501L, 996_502L, 996_503L, 996_504L);
    private static final List<Long> PRODUCT_IDS = List.of(996_701L, 996_702L, 996_703L, 996_704L);
    private static final List<Long> RECIPE_IDS = List.of(996_801L, 996_802L, 996_803L, 996_804L);
    private static final List<Long> ORDER_LINE_IDS = List.of(997_001L, 997_002L, 997_003L, 997_004L);
    private static final List<Long> DOC_LINE_IDS = List.of(997_201L, 997_202L, 997_203L, 997_204L);

    private final Set<Long> failingMaterialIds = new HashSet<>();

    @Autowired
    private OrderConsumptionService service;
    @MockitoSpyBean
    private InventoryLedgerService ledgerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedDocument() {
        doThrow(new IllegalStateException("injected ledger failure"))
            .when(ledgerService)
            .record(argThat(command -> command != null
                && command.getTransactionType() == InventoryTransactionType.CONSUMPTION_SUMMARY
                && failingMaterialIds.contains(command.getMaterialId())));

        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, 'Order Consumption Conflict Tenant', 'OC_CONFLICT', 'ACTIVE', CURRENT_TIMESTAMP)
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Conflict Branch', 'OCC-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'OCC-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'OCC-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'OCC-WH-1', 'Conflict Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Conflict Menu', 0, TRUE, CURRENT_TIMESTAMP)
            """, MENU_CATEGORY_ID, TENANT_ID);

        for (int index = 0; index < MATERIAL_IDS.size(); index++) {
            insertMaterial(index);
            insertProductAndRecipe(index);
        }
        insertOrder();
        for (int index = 0; index < MATERIAL_IDS.size(); index++) {
            insertOrderLine(index);
        }
        jdbcTemplate.update("""
            INSERT INTO order_consumption
                (id, tenant_id, warehouse_id, status, created_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)
            """, DOC_ID, TENANT_ID, WAREHOUSE_ID);
        for (int index = 0; index < MATERIAL_IDS.size(); index++) {
            jdbcTemplate.update("""
                INSERT INTO order_consumption_line
                    (id, doc_id, order_line_id, is_consumed, created_at)
                VALUES (?, ?, ?, FALSE, CURRENT_TIMESTAMP)
                """, DOC_LINE_IDS.get(index), DOC_ID, ORDER_LINE_IDS.get(index));
        }
    }

    @AfterEach
    void cleanup() {
        failingMaterialIds.clear();
        jdbcTemplate.update("DELETE FROM order_consumption_line WHERE doc_id IN (SELECT id FROM order_consumption WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM order_consumption WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM order_line WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM stock_batch WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM stock_balance WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM recipe_item WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM recipe WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM product WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM menu_category WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM material WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM warehouse WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM material_category WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM uom WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
    }

    @Test
    void technicalFailureMarksCommittedOutcomesAndRetryDoesNotDoublePost() {
        purchaseAll("10.000000");
        failingMaterialIds.add(MATERIAL_IDS.get(2));

        service.processClaimedDoc(DOC_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertLineOutcomes(true, true, false, true);
        assertConsumptionRows(1, 1, 0, 1);

        failingMaterialIds.clear();
        service.recalculate(DOC_ID, TENANT_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
        assertLineOutcomes(true, true, true, true);
        assertConsumptionRows(1, 1, 1, 1);
    }

    @Test
    void everyTechnicalFailureLeavesEveryLineUnconsumed() {
        purchaseAll("10.000000");
        failingMaterialIds.addAll(MATERIAL_IDS);

        service.processClaimedDoc(DOC_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertLineOutcomes(false, false, false, false);
        assertConsumptionRows(0, 0, 0, 0);
    }

    @Test
    void shortfallAndTechnicalFailureMarkEachLineByItsOutcome() {
        purchase(MATERIAL_IDS.get(0), "2.000000");
        for (int index = 1; index < MATERIAL_IDS.size(); index++) {
            purchase(MATERIAL_IDS.get(index), "10.000000");
        }
        failingMaterialIds.add(MATERIAL_IDS.get(2));

        service.processClaimedDoc(DOC_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertLineOutcomes(false, true, false, true);
        assertConsumptionRows(0, 1, 0, 1);
    }

    private void purchaseAll(String quantity) {
        MATERIAL_IDS.forEach(materialId -> purchase(materialId, quantity));
    }

    private void purchase(Long materialId, String quantity) {
        ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(materialId)
            .transactionType(InventoryTransactionType.PURCHASE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(new BigDecimal(quantity))
            .enteredUomId(UOM_ID)
            .enteredUnitCost(new BigDecimal("2.000000"))
            .movementDate(LocalDateTime.now())
            .createdBy(USER_ID)
            .build());
    }

    private void insertMaterial(int index) {
        jdbcTemplate.update("""
            INSERT INTO material
                (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                 code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_IDS.get(index), TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID,
            "OCC-MAT-" + (index + 1), "Material " + (index + 1));
    }

    private void insertProductAndRecipe(int index) {
        jdbcTemplate.update("""
            INSERT INTO product
                (id, tenant_id, name, selling_price, is_active, is_menu, menu_category_id, created_at)
            VALUES (?, ?, ?, 10, TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            """, PRODUCT_IDS.get(index), TENANT_ID, "Dish " + (index + 1), MENU_CATEGORY_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, RECIPE_IDS.get(index), TENANT_ID, PRODUCT_IDS.get(index));
        jdbcTemplate.update("""
            INSERT INTO recipe_item
                (tenant_id, recipe_id, material_id, quantity, uom_id, created_at)
            VALUES (?, ?, ?, 5, ?, CURRENT_TIMESTAMP)
            """, TENANT_ID, RECIPE_IDS.get(index), MATERIAL_IDS.get(index), UOM_ID);
    }

    private void insertOrder() {
        jdbcTemplate.update("""
            INSERT INTO orders
                (id, tenant_id, order_type, order_source, status, payment_method,
                 branch_id, warehouse_id, subtotal, tax_amount, total_amount, order_date, created_at)
            VALUES (?, ?, 'TAKEAWAY', 'POS', 'COMPLETE', 'CASH', ?, ?, 40, 0, 40,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, ORDER_ID, TENANT_ID, BRANCH_ID, WAREHOUSE_ID);
    }

    private void insertOrderLine(int index) {
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 10, 10, CURRENT_TIMESTAMP)
            """, ORDER_LINE_IDS.get(index), TENANT_ID, ORDER_ID,
            PRODUCT_IDS.get(index), RECIPE_IDS.get(index));
    }

    private OrderConsumptionStatus docStatus() {
        return OrderConsumptionStatus.valueOf(jdbcTemplate.queryForObject(
            "SELECT status FROM order_consumption WHERE id = ?", String.class, DOC_ID));
    }

    private void assertLineOutcomes(boolean... expected) {
        for (int index = 0; index < expected.length; index++) {
            Boolean consumed = jdbcTemplate.queryForObject(
                "SELECT is_consumed FROM order_consumption_line WHERE order_line_id = ?",
                Boolean.class, ORDER_LINE_IDS.get(index));
            assertThat(consumed).as("material %s line outcome", index + 1).isEqualTo(expected[index]);
        }
    }

    private void assertConsumptionRows(int... expected) {
        for (int index = 0; index < expected.length; index++) {
            Integer rowCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_transaction
                WHERE tenant_id = ?
                  AND reference_type = 'ORDER_CONSUMPTION_DOC'
                  AND reference_id = ?
                  AND material_id = ?
                """, Integer.class, TENANT_ID, DOC_ID, MATERIAL_IDS.get(index));
            assertThat(rowCount).as("material %s committed ledger rows", index + 1)
                .isEqualTo(expected[index]);
        }
    }
}
