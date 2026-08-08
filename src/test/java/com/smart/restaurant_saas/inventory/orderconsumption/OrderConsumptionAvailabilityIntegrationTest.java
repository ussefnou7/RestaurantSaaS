package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.StockBalanceService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Which doc states count towards the outstanding-consumption figure the stock screen subtracts.
 *
 * <p>PARTIAL and CONFLICT both do: in each, the food was sold, it left the kitchen, and its
 * consumption has not posted. They differ only in why the posting is delayed. IN_PROGRESS does
 * not — its rows are mid-mutation. PENDING does, through the recipe expansion, because its
 * material rows do not exist yet.
 *
 * <p>Each test seeds only the docs it needs, so no other doc contributes to the figure it asserts.
 */
@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionAvailabilityIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long BRANCH_ID = 994_011L;
    private static final Long UOM_ID = 994_021L;
    private static final Long CATEGORY_ID = 994_031L;
    private static final Long WAREHOUSE_ID = 994_041L;
    private static final Long ALPHA_ID = 994_051L;
    private static final Long BETA_ID = 994_052L;
    private static final Long MENU_CATEGORY_ID = 994_061L;
    private static final Long PRODUCT_ID = 994_071L;
    private static final Long RECIPE_ID = 994_081L;
    private static final Long ORDER_ID = 994_091L;
    private static final Long FIRST_ORDER_LINE_ID = 994_101L;
    private static final Long SECOND_ORDER_LINE_ID = 994_102L;
    private static final Long USER_ID = 994_901L;

    /** One unit of the product needs 2 KG of Alpha and 3 KG of Beta. */
    private static final String ALPHA_PER_UNIT = "2.000000";
    private static final String BETA_PER_UNIT = "3.000000";

    private final Set<Long> failingMaterialIds = new HashSet<>();

    @Autowired
    private OrderConsumptionService service;
    @Autowired
    private OrderConsumptionAvailabilityService availabilityService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @MockitoSpyBean
    private InventoryLedgerService ledgerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedMasterData() {
        doThrow(new IllegalStateException("injected ledger failure"))
            .when(ledgerService)
            .record(argThat(command -> command != null
                && command.getTransactionType() == InventoryTransactionType.CONSUMPTION_SUMMARY
                && failingMaterialIds.contains(command.getMaterialId())));

        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Order Consumption Availability Tenant', 'OC_AVAIL', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Availability Branch', 'OCA-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'OCA-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'OCA-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'OCA-WH-1', 'Availability Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        insertMaterial(ALPHA_ID, "OCA-ALPHA", "Alpha");
        insertMaterial(BETA_ID, "OCA-BETA", "Beta");

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Availability Menu', 0, TRUE, CURRENT_TIMESTAMP)
            """, MENU_CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO product
                (id, tenant_id, name, selling_price, is_active, is_menu, menu_category_id, created_at)
            VALUES (?, ?, 'Availability Dish', 10, TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            """, PRODUCT_ID, TENANT_ID, MENU_CATEGORY_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, RECIPE_ID, TENANT_ID, PRODUCT_ID);
        insertRecipeItem(ALPHA_ID, ALPHA_PER_UNIT);
        insertRecipeItem(BETA_ID, BETA_PER_UNIT);

        jdbcTemplate.update("""
            INSERT INTO orders
                (id, tenant_id, order_type, order_source, status, payment_method,
                 branch_id, warehouse_id, subtotal, tax_amount, total_amount, order_date, created_at)
            VALUES (?, ?, 'TAKEAWAY', 'POS', 'COMPLETE', 'CASH', ?, ?, 20, 0, 20,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, ORDER_ID, TENANT_ID, BRANCH_ID, WAREHOUSE_ID);
        insertOrderLine(FIRST_ORDER_LINE_ID);
        insertOrderLine(SECOND_ORDER_LINE_ID);
    }

    @AfterEach
    void cleanup() {
        failingMaterialIds.clear();
        jdbcTemplate.update("DELETE FROM order_consumption_material WHERE doc_id IN (SELECT id FROM order_consumption WHERE tenant_id = ?)", TENANT_ID);
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
    void conflictDocUnconsumedMaterialIsSubtractedFromTheDisplayedQuantity() {
        purchase(ALPHA_ID, "10.000000");
        purchase(BETA_ID, "10.000000");
        failingMaterialIds.add(ALPHA_ID);

        Long docId = processNewDoc(994_201L, 994_301L, FIRST_ORDER_LINE_ID);

        assertThat(docStatus(docId)).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertThat(materialConsumed(docId, ALPHA_ID)).isFalse();

        // Alpha never left the ledger, but it did leave the kitchen — 2 KG of the 10 on the shelf
        // are already sold, so the screen must not offer them.
        assertThat(outstanding().get(ALPHA_ID)).isEqualByComparingTo(ALPHA_PER_UNIT);
        assertThat(balanceQuantity(ALPHA_ID)).isEqualByComparingTo("10.000000");
        assertThat(displayedQuantity(ALPHA_ID)).isEqualByComparingTo("8.000000");
    }

    @Test
    void conflictDocConsumedMaterialIsNotSubtracted() {
        purchase(ALPHA_ID, "10.000000");
        purchase(BETA_ID, "10.000000");
        failingMaterialIds.add(ALPHA_ID);

        Long docId = processNewDoc(994_201L, 994_301L, FIRST_ORDER_LINE_ID);

        // Beta posted inside the same CONFLICT doc. Its 3 KG already came off the balance, so
        // counting it again would subtract the same stock twice — the PARTIAL guard, unchanged.
        assertThat(docStatus(docId)).isEqualTo(OrderConsumptionStatus.CONFLICT);
        assertThat(materialConsumed(docId, BETA_ID)).isTrue();
        assertThat(outstanding()).doesNotContainKey(BETA_ID);
        assertThat(balanceQuantity(BETA_ID)).isEqualByComparingTo("7.000000");
        assertThat(displayedQuantity(BETA_ID)).isEqualByComparingTo("7.000000");
    }

    @Test
    void inProgressDocIsExcludedWhileItsRowsAreMidMutation() {
        purchase(ALPHA_ID, "10.000000");
        // The state cannot be observed from outside the processing transaction, so it is staged:
        // a claimed doc whose material rows are written but whose consumed flags are not settled.
        insertDoc(994_203L, OrderConsumptionStatus.IN_PROGRESS);
        insertDocLine(994_303L, 994_203L, FIRST_ORDER_LINE_ID);
        insertMaterialRow(994_203L, ALPHA_ID, ALPHA_PER_UNIT);

        assertThat(outstanding()).isEmpty();
        assertThat(displayedQuantity(ALPHA_ID)).isEqualByComparingTo("10.000000");
    }

    @Test
    void pendingDocStillComesFromTheRecipeExpansion() {
        purchase(ALPHA_ID, "10.000000");
        purchase(BETA_ID, "10.000000");
        insertDoc(994_204L, OrderConsumptionStatus.PENDING);
        insertDocLine(994_304L, 994_204L, FIRST_ORDER_LINE_ID);

        // No material rows exist yet — the figure is re-derived from the line's recipe.
        assertThat(materialRowCount(994_204L)).isZero();
        assertThat(outstanding()).containsOnlyKeys(ALPHA_ID, BETA_ID);
        assertThat(outstanding().get(ALPHA_ID)).isEqualByComparingTo(ALPHA_PER_UNIT);
        assertThat(outstanding().get(BETA_ID)).isEqualByComparingTo(BETA_PER_UNIT);
        assertThat(displayedQuantity(ALPHA_ID)).isEqualByComparingTo("8.000000");
        assertThat(displayedQuantity(BETA_ID)).isEqualByComparingTo("7.000000");
    }

    @Test
    void partialAndConflictDocsInOneWarehouseBothCountAndSumPerMaterial() {
        // First doc: no stock at all, so both materials are short and the doc lands PARTIAL.
        Long partialDocId = processNewDoc(994_202L, 994_302L, FIRST_ORDER_LINE_ID);
        assertThat(docStatus(partialDocId)).isEqualTo(OrderConsumptionStatus.PARTIAL);

        // Second doc: stock has arrived, but Alpha's ledger write fails, so the doc lands CONFLICT
        // with Alpha unconsumed and Beta posted.
        purchase(ALPHA_ID, "10.000000");
        purchase(BETA_ID, "10.000000");
        failingMaterialIds.add(ALPHA_ID);
        Long conflictDocId = processNewDoc(994_201L, 994_301L, SECOND_ORDER_LINE_ID);
        assertThat(docStatus(conflictDocId)).isEqualTo(OrderConsumptionStatus.CONFLICT);

        // Alpha is outstanding on both docs and must sum, not overwrite: 2 + 2.
        assertThat(outstanding().get(ALPHA_ID)).isEqualByComparingTo("4.000000");
        // Beta is outstanding on the PARTIAL doc only — the CONFLICT doc consumed it.
        assertThat(outstanding().get(BETA_ID)).isEqualByComparingTo(BETA_PER_UNIT);
        assertThat(outstanding()).containsOnlyKeys(ALPHA_ID, BETA_ID);

        assertThat(displayedQuantity(ALPHA_ID)).isEqualByComparingTo("6.000000");
        assertThat(displayedQuantity(BETA_ID)).isEqualByComparingTo("4.000000");
    }

    /** Inserts a PENDING doc for one order line and runs it through the real claim/process pair. */
    private Long processNewDoc(Long docId, Long docLineId, Long orderLineId) {
        insertDoc(docId, OrderConsumptionStatus.PENDING);
        insertDocLine(docLineId, docId, orderLineId);
        assertThat(service.claimDoc(docId, USER_ID)).isTrue();
        service.processClaimedDoc(docId, USER_ID);
        return docId;
    }

    private Map<Long, BigDecimal> outstanding() {
        return availabilityService.findOutstandingDisplayQuantitiesByMaterial(TENANT_ID, WAREHOUSE_ID);
    }

    /** What the stock screen renders: the balance less everything sold but not yet posted. */
    private BigDecimal displayedQuantity(Long materialId) {
        return stockBalanceService.findByWarehouseAndMaterial(TENANT_ID, WAREHOUSE_ID, materialId)
            .getQuantity();
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

    private void insertMaterial(Long materialId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material
                (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                 code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, materialId, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name);
    }

    private void insertRecipeItem(Long materialId, String quantity) {
        jdbcTemplate.update("""
            INSERT INTO recipe_item
                (tenant_id, recipe_id, material_id, quantity, uom_id, created_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, TENANT_ID, RECIPE_ID, materialId, new BigDecimal(quantity), UOM_ID);
    }

    private void insertOrderLine(Long lineId) {
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 10, 10, CURRENT_TIMESTAMP)
            """, lineId, TENANT_ID, ORDER_ID, PRODUCT_ID, RECIPE_ID);
    }

    private void insertDoc(Long docId, OrderConsumptionStatus status) {
        jdbcTemplate.update("""
            INSERT INTO order_consumption
                (id, tenant_id, warehouse_id, status, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, docId, TENANT_ID, WAREHOUSE_ID, status.name());
    }

    private void insertDocLine(Long id, Long docId, Long orderLineId) {
        jdbcTemplate.update("""
            INSERT INTO order_consumption_line
                (id, doc_id, order_line_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, id, docId, orderLineId);
    }

    private void insertMaterialRow(Long docId, Long materialId, String requiredQuantity) {
        jdbcTemplate.update("""
            INSERT INTO order_consumption_material
                (doc_id, material_id, required_quantity, required_uom_id,
                 entered_quantity, entered_uom_id, is_consumed, created_at)
            VALUES (?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP)
            """, docId, materialId, new BigDecimal(requiredQuantity), UOM_ID,
            new BigDecimal(requiredQuantity), UOM_ID);
    }

    private OrderConsumptionStatus docStatus(Long docId) {
        return OrderConsumptionStatus.valueOf(jdbcTemplate.queryForObject(
            "SELECT status FROM order_consumption WHERE id = ?", String.class, docId));
    }

    private boolean materialConsumed(Long docId, Long materialId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT is_consumed
            FROM order_consumption_material
            WHERE doc_id = ? AND material_id = ?
            """, Boolean.class, docId, materialId));
    }

    private int materialRowCount(Long docId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_consumption_material WHERE doc_id = ?", Integer.class, docId);
    }

    private BigDecimal balanceQuantity(Long materialId) {
        return jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM stock_balance
            WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, materialId);
    }
}
