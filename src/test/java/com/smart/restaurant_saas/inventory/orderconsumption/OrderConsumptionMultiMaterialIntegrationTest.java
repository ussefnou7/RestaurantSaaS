package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.StockBalanceService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The defect this feature exists to close: <b>one product whose recipe needs two materials, one of
 * them short</b>.
 *
 * <p>Consumption runs per material, but the outcome used to be stored per line. Chicken consumed,
 * bread did not, and the single line stayed unconsumed — so the availability figure re-expanded
 * that line back into both materials and subtracted chicken's full 15 KG a second time from a
 * balance that had already given it up. The stock screen showed -8 for a material whose open
 * batches summed to 7.
 */
@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionMultiMaterialIntegrationTest {

    private static final Long TENANT_ID = 997_001L;
    private static final Long BRANCH_ID = 997_101L;
    private static final Long UOM_ID = 997_201L;
    private static final Long CATEGORY_ID = 997_301L;
    private static final Long WAREHOUSE_ID = 997_401L;
    private static final Long CHICKEN_ID = 997_501L;
    private static final Long BREAD_ID = 997_502L;
    private static final Long MENU_CATEGORY_ID = 997_601L;
    private static final Long PRODUCT_ID = 997_701L;
    private static final Long RECIPE_ID = 997_801L;
    private static final Long ORDER_ID = 997_901L;
    private static final Long ORDER_LINE_ID = 998_001L;
    private static final Long DOC_ID = 998_101L;
    private static final Long DOC_LINE_ID = 998_201L;
    private static final Long USER_ID = 998_301L;

    /** One shawarma needs 15 KG of chicken and 3 KG of bread. */
    private static final String CHICKEN_PER_UNIT = "15.000000";
    private static final String BREAD_PER_UNIT = "3.000000";

    @Autowired
    private OrderConsumptionService service;
    @Autowired
    private OrderConsumptionAvailabilityService availabilityService;
    @Autowired
    private InventoryLedgerService ledgerService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Order Consumption Multi Material Tenant', 'OC_MULTI', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Multi Material Branch', 'OCM-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'OCM-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'OCM-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'OCM-WH-1', 'Multi Material Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        insertMaterial(CHICKEN_ID, "OCM-CHICKEN", "Chicken");
        insertMaterial(BREAD_ID, "OCM-BREAD", "Bread");

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Multi Material Menu', 0, TRUE, CURRENT_TIMESTAMP)
            """, MENU_CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO product
                (id, tenant_id, name, selling_price, is_active, is_menu, menu_category_id, created_at)
            VALUES (?, ?, 'Shawarma', 10, TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            """, PRODUCT_ID, TENANT_ID, MENU_CATEGORY_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, RECIPE_ID, TENANT_ID, PRODUCT_ID);
        // One recipe, two materials — the shape the line-level flag could not represent.
        insertRecipeItem(CHICKEN_ID, CHICKEN_PER_UNIT);
        insertRecipeItem(BREAD_ID, BREAD_PER_UNIT);

        jdbcTemplate.update("""
            INSERT INTO orders
                (id, tenant_id, order_type, order_source, status, payment_method,
                 branch_id, warehouse_id, subtotal, tax_amount, total_amount, order_date, created_at)
            VALUES (?, ?, 'TAKEAWAY', 'POS', 'COMPLETE', 'CASH', ?, ?, 10, 0, 10,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, ORDER_ID, TENANT_ID, BRANCH_ID, WAREHOUSE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 10, 10, CURRENT_TIMESTAMP)
            """, ORDER_LINE_ID, TENANT_ID, ORDER_ID, PRODUCT_ID, RECIPE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_consumption
                (id, tenant_id, warehouse_id, status, created_at)
            VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
            """, DOC_ID, TENANT_ID, WAREHOUSE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_consumption_line
                (id, doc_id, order_line_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, DOC_LINE_ID, DOC_ID, ORDER_LINE_ID);
    }

    @AfterEach
    void cleanup() {
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
    void consumedMaterialOfAPartiallyConsumedLineIsNotSubtractedTwice() {
        purchase(CHICKEN_ID, "22.000000");
        // No bread at all, so the line's second material cannot consume.

        process();

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.PARTIAL);
        assertThat(materialConsumed(CHICKEN_ID)).isTrue();
        assertThat(materialConsumed(BREAD_ID)).isFalse();

        // Chicken really left the warehouse: 22 - 15 = 7, in the balance and in the batches.
        assertThat(balanceQuantity(CHICKEN_ID)).isEqualByComparingTo("7.000000");
        assertThat(openBatchQuantity(CHICKEN_ID)).isEqualByComparingTo("7.000000");

        // The defect: the screen used to render 7 - 15 = -8 here, because the unconsumed line put
        // chicken back into the outstanding total. The displayed figure must equal the batch sum.
        assertThat(displayedQuantity(CHICKEN_ID)).isEqualByComparingTo("7.000000");

        // Bread never moved, and is still outstanding — 0 on hand less the 3 KG already sold.
        assertThat(balanceQuantity(BREAD_ID)).isNull();
        assertThat(outstanding()).containsOnlyKeys(BREAD_ID);
        assertThat(outstanding().get(BREAD_ID)).isEqualByComparingTo(BREAD_PER_UNIT);

        assertStatusMatchesRows();
    }

    @Test
    void recalculateAfterTheCoveringInvoicePostsOneLedgerRowPerMaterial() {
        purchase(CHICKEN_ID, "22.000000");
        process();
        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.PARTIAL);

        purchase(BREAD_ID, "10.000000");
        service.recalculate(DOC_ID, TENANT_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
        assertThat(materialConsumed(CHICKEN_ID)).isTrue();
        assertThat(materialConsumed(BREAD_ID)).isTrue();
        // The per-material idempotency key kept chicken from posting a second time.
        assertThat(ledgerRows(CHICKEN_ID)).isEqualTo(1);
        assertThat(ledgerRows(BREAD_ID)).isEqualTo(1);
        assertThat(balanceQuantity(CHICKEN_ID)).isEqualByComparingTo("7.000000");
        assertThat(balanceQuantity(BREAD_ID)).isEqualByComparingTo("7.000000");
        // A POSTED doc is outstanding for nothing, so both screens show the real balance.
        assertThat(outstanding()).isEmpty();
        assertThat(displayedQuantity(CHICKEN_ID)).isEqualByComparingTo("7.000000");
        assertThat(displayedQuantity(BREAD_ID)).isEqualByComparingTo("7.000000");

        assertStatusMatchesRows();
    }

    @Test
    void availabilityIsUnchangedByTheMoveFromRecipeExpansionToMaterialRows() {
        // No stock for either material, so processing writes the rows and consumes nothing. The
        // requirement is identical on both sides of the transition; only the source changes.
        Map<Long, BigDecimal> whilePending = outstanding();
        assertThat(whilePending).containsOnlyKeys(CHICKEN_ID, BREAD_ID);

        process();

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.PARTIAL);
        Map<Long, BigDecimal> fromMaterialRows = outstanding();

        assertThat(fromMaterialRows).containsOnlyKeys(whilePending.keySet().toArray(Long[]::new));
        whilePending.forEach((materialId, expected) ->
            assertThat(fromMaterialRows.get(materialId))
                .as("outstanding quantity for material %s", materialId)
                .isEqualByComparingTo(expected));
    }

    @Test
    void partialAvailabilitySubtractsOnlyTheUnconsumedMaterials() {
        purchase(CHICKEN_ID, "22.000000");

        process();

        // Chicken consumed, so it contributes nothing; only bread is still owed.
        assertThat(outstanding()).containsOnlyKeys(BREAD_ID);
        assertThat(outstanding().get(BREAD_ID)).isEqualByComparingTo(BREAD_PER_UNIT);
    }

    /** D29 runs through the real two-transaction claim/process pair, as the scheduler does. */
    private void process() {
        assertThat(service.claimDoc(DOC_ID, USER_ID)).isTrue();
        service.processClaimedDoc(DOC_ID, USER_ID);
    }

    /**
     * The doc's stored status must always be what its rows say — never a value left over from an
     * earlier run.
     */
    private void assertStatusMatchesRows() {
        boolean anyTechnical = !jdbcTemplate.queryForList("""
            SELECT 1 FROM order_consumption_material
            WHERE doc_id = ? AND failure_reason = 'TECHNICAL_FAILURE'
            """, DOC_ID).isEmpty();
        boolean anyOutstanding = !jdbcTemplate.queryForList("""
            SELECT 1 FROM order_consumption_material
            WHERE doc_id = ? AND is_consumed = FALSE
            """, DOC_ID).isEmpty();
        OrderConsumptionStatus derived = anyTechnical
            ? OrderConsumptionStatus.CONFLICT
            : anyOutstanding ? OrderConsumptionStatus.PARTIAL : OrderConsumptionStatus.POSTED;
        assertThat(docStatus()).isEqualTo(derived);
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

    private OrderConsumptionStatus docStatus() {
        return OrderConsumptionStatus.valueOf(jdbcTemplate.queryForObject(
            "SELECT status FROM order_consumption WHERE id = ?", String.class, DOC_ID));
    }

    private boolean materialConsumed(Long materialId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT is_consumed
            FROM order_consumption_material
            WHERE doc_id = ? AND material_id = ?
            """, Boolean.class, DOC_ID, materialId));
    }

    private int ledgerRows(Long materialId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM inventory_transaction
            WHERE tenant_id = ?
              AND reference_type = 'ORDER_CONSUMPTION_DOC'
              AND reference_id = ?
              AND material_id = ?
            """, Integer.class, TENANT_ID, DOC_ID, materialId);
    }

    private BigDecimal balanceQuantity(Long materialId) {
        List<BigDecimal> rows = jdbcTemplate.queryForList("""
            SELECT quantity
            FROM stock_balance
            WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, materialId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BigDecimal openBatchQuantity(Long materialId) {
        return jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(batch.remaining_quantity), 0)
            FROM stock_batch batch
            JOIN stock_balance balance ON balance.id = batch.stock_balance_id
            WHERE balance.tenant_id = ?
              AND balance.warehouse_id = ?
              AND balance.material_id = ?
              AND batch.status = 'OPEN'
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, materialId);
    }
}
