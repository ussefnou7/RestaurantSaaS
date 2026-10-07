package com.smart.restaurant_saas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.auth.support.TestScopes;
import com.smart.restaurant_saas.dashboard.dto.BlockStatus;
import com.smart.restaurant_saas.dashboard.dto.BranchKpiRow;
import com.smart.restaurant_saas.dashboard.dto.DashboardSummaryResponse;
import com.smart.restaurant_saas.inventory.reports.LowStockReportService;
import com.smart.restaurant_saas.inventory.reports.StockValuationReportService;
import com.smart.restaurant_saas.inventory.reports.dto.StockValuationRow;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.order.reports.SalesOverTimeReportService;
import com.smart.restaurant_saas.order.reports.dto.SalesOverTimeRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The guard behind the shared-query rule.
 *
 * <p>The architecture says a metric's definition lives in exactly one repository method, and that
 * the dashboard calls the same method the matching report does. Nothing enforces that: a future
 * change can add a filter to one query and not the other, and both will still look correct read
 * alone. The failure mode is not an exception — it is the dashboard reading 2,847,300 while the
 * sales report reads 2,851,900 for the same range, after which the owner is right to trust
 * neither. A convention with no test erodes the first time someone is in a hurry, so the agreement
 * is asserted rather than described.
 *
 * <p><b>Three pairs, chosen because each would drift for a different reason.</b> Net sales share a
 * status predicate and a half-open window; stock value shares an active-material-in-active-
 * warehouse restriction and a per-row expression that is summed in two different places; the
 * low-stock counts share a {@code minimum > 0} guard whose absence would silently reclassify every
 * material with no minimum configured.
 *
 * <p>Seeded ids live in a dedicated high range and the test is transactional, following the
 * existing report integration tests.
 */
