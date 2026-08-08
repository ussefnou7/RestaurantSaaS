package com.smart.restaurant_saas.order.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.order.OrderErrorCode;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByHourRow;
import com.smart.restaurant_saas.order.reports.dto.SalesByPaymentMethodRow;
import com.smart.restaurant_saas.order.reports.dto.SalesByProductRow;
import com.smart.restaurant_saas.order.reports.dto.SalesOverTimeRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * All three sales reports share one seed, because the point of several of these assertions is that
 * the reports agree with each other over the same data. Verified against real Postgres — every
 * aggregate is native SQL. Seeded ids are in a dedicated high range and the test is transactional.
 */
@SpringBootTest
@Transactional
class SalesReportsIntegrationTest {

    private static final Long TENANT_ID = 998_101L;
    private static final Long OTHER_TENANT_ID = 998_102L;

    private static final Long BRANCH_ID = 998_201L;
    private static final Long SECOND_BRANCH_ID = 998_202L;
    private static final Long WAREHOUSE_ID = 998_301L;

    private static final Long CASHIER_A = 998_401L;
    private static final Long CASHIER_B = 998_402L;

    private static final Long CATEGORY_ID = 998_501L;
    private static final Long BURGER_ID = 998_601L;
    private static final Long FRIES_ID = 998_602L;
    private static final Long RECIPE_ID = 998_701L;

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 3, 31);

    private long nextOrderId = 998_800L;
    private long nextLineId = 999_000L;

    @Autowired
    private SalesOverTimeReportService salesOverTime;

    @Autowired
    private SalesByProductReportService salesByProduct;

    @Autowired
    private SalesByPaymentMethodReportService salesByPaymentMethod;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "Sales Tenant", "SALES_REPORTS");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "SALES_REPORTS_OTHER");
        insertBranch(BRANCH_ID, TENANT_ID, "SLS-BR-1", "Main Branch");
        insertBranch(SECOND_BRANCH_ID, TENANT_ID, "SLS-BR-2", "Second Branch");
        insertWarehouse(WAREHOUSE_ID, TENANT_ID, BRANCH_ID, "SLS-WH-1", "Main Warehouse");
        insertCategory(CATEGORY_ID, "Mains");
        insertProduct(BURGER_ID, "Burger", "40.00");
        insertProduct(FRIES_ID, "Fries", "15.00");
        insertRecipe(RECIPE_ID, BURGER_ID);
    }

    // =========================================================================
    // Status filtering
    // =========================================================================

    @Test
    void countsOnlyCompleteOrders() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-05 13:00:00", "CANCELLED", "CASH", "500.00", "70.00", "570.00");

        List<SalesOverTimeRow> rows = overTime();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getOrderCount()).isEqualTo(1L);
            assertThat(row.getTotalAmount()).isEqualTo("114.000000");
        });
    }

    @Test
    void everyReportExcludesCancelledOrdersIdentically() {
        order("2026-03-05 12:00:00", "CANCELLED", "CASH", "500.00", "70.00", "570.00");

        assertThat(overTime()).isEmpty();
        assertThat(byHour()).isEmpty();
        assertThat(byProduct()).isEmpty();
        assertThat(byPaymentMethod()).isEmpty();
    }

    // =========================================================================
    // THE RECONCILIATION REQUIREMENT
    // =========================================================================

    @Test
    void salesOverTimeAndByPaymentMethodProduceIdenticalTotals() {
        // Same orders, same filters, different grouping — the sums must be identical. A predicate
        // drifting between the two queries is the defect most likely to survive review, because
        // each query reads correctly on its own.
        long burgerOrder = order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        line(burgerOrder, BURGER_ID, "2", "40.00", "80.00");
        long cardOrder = order("2026-03-06 19:30:00", "COMPLETE", "CARD", "250.00", "35.00", "285.00");
        line(cardOrder, FRIES_ID, "4", "15.00", "60.00");
        order("2026-03-07 21:00:00", "COMPLETE", "WALLET", "33.33", "4.6662", "38.00");
        order("2026-03-20 11:00:00", "COMPLETE", "AGGREGATOR", "77.00", "10.78", "87.78");

        assertThat(sum(overTime(), SalesOverTimeRow::getTotalAmount))
            .isEqualByComparingTo(sum(byPaymentMethod(), SalesByPaymentMethodRow::getTotalAmount));
    }

    @Test
    void theTotalsStillAgreeUnderEveryFilter() {
        long a = order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00",
            BRANCH_ID, CASHIER_A, "DINE_IN");
        line(a, BURGER_ID, "2", "40.00", "80.00");
        order("2026-03-06 19:30:00", "COMPLETE", "CARD", "250.00", "35.00", "285.00",
            SECOND_BRANCH_ID, CASHIER_B, "DELIVERY");
        order("2026-03-07 21:00:00", "COMPLETE", "WALLET", "60.00", "8.40", "68.40",
            BRANCH_ID, CASHIER_B, "TAKEAWAY");

        assertTotalsAgree(BRANCH_ID, null, null);
        assertTotalsAgree(null, CASHIER_B, null);
        assertTotalsAgree(null, null, OrderType.DINE_IN);
        assertTotalsAgree(BRANCH_ID, CASHIER_B, OrderType.TAKEAWAY);
    }

    @Test
    void hourlyAndDailySeriesAlsoAgreeOnTotals() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-05 19:00:00", "COMPLETE", "CARD", "250.00", "35.00", "285.00");
        order("2026-03-06 12:00:00", "COMPLETE", "CASH", "60.00", "8.40", "68.40");

        assertThat(sum(byHour(), SalesByHourRow::getTotalAmount))
            .isEqualByComparingTo(sum(overTime(), SalesOverTimeRow::getTotalAmount));
        // Three orders across two days but three distinct hours.
        assertThat(overTime()).hasSize(2);
        assertThat(byHour()).hasSize(3);
    }

    @Test
    void byProductRevenueMatchesTheSubtotalNotTheTotal() {
        // The pre-tax report reconciles with subtotal; comparing it to totalAmount is the mistake
        // the response documentation exists to pre-empt.
        long first = order("2026-03-05 12:00:00", "COMPLETE", "CASH", "140.00", "19.60", "159.60");
        line(first, BURGER_ID, "2", "40.00", "80.00");
        line(first, FRIES_ID, "4", "15.00", "60.00");

        assertThat(sum(byProduct(), SalesByProductRow::getRevenue))
            .isEqualByComparingTo(sum(overTime(), SalesOverTimeRow::getSubtotal));
        assertThat(sum(byProduct(), SalesByProductRow::getRevenue))
            .isNotEqualByComparingTo(sum(overTime(), SalesOverTimeRow::getTotalAmount));
    }

    // =========================================================================
    // Report 1 — sales over time
    // =========================================================================

    @Test
    void isSortedChronologicallyAscendingNotByMagnitude() {
        // The shape over time is the finding; sorting by value would destroy it.
        order("2026-03-20 12:00:00", "COMPLETE", "CASH", "10.00", "1.40", "11.40");
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "900.00", "126.00", "1026.00");
        order("2026-03-12 12:00:00", "COMPLETE", "CASH", "50.00", "7.00", "57.00");

        assertThat(overTime()).extracting(SalesOverTimeRow::getSalesDate)
            .containsExactly(
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 20));
    }

    @Test
    void omitsDaysWithNoSalesRatherThanZeroFilling() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-08 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");

        assertThat(overTime()).hasSize(2);
    }

    @Test
    void computesAverageOrderValueAndHandlesASingleOrder() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-05 13:00:00", "COMPLETE", "CASH", "200.00", "28.00", "228.00");
        order("2026-03-06 12:00:00", "COMPLETE", "CASH", "50.00", "7.00", "57.00");

        List<SalesOverTimeRow> rows = overTime();

        assertThat(rows.get(0).getAverageOrderValue()).isEqualTo("171.000000");
        // A single order: the average is that order, with no division trouble.
        assertThat(rows.get(1).getOrderCount()).isEqualTo(1L);
        assertThat(rows.get(1).getAverageOrderValue()).isEqualTo("57.000000");
    }

    @Test
    void reportsComponentsSeparatelyAndNeverBlendsTax() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");

        SalesOverTimeRow row = overTime().getFirst();

        assertThat(row.getSubtotal()).isEqualTo("100.000000");
        assertThat(row.getTaxAmount()).isEqualTo("14.000000");
        assertThat(row.getTotalAmount()).isEqualTo("114.000000");
    }

    @Test
    void bucketsHoursOnTheCalendarClockIncludingAfterMidnight() {
        // Known limitation, pinned so it is a contract rather than an accident: 02:00 belongs to
        // its own calendar date, not to the previous night's session.
        order("2026-03-05 23:30:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-06 02:00:00", "COMPLETE", "CASH", "60.00", "8.40", "68.40");

        List<SalesByHourRow> rows = byHour();

        assertThat(rows).extracting(SalesByHourRow::getSalesDate, SalesByHourRow::getHourOfDay)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 3, 5), 23),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 3, 6), 2));
    }

    // =========================================================================
    // Report 2 — by product
    // =========================================================================

    @Test
    void groupsByProductSortsByRevenueAndSharesAddToOneHundred() {
        long first = order("2026-03-05 12:00:00", "COMPLETE", "CASH", "140.00", "19.60", "159.60");
        line(first, FRIES_ID, "4", "15.00", "60.00");
        line(first, BURGER_ID, "2", "40.00", "80.00");
        long second = order("2026-03-06 12:00:00", "COMPLETE", "CASH", "40.00", "5.60", "45.60");
        line(second, BURGER_ID, "1", "40.00", "40.00");

        List<SalesByProductRow> rows = byProduct();

        assertThat(rows).extracting(SalesByProductRow::getProductId)
            .containsExactly(BURGER_ID, FRIES_ID);
        assertThat(rows.getFirst().getQuantitySold()).isEqualTo("3.000000");
        assertThat(rows.getFirst().getRevenue()).isEqualTo("120.000000");
        assertThat(sum(rows, SalesByProductRow::getRevenueSharePercent))
            .isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    @Test
    void productSharesAddToOneHundredWithinAFilteredScopeToo() {
        long first = order("2026-03-05 12:00:00", "COMPLETE", "CASH", "80.00", "11.20", "91.20",
            BRANCH_ID, CASHIER_A, "DINE_IN");
        line(first, BURGER_ID, "2", "40.00", "80.00");
        long second = order("2026-03-06 12:00:00", "COMPLETE", "CASH", "60.00", "8.40", "68.40",
            SECOND_BRANCH_ID, CASHIER_A, "DINE_IN");
        line(second, FRIES_ID, "4", "15.00", "60.00");

        List<SalesByProductRow> scoped = salesByProduct.salesByProduct(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, BRANCH_ID, null, null);

        assertThat(scoped).singleElement()
            .satisfies(row -> assertThat(row.getRevenueSharePercent()).isEqualTo("100.000000"));
    }

    // =========================================================================
    // Report 3 — by payment method
    // =========================================================================

    @Test
    void groupsByMethodSortsByTotalAndSharesAddToOneHundred() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");
        order("2026-03-06 12:00:00", "COMPLETE", "CARD", "250.00", "35.00", "285.00");
        order("2026-03-07 12:00:00", "COMPLETE", "CARD", "50.00", "7.00", "57.00");

        List<SalesByPaymentMethodRow> rows = byPaymentMethod();

        assertThat(rows).extracting(SalesByPaymentMethodRow::getPaymentMethod)
            .containsExactly("CARD", "CASH");
        assertThat(rows.getFirst().getOrderCount()).isEqualTo(2L);
        assertThat(rows.getFirst().getTotalAmount()).isEqualTo("342.000000");
        assertThat(sum(rows, SalesByPaymentMethodRow::getTotalSharePercent))
            .isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    // =========================================================================
    // Filters, boundaries, isolation
    // =========================================================================

    @Test
    void branchCashierAndOrderTypeFiltersEachNarrowIndependently() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00",
            BRANCH_ID, CASHIER_A, "DINE_IN");
        order("2026-03-06 12:00:00", "COMPLETE", "CASH", "200.00", "28.00", "228.00",
            SECOND_BRANCH_ID, CASHIER_A, "DINE_IN");
        order("2026-03-07 12:00:00", "COMPLETE", "CASH", "300.00", "42.00", "342.00",
            BRANCH_ID, CASHIER_B, "DELIVERY");

        assertThat(sum(salesOverTime.salesOverTime(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, BRANCH_ID, null, null),
            SalesOverTimeRow::getTotalAmount)).isEqualByComparingTo(new BigDecimal("456.000000"));
        assertThat(sum(salesOverTime.salesOverTime(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, null, CASHIER_A, null),
            SalesOverTimeRow::getTotalAmount)).isEqualByComparingTo(new BigDecimal("342.000000"));
        assertThat(sum(salesOverTime.salesOverTime(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, OrderType.DELIVERY),
            SalesOverTimeRow::getTotalAmount)).isEqualByComparingTo(new BigDecimal("342.000000"));
    }

    @Test
    void includesOrdersExactlyOnBothRangeBoundaries() {
        // The last day matters most: a closed upper bound would drop everything after midnight,
        // which is most of a restaurant's trade.
        order("2026-03-01 00:00:00", "COMPLETE", "CASH", "10.00", "1.40", "11.40");
        order("2026-03-31 23:59:00", "COMPLETE", "CASH", "20.00", "2.80", "22.80");
        order("2026-02-28 23:59:00", "COMPLETE", "CASH", "999.00", "0.00", "999.00");
        order("2026-04-01 00:00:00", "COMPLETE", "CASH", "999.00", "0.00", "999.00");

        assertThat(overTime()).extracting(SalesOverTimeRow::getSalesDate)
            .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    }

    @Test
    void isolatesTenants() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");

        Long otherBranch = 998_203L;
        Long otherWarehouse = 998_302L;
        insertBranch(otherBranch, OTHER_TENANT_ID, "SLS-BR-X", "Foreign Branch");
        insertWarehouse(otherWarehouse, OTHER_TENANT_ID, otherBranch, "SLS-WH-X", "Foreign WH");
        insertOrder(nextOrderId++, OTHER_TENANT_ID, otherBranch, otherWarehouse,
            "2026-03-05 12:00:00", "COMPLETE", "CASH", "999.00", "0.00", "999.00",
            CASHIER_A, "DINE_IN");

        assertThat(sum(overTime(), SalesOverTimeRow::getTotalAmount))
            .isEqualByComparingTo(new BigDecimal("114.000000"));
        assertThat(salesOverTime.salesOverTime(
            OTHER_TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null)).hasSize(1);
    }

    @Test
    void everyReportRejectsAnInvertedRange() {
        assertThatThrownBy(() -> salesOverTime.salesOverTime(
                TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.REPORT_DATE_RANGE_INVALID);
        assertThatThrownBy(() -> salesByProduct.salesByProduct(
                TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> salesByPaymentMethod.salesByPaymentMethod(
                TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> salesOverTime.salesByHour(
                TENANT_ID, null, WINDOW_TO, null, null, null))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void anEmptyRangeIsAnEmptyListNotAnError() {
        order("2026-03-05 12:00:00", "COMPLETE", "CASH", "100.00", "14.00", "114.00");

        assertThat(salesOverTime.salesOverTime(
            TENANT_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null))
            .isEmpty();
        assertThat(salesByProduct.salesByProduct(
            TENANT_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null))
            .isEmpty();
        assertThat(salesByPaymentMethod.salesByPaymentMethod(
            TENANT_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null))
            .isEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<SalesOverTimeRow> overTime() {
        return salesOverTime.salesOverTime(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null);
    }

    private List<SalesByHourRow> byHour() {
        return salesOverTime.salesByHour(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null);
    }

    private List<SalesByProductRow> byProduct() {
        return salesByProduct.salesByProduct(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null);
    }

    private List<SalesByPaymentMethodRow> byPaymentMethod() {
        return salesByPaymentMethod.salesByPaymentMethod(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null);
    }

    private void assertTotalsAgree(Long branchId, Long cashierId, OrderType orderType) {
        BigDecimal daily = sum(salesOverTime.salesOverTime(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, branchId, cashierId, orderType),
            SalesOverTimeRow::getTotalAmount);
        BigDecimal byMethod = sum(salesByPaymentMethod.salesByPaymentMethod(
            TENANT_ID, WINDOW_FROM, WINDOW_TO, branchId, cashierId, orderType),
            SalesByPaymentMethodRow::getTotalAmount);
        assertThat(daily).isEqualByComparingTo(byMethod);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, String> field) {
        return rows.stream().map(field).map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long order(String dateTime, String status, String paymentMethod,
                       String subtotal, String tax, String total) {
        return order(dateTime, status, paymentMethod, subtotal, tax, total,
            BRANCH_ID, CASHIER_A, "DINE_IN");
    }

    private long order(String dateTime, String status, String paymentMethod, String subtotal,
                       String tax, String total, Long branchId, Long cashier, String orderType) {
        long id = nextOrderId++;
        insertOrder(id, TENANT_ID, branchId, WAREHOUSE_ID, dateTime, status, paymentMethod,
            subtotal, tax, total, cashier, orderType);
        return id;
    }

    private void insertOrder(long id, Long tenantId, Long branchId, Long warehouseId,
                             String dateTime, String status, String paymentMethod, String subtotal,
                             String tax, String total, Long cashier, String orderType) {
        // The CHECK constraint requires a cancellation stage exactly when the order is CANCELLED.
        String cancellationStage = "CANCELLED".equals(status) ? "BEFORE_KITCHEN" : null;
        jdbcTemplate.update("""
            INSERT INTO orders (id, tenant_id, order_type, order_source, status, cancellation_stage,
                                payment_method, branch_id, warehouse_id, subtotal, tax_amount,
                                total_amount, order_date, created_by, created_at)
            VALUES (?, ?, ?, 'POS', ?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric),
                    CAST(? AS numeric), CAST(? AS timestamp), ?, CURRENT_TIMESTAMP)
            """, id, tenantId, orderType, status, cancellationStage, paymentMethod, branchId,
            warehouseId, subtotal, tax, total, dateTime, cashier);
    }

    private void line(long orderId, Long productId, String quantity, String unitPrice,
                      String lineTotal) {
        jdbcTemplate.update("""
            INSERT INTO order_line (id, tenant_id, order_id, product_id, recipe_id, quantity,
                                    unit_price, line_total, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), CAST(? AS numeric),
                    CURRENT_TIMESTAMP)
            """, nextLineId++, TENANT_ID, orderId, productId, RECIPE_ID, quantity, unitPrice,
            lineTotal);
    }

    private void insertTenant(Long id, String name, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, id, name, code);
    }

    private void insertBranch(Long id, Long tenantId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, name, code);
    }

    private void insertWarehouse(Long id, Long tenantId, Long branchId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, branchId, code, name);
    }

    private void insertCategory(Long id, String name) {
        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, name);
    }

    private void insertProduct(Long id, String name, String price) {
        jdbcTemplate.update("""
            INSERT INTO product (id, tenant_id, name, selling_price, is_active, is_menu,
                                 menu_category_id, created_at)
            VALUES (?, ?, ?, CAST(? AS numeric), TRUE, TRUE, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, name, price, CATEGORY_ID);
    }

    private void insertRecipe(Long id, Long productId) {
        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, productId);
    }
}
