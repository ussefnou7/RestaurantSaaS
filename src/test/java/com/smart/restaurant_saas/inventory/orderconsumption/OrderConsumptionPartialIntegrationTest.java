package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.StockBalanceService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.order.core.Order;
import com.smart.restaurant_saas.order.core.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionPartialIntegrationTest {

    private static final Long TENANT_ID = 998_001L;
    private static final Long BRANCH_ID = 998_101L;
    private static final Long UOM_ID = 998_201L;
    private static final Long CATEGORY_ID = 998_301L;
    private static final Long WAREHOUSE_ID = 998_401L;
    private static final Long AVAILABLE_MATERIAL_ID = 998_501L;
    private static final Long SHORT_MATERIAL_ID = 998_502L;
    private static final Long MENU_CATEGORY_ID = 998_601L;
    private static final Long AVAILABLE_PRODUCT_ID = 998_701L;
    private static final Long SHORT_PRODUCT_ID = 998_702L;
    private static final Long AVAILABLE_RECIPE_ID = 998_801L;
    private static final Long SHORT_RECIPE_ID = 998_802L;
    private static final Long ORDER_ID = 998_901L;
    private static final Long AVAILABLE_ORDER_LINE_ID = 999_001L;
    private static final Long SHORT_ORDER_LINE_ID = 999_002L;
    private static final Long DOC_ID = 999_101L;
    private static final Long USER_ID = 999_301L;

    @Autowired
    private OrderConsumptionService service;
    @Autowired
    private InventoryLedgerService ledgerService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedDocument() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, 'Order Consumption Partial Tenant', 'OC_PARTIAL', 'ACTIVE', CURRENT_TIMESTAMP)
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Consumption Branch', 'OC-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'OC-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'OC-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'OC-WH-1', 'Consumption Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        insertMaterial(AVAILABLE_MATERIAL_ID, "OC-FLOUR", "Flour");
        insertMaterial(SHORT_MATERIAL_ID, "OC-OIL", "Oil");

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Consumption Menu', 0, TRUE, CURRENT_TIMESTAMP)
            """, MENU_CATEGORY_ID, TENANT_ID);
        insertProductAndRecipe(AVAILABLE_PRODUCT_ID, AVAILABLE_RECIPE_ID, AVAILABLE_MATERIAL_ID, "Flour Dish");
        insertProductAndRecipe(SHORT_PRODUCT_ID, SHORT_RECIPE_ID, SHORT_MATERIAL_ID, "Oil Dish");

        insertOrder(ORDER_ID);
        insertOrderLine(AVAILABLE_ORDER_LINE_ID, ORDER_ID, AVAILABLE_PRODUCT_ID, AVAILABLE_RECIPE_ID);
        insertOrderLine(SHORT_ORDER_LINE_ID, ORDER_ID, SHORT_PRODUCT_ID, SHORT_RECIPE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_consumption
                (id, tenant_id, warehouse_id, status, created_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)
            """, DOC_ID, TENANT_ID, WAREHOUSE_ID);
        insertDocLine(999_201L, AVAILABLE_ORDER_LINE_ID);
        insertDocLine(999_202L, SHORT_ORDER_LINE_ID);
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
    void oneShortMaterialStaysUntouchedAndRetryPostsOnlyOutstandingMaterial() {
        purchase(AVAILABLE_MATERIAL_ID, "10.000000", "2.000000");
        purchase(SHORT_MATERIAL_ID, "2.000000", "3.000000");

        service.processClaimedDoc(DOC_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.PARTIAL);
        assertThat(materialConsumed(AVAILABLE_MATERIAL_ID)).isTrue();
        assertThat(materialConsumed(SHORT_MATERIAL_ID)).isFalse();
        assertThat(consumptionRows(AVAILABLE_MATERIAL_ID)).isEqualTo(1);
        assertThat(consumptionRows(SHORT_MATERIAL_ID)).isZero();
        assertThat(balanceQuantity(AVAILABLE_MATERIAL_ID)).isEqualByComparingTo("5.000000");
        assertThat(balanceQuantity(SHORT_MATERIAL_ID)).isEqualByComparingTo("2.000000");
        assertThat(openBatchQuantity(SHORT_MATERIAL_ID)).isEqualByComparingTo("2.000000");
        assertThat(zeroCostConsumptionRows()).isZero();

        OrderConsumptionDocDetailResponse detail = service.getById(DOC_ID, TENANT_ID);
        assertThat(detail.getMaterials()).hasSize(2);
        assertThat(detail.getMaterials())
            .filteredOn(material -> !material.isConsumed())
            .singleElement()
            .satisfies(material -> {
                assertThat(material.getMaterialId()).isEqualTo(SHORT_MATERIAL_ID);
                assertThat(material.getMaterialName()).isEqualTo("Oil");
                assertThat(material.getRequiredQuantity()).isEqualByComparingTo("5.000000");
                assertThat(material.getAvailableQuantity()).isEqualByComparingTo("2.000000");
                assertThat(material.getUomId()).isEqualTo(UOM_ID);
                assertThat(material.getUomSymbol()).isEqualTo("kg");
                assertThat(material.getFailureReason())
                    .isEqualTo(OrderConsumptionFailureReason.INSUFFICIENT_STOCK);
                assertThat(material.getExceptionClass()).isNull();
            });
        assertThat(stockBalanceService.findByWarehouseAndMaterial(
            TENANT_ID, WAREHOUSE_ID, AVAILABLE_MATERIAL_ID).getQuantity())
            .isEqualByComparingTo("5.000000");
        assertThat(stockBalanceService.findByWarehouseAndMaterial(
            TENANT_ID, WAREHOUSE_ID, SHORT_MATERIAL_ID).getQuantity())
            .isEqualByComparingTo("-3.000000");

        assertPartialDocIsClosedToNewOrderLines();

        purchase(SHORT_MATERIAL_ID, "3.000000", "4.000000");
        service.recalculate(DOC_ID, TENANT_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
        assertThat(materialConsumed(AVAILABLE_MATERIAL_ID)).isTrue();
        assertThat(materialConsumed(SHORT_MATERIAL_ID)).isTrue();
        assertThat(consumptionRows(AVAILABLE_MATERIAL_ID)).isEqualTo(1);
        assertThat(consumptionRows(SHORT_MATERIAL_ID)).isEqualTo(1);
        assertThat(balanceQuantity(AVAILABLE_MATERIAL_ID)).isEqualByComparingTo("5.000000");
        assertThat(balanceQuantity(SHORT_MATERIAL_ID)).isEqualByComparingTo("0.000000");
        assertThat(zeroCostConsumptionRows()).isZero();
    }

    @Test
    void sufficientStockPostsEveryMaterialAndKeepsBalancesNonNegative() {
        purchase(AVAILABLE_MATERIAL_ID, "10.000000", "2.000000");
        purchase(SHORT_MATERIAL_ID, "10.000000", "3.000000");

        service.processClaimedDoc(DOC_ID, USER_ID);

        assertThat(docStatus()).isEqualTo(OrderConsumptionStatus.POSTED);
        assertThat(materialConsumed(AVAILABLE_MATERIAL_ID)).isTrue();
        assertThat(materialConsumed(SHORT_MATERIAL_ID)).isTrue();
        assertThat(consumptionRows(AVAILABLE_MATERIAL_ID)).isEqualTo(1);
        assertThat(consumptionRows(SHORT_MATERIAL_ID)).isEqualTo(1);
        assertThat(balanceQuantity(AVAILABLE_MATERIAL_ID)).isEqualByComparingTo("5.000000");
        assertThat(balanceQuantity(SHORT_MATERIAL_ID)).isEqualByComparingTo("5.000000");
        assertThat(zeroCostConsumptionRows()).isZero();
    }

    private void assertPartialDocIsClosedToNewOrderLines() {
        Long newOrderId = 998_902L;
        Long newOrderLineId = 999_003L;
        insertOrder(newOrderId);
        insertOrderLine(newOrderLineId, newOrderId, SHORT_PRODUCT_ID, SHORT_RECIPE_ID);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Order order = orderRepository.findById(newOrderId).orElseThrow();
            service.recordCompletedOrder(order, USER_ID);
        });

        Long attachedDocId = jdbcTemplate.queryForObject("""
            SELECT line.doc_id
            FROM order_consumption_line line
            WHERE line.order_line_id = ?
            """, Long.class, newOrderLineId);
        assertThat(attachedDocId).isNotEqualTo(DOC_ID);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM order_consumption WHERE id = ?", String.class, attachedDocId))
            .isEqualTo("PENDING");
    }

    private void purchase(Long materialId, String quantity, String unitCost) {
        ledgerService.record(LedgerCommand.builder()
            .tenantId(TENANT_ID)
            .warehouseId(WAREHOUSE_ID)
            .materialId(materialId)
            .transactionType(InventoryTransactionType.PURCHASE)
            .direction(InventoryTransactionDirection.IN)
            .enteredQuantity(new BigDecimal(quantity))
            .enteredUomId(UOM_ID)
            .enteredUnitCost(new BigDecimal(unitCost))
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

    private void insertProductAndRecipe(Long productId, Long recipeId, Long materialId, String name) {
        jdbcTemplate.update("""
            INSERT INTO product
                (id, tenant_id, name, selling_price, is_active, is_menu, menu_category_id, created_at)
            VALUES (?, ?, ?, 10, TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            """, productId, TENANT_ID, name, MENU_CATEGORY_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, recipeId, TENANT_ID, productId);
        jdbcTemplate.update("""
            INSERT INTO recipe_item
                (tenant_id, recipe_id, material_id, quantity, uom_id, created_at)
            VALUES (?, ?, ?, 5, ?, CURRENT_TIMESTAMP)
            """, TENANT_ID, recipeId, materialId, UOM_ID);
    }

    private void insertOrder(Long orderId) {
        jdbcTemplate.update("""
            INSERT INTO orders
                (id, tenant_id, order_type, order_source, status, payment_method,
                 branch_id, warehouse_id, subtotal, tax_amount, total_amount, order_date, created_at)
            VALUES (?, ?, 'TAKEAWAY', 'POS', 'COMPLETE', 'CASH', ?, ?, 10, 0, 10,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, orderId, TENANT_ID, BRANCH_ID, WAREHOUSE_ID);
    }

    private void insertOrderLine(Long lineId, Long orderId, Long productId, Long recipeId) {
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 10, 10, CURRENT_TIMESTAMP)
            """, lineId, TENANT_ID, orderId, productId, recipeId);
    }

    private void insertDocLine(Long id, Long orderLineId) {
        jdbcTemplate.update("""
            INSERT INTO order_consumption_line
                (id, doc_id, order_line_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, id, DOC_ID, orderLineId);
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

    private int consumptionRows(Long materialId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM inventory_transaction
            WHERE tenant_id = ?
              AND reference_type = 'ORDER_CONSUMPTION_DOC'
              AND reference_id = ?
              AND material_id = ?
            """, Integer.class, TENANT_ID, DOC_ID, materialId);
    }

    private int zeroCostConsumptionRows() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM inventory_transaction
            WHERE tenant_id = ?
              AND transaction_type = 'CONSUMPTION_SUMMARY'
              AND total_cost = 0
            """, Integer.class, TENANT_ID);
    }

    private BigDecimal balanceQuantity(Long materialId) {
        return jdbcTemplate.queryForObject("""
            SELECT quantity
            FROM stock_balance
            WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?
            """, BigDecimal.class, TENANT_ID, WAREHOUSE_ID, materialId);
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