@SpringBootTest
@Transactional
class DashboardReconciliationIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long BRANCH_A = 996_101L;
    private static final Long BRANCH_B = 996_102L;
    private static final Long UOM_ID = 996_201L;
    private static final Long CATEGORY_ID = 996_301L;
    private static final Long WAREHOUSE_A = 996_401L;
    private static final Long WAREHOUSE_B = 996_402L;
    private static final Long CHICKEN_ID = 996_501L;
    private static final Long RICE_ID = 996_502L;
    private static final Long OIL_ID = 996_503L;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private DashboardSummaryService dashboardSummaryService;

    @Autowired
    private SalesOverTimeReportService salesOverTimeReportService;

    @Autowired
    private StockValuationReportService stockValuationReportService;

    @Autowired
    private LowStockReportService lowStockReportService;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * A SYS_ADMIN caller, because the subject here is the figures rather than who may see them.
     *
     * <p>Every dashboard block is gated on the permission of the module it reads from, so a caller
     * with no grants gets a page of {@code HIDDEN_NO_PERMISSION} — which would make every
     * assertion below pass against a null payload. The gating itself is asserted in
     * {@code DashboardControllerSecurityTest}, where it is the subject.
     */
    @BeforeEach
    void authenticate() {
        TestScopes.authenticateSysAdmin(TENANT_ID);
    }

    @AfterEach
    void deauthenticate() {
        TestScopes.clearAuthentication();
    }

    @BeforeEach
    void seed() {
        clean();

        // timezone is NOT NULL and there is no fallback by design (D101): a missing tenant zone
        // throws rather than defaulting, and every day boundary on this screen resolves through
        // it. Africa/Cairo so the seeded wall-clock order times land where they read.
        jdbc.update("INSERT INTO tenants (id, name, code, status, timezone, created_at) "
            + "VALUES (?, 'Dashboard Recon', 'DASHRECON', 'ACTIVE', 'Africa/Cairo', "
            + "CURRENT_TIMESTAMP)", TENANT_ID);

        branch(BRANCH_A, "Nasr City", "مدينة نصر");
        branch(BRANCH_B, "Maadi", "المعادي");

        jdbc.update("INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, "
            + "entered_factor, active, created_at) VALUES "
            + "(?, ?, 'DASHKG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)",
            UOM_ID, TENANT_ID);
        jdbc.update("INSERT INTO material_category (id, tenant_id, code, name, active, "
            + "created_at) VALUES (?, ?, 'DASHPROT', 'Proteins', TRUE, CURRENT_TIMESTAMP)",
            CATEGORY_ID, TENANT_ID);

        warehouse(WAREHOUSE_A, BRANCH_A, "WH-A", "Nasr City Kitchen");
        warehouse(WAREHOUSE_B, BRANCH_B, "WH-B", "Maadi Kitchen");

        material(CHICKEN_ID, "CHK", "Chicken");
        material(RICE_ID, "RIC", "Rice");
        material(OIL_ID, "OIL", "Frying Oil");

        // Two warehouses so the low-stock reconciliation covers the per-warehouse grouping, and
        // a mix of out / low / healthy so the two counts are both non-zero and different.
        balance(WAREHOUSE_A, CHICKEN_ID, "0", "10", "40");        // out
        balance(WAREHOUSE_A, RICE_ID, "4", "10", "12");           // low, holding stock
        balance(WAREHOUSE_A, OIL_ID, "50", "10", "25");           // healthy
        balance(WAREHOUSE_B, CHICKEN_ID, "2", "10", "40");        // low
        balance(WAREHOUSE_B, RICE_ID, "0", "0", "12");            // no minimum: never "low"

        // Orders across both branches and several days, so the daily report has multiple rows
        // for the dashboard's single total to be checked against.
        order(BRANCH_A, WAREHOUSE_A, "2026-03-02 12:30:00", "COMPLETE", "100.50", "14.07", "114.57");
        order(BRANCH_A, WAREHOUSE_A, "2026-03-02 19:45:00", "COMPLETE", "240.00", "33.60", "273.60");
        order(BRANCH_A, WAREHOUSE_A, "2026-03-15 13:00:00", "COMPLETE", "75.25", "10.54", "85.79");
        order(BRANCH_B, WAREHOUSE_B, "2026-03-15 20:10:00", "COMPLETE", "310.00", "43.40", "353.40");
        order(BRANCH_B, WAREHOUSE_B, "2026-03-31 21:00:00", "COMPLETE", "180.75", "25.31", "206.06");

        // Must not be counted: a cancellation, and a COMPLETE order one day outside the window.
        cancelledOrder(BRANCH_A, WAREHOUSE_A, "2026-03-10 14:00:00", "500.00", "570.00");
        order(BRANCH_A, WAREHOUSE_A, "2026-04-01 12:00:00", "COMPLETE", "999.00", "139.86", "1138.86");
    }

    @Test
    @DisplayName("dashboard net sales equals the sum of the sales-over-time report's rows")
    void netSalesReconcilesWithTheDailyReport() {
        DashboardSummaryResponse summary =
            dashboardSummaryService.summary(TENANT_ID, FROM, TO, null);
        List<SalesOverTimeRow> reportRows =
            salesOverTimeReportService.salesOverTime(TENANT_ID, FROM, TO, null, null, null);

        BigDecimal reportNetSales = reportRows.stream()
            .map(row -> new BigDecimal(row.getSubtotal()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reportTotal = reportRows.stream()
            .map(row -> new BigDecimal(row.getTotalAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long reportOrders = reportRows.stream().mapToLong(SalesOverTimeRow::getOrderCount).sum();

        assertThat(summary.getKpis().getStatus()).isEqualTo(BlockStatus.OK);
        assertThat(new BigDecimal(summary.getKpis().getData().getNetSales()))
            .isEqualByComparingTo(reportNetSales);
        assertThat(new BigDecimal(summary.getKpis().getData().getTotalAmount()))
            .isEqualByComparingTo(reportTotal);
        assertThat(summary.getKpis().getData().getOrderCount()).isEqualTo(reportOrders);

        // Guards the seed as well as the query: if the exclusions silently stopped working the
        // two sides would still agree, because both would be wrong in the same direction.
        assertThat(reportOrders).isEqualTo(5);
        assertThat(reportNetSales).isEqualByComparingTo(new BigDecimal("906.50"));
    }

    @Test
    @DisplayName("the branch breakdown sums back to the KPI row")
    void branchBreakdownSumsToTheHeadline() {
        DashboardSummaryResponse summary =
            dashboardSummaryService.summary(TENANT_ID, FROM, TO, null);

        assertThat(summary.getBranches().getStatus()).isEqualTo(BlockStatus.OK);
        List<BranchKpiRow> rows = summary.getBranches().getData();
        assertThat(rows).hasSize(2);

        BigDecimal branchSum = rows.stream()
            .map(row -> new BigDecimal(row.getNetSales()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(branchSum)
            .isEqualByComparingTo(new BigDecimal(summary.getKpis().getData().getNetSales()));
        assertThat(rows.stream().mapToLong(BranchKpiRow::getOrderCount).sum())
            .isEqualTo(summary.getKpis().getData().getOrderCount());

        // Shares are computed against the visible rows, so they add to 100 rather than against
        // some tenant-wide total the caller cannot see.
        BigDecimal shareSum = rows.stream()
            .map(row -> new BigDecimal(row.getNetSalesSharePercent()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shareSum).isCloseTo(new BigDecimal("100"), org.assertj.core.data.Offset.offset(
            new BigDecimal("0.02")));
    }

    @Test
    @DisplayName("dashboard stock value equals the sum of the valuation report's rows")
    void stockValueReconcilesWithTheValuationReport() {
        BigDecimal reportValue = stockValuationReportService
            .stockValuation(TENANT_ID, null, null, null).stream()
            .map(row -> new BigDecimal(row.getTotalValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(stockBalanceRepository.sumStockValue(TENANT_ID, null))
            .isEqualByComparingTo(reportValue);
        assertThat(reportValue).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("stock value narrows by branch the same way the valuation report does")
    void stockValueReconcilesPerBranch() {
        List<StockValuationRow> branchARows =
            stockValuationReportService.stockValuation(TENANT_ID, BRANCH_A, null, null);

        BigDecimal reportValue = branchARows.stream()
            .map(row -> new BigDecimal(row.getTotalValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(stockBalanceRepository.sumStockValue(TENANT_ID, BRANCH_A))
            .isEqualByComparingTo(reportValue);
    }

    @Test
    @DisplayName("the low-stock alert counts equal the low-stock report's rows, per warehouse")
    void lowStockCountsReconcileWithTheReport() {
        long reportRows = lowStockReportService.lowStock(TENANT_ID, null, null, null).size();

        long alertRows = stockBalanceRepository.aggregateLowStockCounts(TENANT_ID, null).stream()
            .mapToLong(row -> row.getOutOfStockCount() + row.getBelowMinimumCount())
            .sum();

        assertThat(alertRows).isEqualTo(reportRows);

        // The split is the alert's own contribution, so it is pinned too: chicken out in both
        // warehouses would be a single "2 out" row if the grouping drifted, and the balance with
        // no minimum configured must appear in neither count.
        assertThat(reportRows).isEqualTo(3);
        assertThat(stockBalanceRepository.aggregateLowStockCounts(TENANT_ID, null))
            .extracting(row -> row.getWarehouseId() + ":"
                + row.getOutOfStockCount() + "/" + row.getBelowMinimumCount())
            .containsExactlyInAnyOrder(
                WAREHOUSE_A + ":1/1",
                WAREHOUSE_B + ":0/1");
    }

    @Test
    @DisplayName("cost coverage reports INSUFFICIENT_DATA rather than 100% for a range with no sales")
    void costCoverageIsUnknownWithoutSales() {
        DashboardSummaryResponse summary = dashboardSummaryService.summary(
            TENANT_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

        // The whole point: 0/0 is not 100%. Telling an owner every cost is posted for a month
        // that sold nothing is a false reassurance about exactly what the figure warns about.
        assertThat(summary.getCostCoverage().getStatus()).isEqualTo(BlockStatus.INSUFFICIENT_DATA);
        assertThat(summary.getCostCoverage().getData()).isNull();
        assertThat(summary.getKpis().getData().getCostCoverage()).isNull();
    }

    @Test
    @DisplayName("the trend's bucket follows the requested range, not a parameter")
    void trendBucketFollowsTheRange() {
        assertThat(dashboardSummaryService.summary(
                TENANT_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), null).getBucket())
            .isEqualTo(TrendBucket.HOUR);

        assertThat(dashboardSummaryService.summary(TENANT_ID, FROM, TO, null).getBucket())
            .isEqualTo(TrendBucket.DAY);

        assertThat(dashboardSummaryService.summary(
                TENANT_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null).getBucket())
            .isEqualTo(TrendBucket.MONTH);
    }

    // ---- seeding ---------------------------------------------------------------------------

    @Autowired
    private com.smart.restaurant_saas.dashboard.alert.AlertService dashboardAlerts;

    @Test
    void allAlertQueriesRunWithinTheRequestedBranch() {
        var response = dashboardAlerts.alerts(TENANT_ID, BRANCH_A);
        assertThat(response.getWithheld()).isEmpty();
        assertThat(response.getGroups()).anySatisfy(group -> {
            assertThat(group.getCode()).isEqualTo(com.smart.restaurant_saas.dashboard.alert.AlertCode.STOCK_OUT);
            assertThat(group.getCount()).isEqualTo(1);
            assertThat(group.getRows()).allSatisfy(row -> assertThat(row.getBranchId()).isEqualTo(BRANCH_A));
        });
    }

    @Autowired
    private com.smart.restaurant_saas.dashboard.alert.StockAlerts runningOutSoon;

    @Test
    void daysOfCoverUsesTheBalanceUnitAndExcludesFutureConsumption() {
        Long grams = 996_202L;
        jdbc.update("INSERT INTO uom (id, tenant_id, code, name, symbol, type, base_uom_id, "
            + "factor_to_base, entered_factor, active, created_at) VALUES "
            + "(?, ?, 'DASHG', 'Gram', 'g', 'WEIGHT', ?, 0.001, 0.001, TRUE, CURRENT_TIMESTAMP)",
            grams, TENANT_ID, UOM_ID);
        jdbc.update("UPDATE material SET stock_uom_id = ? WHERE id = ?", grams, CHICKEN_ID);
        jdbc.update("UPDATE stock_balance SET quantity = 10 WHERE material_id = ?", CHICKEN_ID);
        for (Long warehouse : List.of(WAREHOUSE_A, WAREHOUSE_B)) {
            jdbc.update("""
                INSERT INTO inventory_transaction (tenant_id, warehouse_id, material_id,
                    transaction_type, direction, entered_quantity, entered_uom_id,
                    stock_quantity, stock_uom_id, total_cost, reference_type,
                    transaction_date, movement_date, created_at)
                VALUES (?, ?, ?, 'CONSUMPTION_SUMMARY', 'OUT', 14000, ?, 14000, ?, 140,
                    'ORDER_CONSUMPTION_DOC', '2026-03-25', '2026-03-25', CURRENT_TIMESTAMP)
                """, TENANT_ID, warehouse, CHICKEN_ID, grams, grams);
        }
        var context = new com.smart.restaurant_saas.dashboard.alert.AlertContext(
            TENANT_ID, BRANCH_A, java.time.ZoneId.of("Africa/Cairo"),
            java.time.LocalDateTime.of(2026, 3, 31, 12, 0));
        // 10 kg / (14,000 g / 14 days) = 10 days, not 0.01 days.
        assertThat(runningOutSoon.runningOutSoon(context)).isEmpty();
        jdbc.update("UPDATE stock_balance SET quantity = 2 WHERE material_id = ?", CHICKEN_ID);
        var alerts = runningOutSoon.runningOutSoon(context);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().branchId()).isEqualTo(BRANCH_A);
        assertThat(alerts.getFirst().params().get("soonestDaysOfCover")).isEqualTo("2.0");
        jdbc.update("UPDATE inventory_transaction SET movement_date = '2026-04-01' "
            + "WHERE tenant_id = ?", TENANT_ID);
        assertThat(runningOutSoon.runningOutSoon(context)).isEmpty();
    }

    private void branch(Long id, String name, String nameAr) {
        jdbc.update("INSERT INTO branches (id, tenant_id, name, name_ar, code, is_active, "
            + "timezone, created_at) VALUES (?, ?, ?, ?, ?, TRUE, 'Africa/Cairo', "
            + "CURRENT_TIMESTAMP)",
            id, TENANT_ID, name, nameAr, "BR" + id);
    }

    private void warehouse(Long id, Long branchId, String code, String name) {
        jdbc.update("INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, "
            + "created_at) VALUES (?, ?, ?, ?, ?, 'KITCHEN', TRUE, CURRENT_TIMESTAMP)",
            id, TENANT_ID, branchId, code, name);
    }

    private void material(Long id, String code, String name) {
        jdbc.update("INSERT INTO material (id, tenant_id, category_id, stock_uom_id, "
            + "display_uom_id, code, name, active, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)",
            id, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name);
    }

    private void balance(Long warehouseId, Long materialId, String quantity, String minimum,
                         String averageCost) {
        jdbc.update("INSERT INTO stock_balance (tenant_id, warehouse_id, material_id, quantity, "
            + "uom_id, average_cost, minimum_quantity, created_at) "
            + "VALUES (?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), CAST(? AS numeric), "
            + "CURRENT_TIMESTAMP)",
            TENANT_ID, warehouseId, materialId, quantity, UOM_ID, averageCost, minimum);
    }

    private void order(Long branchId, Long warehouseId, String orderDate, String status,
                       String subtotal, String tax, String total) {
        jdbc.update("INSERT INTO orders (tenant_id, order_type, order_source, status, "
            + "payment_method, branch_id, warehouse_id, subtotal, tax_amount, total_amount, "
            + "order_date, created_at) VALUES (?, 'DINE_IN', 'POS', ?, 'CASH', ?, ?, "
            + "CAST(? AS numeric), CAST(? AS numeric), CAST(? AS numeric), CAST(? AS timestamp), "
            + "CURRENT_TIMESTAMP)",
            TENANT_ID, status, branchId, warehouseId, subtotal, tax, total, orderDate);
    }

    private void cancelledOrder(Long branchId, Long warehouseId, String orderDate, String subtotal,
                                String total) {
        jdbc.update("INSERT INTO orders (tenant_id, order_type, order_source, status, "
            + "cancellation_stage, payment_method, branch_id, warehouse_id, subtotal, tax_amount, "
            + "total_amount, order_date, created_at) "
            + "VALUES (?, 'DINE_IN', 'POS', 'CANCELLED', 'IN_KITCHEN_COOKED', 'CASH', ?, ?, "
            + "CAST(? AS numeric), 0, CAST(? AS numeric), CAST(? AS timestamp), CURRENT_TIMESTAMP)",
            TENANT_ID, branchId, warehouseId, subtotal, total, orderDate);
    }

    private void clean() {
        jdbc.update("DELETE FROM orders WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM stock_balance WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM material WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM warehouse WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM material_category WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM uom WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
    }
}
