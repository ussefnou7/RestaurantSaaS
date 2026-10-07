package com.smart.restaurant_saas.dashboard;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.dashboard.dto.BranchKpiRow;
import com.smart.restaurant_saas.dashboard.dto.ConsumptionPipelineRow;
import com.smart.restaurant_saas.dashboard.dto.CostCoverage;
import com.smart.restaurant_saas.dashboard.dto.DashboardBlock;
import com.smart.restaurant_saas.dashboard.dto.DashboardKpis;
import com.smart.restaurant_saas.dashboard.dto.DashboardSummaryResponse;
import com.smart.restaurant_saas.dashboard.dto.MixRow;
import com.smart.restaurant_saas.dashboard.dto.SalesTrend;
import com.smart.restaurant_saas.dashboard.dto.TrendPoint;
import com.smart.restaurant_saas.inventory.core.WasteService;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionRepository;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.order.core.BranchSalesAggregate;
import com.smart.restaurant_saas.order.core.CostCoverageAggregate;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.order.core.SalesMixAggregate;
import com.smart.restaurant_saas.order.core.SalesTotalsAggregate;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles permission-gated summary blocks using the owning modules' aggregate queries. */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private static final int SCALE = 6;
    private static final int PERCENT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderRepository orderRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OrderConsumptionRepository consumptionRepository;
    private final BranchRepository branchRepository;
    private final SecurityService securityService;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final TenantTimeZoneService tenantTimeZoneService;

    /**
     * The dashboard for one range and branch scope.
     *
     * @param requestedBranchId a <em>narrowing</em> request, never a grant. Resolved through
     *                          {@code CurrentUserScopeProvider}, which 403s a scoped caller asking
     *                          for a branch that is not theirs rather than returning empty blocks
     *                          — an empty block would teach them the branch has no sales.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long tenantId, LocalDate from, LocalDate to,
                                            Long requestedBranchId) {
        ZoneId zone = tenantTimeZoneService.zoneFor(tenantId);
        DashboardDateRange range = DashboardDateRange.of(from, to, zone);
        Long branchFilter = currentUserScopeProvider.resolveBranchFilter(requestedBranchId);

        boolean canSeeSales = securityService.hasPermission(DashboardPermissions.REPORTS_VIEW_SALES);
        boolean canSeeOrders = securityService.hasPermission(DashboardPermissions.ORDERS_VIEW);
        boolean canSeeInventory =
            securityService.hasPermission(DashboardPermissions.INVENTORY_REPORTS_VIEW);

        return DashboardSummaryResponse.builder()
            .from(range.from())
            .to(range.to())
            .branchId(branchFilter)
            .bucket(range.bucket())
            .generatedAt(LocalDateTime.now(zone))
            .kpis(canSeeSales
                ? DashboardBlock.of(kpis(tenantId, range, branchFilter, canSeeInventory))
                : DashboardBlock.hidden())
            .trend(canSeeSales
                ? DashboardBlock.of(trend(tenantId, range, branchFilter))
                : DashboardBlock.hidden())
            .branches(canSeeSales
                ? DashboardBlock.of(branches(tenantId, range, branchFilter))
                : DashboardBlock.hidden())
            .orderTypes(canSeeSales
                ? DashboardBlock.of(mix(orderRepository.aggregateSalesByOrderType(
                    tenantId, range.fromInclusive(), range.toExclusive(), branchFilter)))
                : DashboardBlock.hidden())
            .paymentMethods(canSeeSales
                ? DashboardBlock.of(paymentMethods(tenantId, range, branchFilter))
                : DashboardBlock.hidden())
            // Cancellations are gated on viewing orders rather than on sales reporting: they are
            // a property of individual orders, and a role allowed to read sales totals is not
            // necessarily allowed to know which orders were voided and for how much.
            .cancellations(canSeeOrders
                ? DashboardBlock.of(mix(orderRepository.aggregateCancellationsByStage(
                    tenantId, range.fromInclusive(), range.toExclusive(), branchFilter)))
                : DashboardBlock.hidden())
            .costCoverage(canSeeInventory
                ? costCoverage(tenantId, range, branchFilter)
                : DashboardBlock.hidden())
            .consumptionPipeline(canSeeInventory
                ? DashboardBlock.of(pipeline(tenantId, range, branchFilter))
                : DashboardBlock.hidden())
            .build();
    }

    /**
     * The KPI row.
     *
     * <p>The three inventory figures — waste, stock value, cost coverage — are omitted rather than
     * zeroed when the caller cannot see inventory. Null renders as an absence; zero would claim
     * the tenant wasted nothing and holds no stock, which is a false statement rather than a
     * hidden one.
     */
    private DashboardKpis kpis(Long tenantId, DashboardDateRange range, Long branchFilter,
                               boolean canSeeInventory) {
        SalesTotalsAggregate totals = orderRepository.aggregateSalesTotals(
            tenantId, range.fromInclusive(), range.toExclusive(), branchFilter);

        DashboardKpis.DashboardKpisBuilder builder = DashboardKpis.builder()
            .orderCount(totals.getOrderCount())
            .netSales(money(totals.getNetSales()))
            .taxAmount(money(totals.getTaxAmount()))
            .totalAmount(money(totals.getTotalAmount()))
            .averageOrderValue(money(totals.getAverageOrderValue()));

        if (!canSeeInventory) {
            return builder.build();
        }

        CostCoverageAggregate coverage = orderRepository.aggregateCostCoverage(
            tenantId, range.fromInclusive(), range.toExclusive(), branchFilter);

        return builder
            // The ledger's waste sign is negative — a write-off removes stock — and the KPI is
            // read as "waste cost us X", so the magnitude is presented. A window whose reversals
            // outweighed its write-offs therefore shows as a small positive net, not as a
            // negative waste figure, which would be unreadable.
            .wasteValue(money(transactionRepository.sumNetValueByReferenceType(
                tenantId, WasteService.REFERENCE_TYPE,
                range.fromInclusive(), range.toExclusive(), branchFilter).abs()))
            .stockValue(money(stockBalanceRepository.sumStockValue(tenantId, branchFilter)))
            .costCoverage(coveragePercent(coverage))
            .build();
    }

    /**
     * The trend, at the grain the range implied.
     *
     * <p>Three branches calling three fixed-grouping queries, two of which the sales reports
     * already own. The dashboard does not re-derive a daily or hourly series of its own, so its
     * chart and the sales-over-time report cannot disagree about a day.
     */
    private SalesTrend trend(Long tenantId, DashboardDateRange range, Long branchFilter) {
        SalesTrend.SalesTrendBuilder builder = SalesTrend.builder().bucket(range.bucket());

        switch (range.bucket()) {
            case HOUR -> orderRepository.aggregateSalesByHour(
                    tenantId, range.fromInclusive(), range.toExclusive(), branchFilter, null, null)
                .forEach(row -> builder.point(TrendPoint.builder()
                    .label(String.valueOf(row.getHourOfDay()))
                    .orderCount(row.getOrderCount())
                    .netSales(money(row.getSubtotal()))
                    .totalAmount(money(row.getTotalAmount()))
                    .build()));
            case DAY -> orderRepository.aggregateSalesOverTime(
                    tenantId, range.fromInclusive(), range.toExclusive(), branchFilter, null, null)
                .forEach(row -> builder.point(TrendPoint.builder()
                    .label(row.getSalesDate().toString())
                    .orderCount(row.getOrderCount())
                    .netSales(money(row.getSubtotal()))
                    .totalAmount(money(row.getTotalAmount()))
                    .build()));
            case MONTH -> orderRepository.aggregateSalesByMonth(
                    tenantId, range.fromInclusive(), range.toExclusive(), branchFilter)
                .forEach(row -> builder.point(TrendPoint.builder()
                    .label(row.getBucketStart().toString())
                    .orderCount(row.getOrderCount())
                    .netSales(money(row.getNetSales()))
                    .totalAmount(money(row.getTotalAmount()))
                    .build()));
        }

        return builder.build();
    }

    /** Per-branch figures with names resolved, and each branch's share of the range. */
    private List<BranchKpiRow> branches(Long tenantId, DashboardDateRange range, Long branchFilter) {
        List<BranchSalesAggregate> rows = orderRepository.aggregateSalesByBranch(
            tenantId, range.fromInclusive(), range.toExclusive(), branchFilter);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Branch> names = branchRepository.findByTenantIdAndIdIn(
                tenantId, rows.stream().map(BranchSalesAggregate::getBranchId).toList()).stream()
            .collect(Collectors.toMap(Branch::getId, Function.identity()));

        // Shares are computed against the sum of these rows rather than re-queried, so they add
        // to 100 within what the caller can actually see — a tenant-wide denominator would make
        // a branch-scoped user's single row read as 12%.
        BigDecimal total = rows.stream()
            .map(BranchSalesAggregate::getNetSales)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
            .map(row -> {
                Branch branch = names.get(row.getBranchId());
                return BranchKpiRow.builder()
                    .branchId(row.getBranchId())
                    .branchName(branch == null ? null : branch.getName())
                    .branchNameAr(branch == null ? null : branch.getNameAr())
                    .orderCount(row.getOrderCount())
                    .netSales(money(row.getNetSales()))
                    .taxAmount(money(row.getTaxAmount()))
                    .totalAmount(money(row.getTotalAmount()))
                    .averageOrderValue(money(row.getAverageOrderValue()))
                    .netSalesSharePercent(sharePercent(row.getNetSales(), total))
                    .build();
            })
            .toList();
    }

    /** Payment mix, from the payment-method report's own query. */
    private List<MixRow> paymentMethods(Long tenantId, DashboardDateRange range, Long branchFilter) {
        return orderRepository.aggregateSalesByPaymentMethod(
                tenantId, range.fromInclusive(), range.toExclusive(), branchFilter, null, null)
            .stream()
            .map(row -> MixRow.builder()
                .dimension(row.getPaymentMethod())
                .orderCount(row.getOrderCount())
                .netSales(money(row.getSubtotal()))
                .totalAmount(money(row.getTotalAmount()))
                .sharePercent(percent(row.getTotalSharePercent()))
                .build())
            .toList();
    }

    private List<MixRow> mix(List<SalesMixAggregate> rows) {
        return rows.stream()
            .map(row -> MixRow.builder()
                .dimension(row.getDimension())
                .orderCount(row.getOrderCount())
                .netSales(money(row.getNetSales()))
                .totalAmount(money(row.getTotalAmount()))
                .sharePercent(percent(row.getSharePercent()))
                .build())
            .toList();
    }

    /**
     * Cost coverage, or {@code INSUFFICIENT_DATA} when there are no sales to measure it against.
     *
     * <p>The guard is the point of the method. 0/0 is not 100%, and a day that sold nothing
     * reporting "all costs posted" is a false reassurance about precisely the thing the figure
     * exists to warn about.
     */
    private DashboardBlock<CostCoverage> costCoverage(Long tenantId, DashboardDateRange range,
                                                      Long branchFilter) {
        CostCoverageAggregate coverage = orderRepository.aggregateCostCoverage(
            tenantId, range.fromInclusive(), range.toExclusive(), branchFilter);

        if (isZero(coverage.getTotalSales())) {
            return DashboardBlock.insufficientData();
        }

        return DashboardBlock.of(CostCoverage.builder()
            .coveragePercent(coveragePercent(coverage))
            .totalSales(money(coverage.getTotalSales()))
            .postedSales(money(coverage.getPostedSales()))
            .unpostedSales(money(coverage.getUnpostedSales()))
            .noConsumptionSales(money(coverage.getNoConsumptionSales()))
            .build());
    }

    private List<ConsumptionPipelineRow> pipeline(Long tenantId, DashboardDateRange range,
                                                  Long branchFilter) {
        return consumptionRepository.aggregateByStatus(
                tenantId, branchFilter, range.fromInclusive(), range.toExclusive())
            .stream()
            .map(row -> ConsumptionPipelineRow.builder()
                .status(row.getStatus())
                .docCount(row.getDocCount())
                .oldestAt(row.getOldestAt())
                .build())
            .toList();
    }

    /** Null when there are no sales — see {@link #costCoverage}. */
    private String coveragePercent(CostCoverageAggregate coverage) {
        if (isZero(coverage.getTotalSales())) {
            return null;
        }
        return sharePercent(coverage.getPostedSales(), coverage.getTotalSales());
    }

    private String sharePercent(BigDecimal part, BigDecimal total) {
        if (part == null || isZero(total)) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
            .divide(total, PERCENT_SCALE, ROUNDING)
            .toPlainString();
    }

    private boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    /** Scale-6 strings, matching every report. Null stays null: absent is not zero. */
    private String money(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? null : value.setScale(PERCENT_SCALE, ROUNDING).toPlainString();
    }
}
