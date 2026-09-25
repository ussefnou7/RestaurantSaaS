package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.order.core.Order;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderLineType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D20/D134 end to end: one paid order carrying a dish that was cooked and then binned splits into
 * two documents, and the API says which is which.
 *
 * <p>The type is the whole point — a waste doc is otherwise identical to an ordinary one on the
 * screen, so a doc that arrives untyped is indistinguishable from one that never arrived.
 */
@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionWasteDocIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long BRANCH_ID = 996_101L;
    private static final Long UOM_ID = 996_201L;
    private static final Long CATEGORY_ID = 996_301L;
    private static final Long WAREHOUSE_ID = 996_401L;
    private static final Long CHICKEN_ID = 996_501L;
    private static final Long MENU_CATEGORY_ID = 996_601L;
    private static final Long PRODUCT_ID = 996_701L;
    private static final Long RECIPE_ID = 996_801L;
    private static final Long ORDER_ID = 996_901L;
    private static final Long SOLD_LINE_ID = 997_001L;
    private static final Long BINNED_LINE_ID = 997_002L;
    private static final Long USER_ID = 997_101L;

    @Autowired
    private OrderConsumptionService service;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Order Consumption Waste Tenant', 'OC_WASTE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Waste Branch', 'OCW-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'OCW-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'OCW-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'OCW-WH-1', 'Waste Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material
                (id, tenant_id, category_id, stock_uom_id, display_uom_id, code, name,
                 active, created_at)
            VALUES (?, ?, ?, ?, ?, 'OCW-CHICKEN', 'Chicken', TRUE, CURRENT_TIMESTAMP)
            """, CHICKEN_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Waste Menu', 0, TRUE, CURRENT_TIMESTAMP)
            """, MENU_CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO product
                (id, tenant_id, name, selling_price, is_active, is_menu, menu_category_id, created_at)
            VALUES (?, ?, 'Grilled Chicken', 80, TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            """, PRODUCT_ID, TENANT_ID, MENU_CATEGORY_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, RECIPE_ID, TENANT_ID, PRODUCT_ID);
        jdbcTemplate.update("""
            INSERT INTO recipe_item
                (tenant_id, recipe_id, material_id, uom_id, quantity, created_at)
            VALUES (?, ?, ?, ?, 1.000000, CURRENT_TIMESTAMP)
            """, TENANT_ID, RECIPE_ID, CHICKEN_ID, UOM_ID);

        // One ticket: one chicken sold, one cooked and sent back. Only the sold one is in the money.
        jdbcTemplate.update("""
            INSERT INTO orders
                (id, tenant_id, order_type, order_source, status, payment_method,
                 branch_id, warehouse_id, subtotal, tax_amount, total_amount, order_date, created_at)
            VALUES (?, ?, 'TAKEAWAY', 'POS', 'COMPLETE', 'CASH', ?, ?, 80, 0, 80,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, ORDER_ID, TENANT_ID, BRANCH_ID, WAREHOUSE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, line_type, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 80, 80, 'SALE', CURRENT_TIMESTAMP)
            """, SOLD_LINE_ID, TENANT_ID, ORDER_ID, PRODUCT_ID, RECIPE_ID);
        jdbcTemplate.update("""
            INSERT INTO order_line
                (id, tenant_id, order_id, product_id, recipe_id,
                 quantity, unit_price, line_total, line_type, waste_stage, created_at)
            VALUES (?, ?, ?, ?, ?, 1, 80, 80, 'WASTE', 'IN_KITCHEN_COOKED', CURRENT_TIMESTAMP)
            """, BINNED_LINE_ID, TENANT_ID, ORDER_ID, PRODUCT_ID, RECIPE_ID);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM order_consumption_material WHERE doc_id IN (SELECT id FROM order_consumption WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM order_consumption_line WHERE doc_id IN (SELECT id FROM order_consumption WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM order_consumption WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM order_line WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = ?", TENANT_ID);
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
    void aBinnedDishOpensItsOwnDocAndTheApiNamesItAsWaste() {
        recordOrder();

        Long ordinaryDocId = docIdOfType("ORDINARY");
        Long wasteDocId = docIdOfType("WASTE");
        assertThat(ordinaryDocId).isNotNull();
        assertThat(wasteDocId).isNotNull().isNotEqualTo(ordinaryDocId);

        OrderConsumptionDocDetailResponse waste = service.getById(wasteDocId, TENANT_ID);
        assertThat(waste.getType()).isEqualTo(OrderConsumptionType.WASTE);
        assertThat(waste.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(line.getLineType()).isEqualTo(OrderLineType.WASTE);
            assertThat(line.getWasteStage()).isEqualTo(CancellationStage.IN_KITCHEN_COOKED);
        });

        OrderConsumptionDocDetailResponse ordinary = service.getById(ordinaryDocId, TENANT_ID);
        assertThat(ordinary.getType()).isEqualTo(OrderConsumptionType.ORDINARY);
        assertThat(ordinary.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getLineType()).isEqualTo(OrderLineType.SALE);
            assertThat(line.getWasteStage()).isNull();
        });
    }

    @Test
    void theListFiltersByTypeAndCarriesItOnEveryRow() {
        recordOrder();

        Page<OrderConsumptionDocListResponse> wasteOnly = service.list(
            TENANT_ID, WAREHOUSE_ID, OrderConsumptionType.WASTE, null, null, null,
            PageRequest.of(0, 20));
        assertThat(wasteOnly.getContent()).singleElement().satisfies(doc ->
            assertThat(doc.getType()).isEqualTo(OrderConsumptionType.WASTE));

        Page<OrderConsumptionDocListResponse> both = service.list(
            TENANT_ID, WAREHOUSE_ID, null, null, null, null, PageRequest.of(0, 20));
        assertThat(both.getContent())
            .extracting(OrderConsumptionDocListResponse::getType)
            .containsExactlyInAnyOrder(OrderConsumptionType.ORDINARY, OrderConsumptionType.WASTE);
    }

    /** recordCompletedOrder takes the entity, so the order is read and handed over in one tx. */
    private void recordOrder() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Order order = orderRepository.findById(ORDER_ID).orElseThrow();
            order.getLines().size();
            service.recordCompletedOrder(order, USER_ID);
        });
    }

    private Long docIdOfType(String type) {
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM order_consumption WHERE tenant_id = ? AND type = ?",
            Long.class, TENANT_ID, type);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
