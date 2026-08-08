package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByHourRow;
import com.smart.restaurant_saas.order.reports.dto.SalesOverTimeRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "When do we actually sell?" — the staffing, shift-planning and promotion-timing view.
 *
 * <p>A restaurant seeing Tuesday at half of Friday can cut labour against it, which is why this is
 * the one report in the family sorted chronologically rather than by magnitude: the shape over time
 * is the finding.
 *
 * <p>Two fixed groupings, two queries, two row types — daily and hourly. Not one query with a
 * granularity parameter: filters narrow scope, but the grouping of a report is fixed and is never a
 * parameter (D86).
 *
 * <p><b>Read-only.</b>
 */
@Service
@RequiredArgsConstructor
public class SalesOverTimeReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderRepository orderRepository;
    private final TenantTimeZoneService tenantTimeZoneService;

    /**
     * Daily series over {@code [dateFrom, dateTo]} (calendar days, both inclusive), ascending.
     *
     * <p>Days with no COMPLETE orders are omitted rather than zero-filled — see
     * {@link SalesOverTimeRow}.
     */
    @Transactional(readOnly = true)
    public List<SalesOverTimeRow> salesOverTime(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                                Long branchId, Long cashierUserId,
                                                OrderType orderType) {
        SalesReportDateRange range = SalesReportDateRange.of(dateFrom, dateTo, tenantTimeZoneService.zoneFor(tenantId));

        return orderRepository.aggregateSalesOverTime(
                tenantId,
                range.fromInclusive(),
                range.toExclusive(),
                branchId,
                cashierUserId,
                orderType == null ? null : orderType.name())
            .stream()
            .map(aggregate -> SalesOverTimeRow.builder()
                .salesDate(aggregate.getSalesDate())
                .orderCount(aggregate.getOrderCount())
                .subtotal(scaled(aggregate.getSubtotal()))
                .taxAmount(scaled(aggregate.getTaxAmount()))
                .totalAmount(scaled(aggregate.getTotalAmount()))
                .averageOrderValue(scaled(aggregate.getAverageOrderValue()))
                .build())
            .toList();
    }

    /**
     * The same series at hourly resolution, ascending by day then hour. Calendar hours — an order at
     * 02:00 belongs to that calendar date (see {@link SalesByHourRow}).
     */
    @Transactional(readOnly = true)
    public List<SalesByHourRow> salesByHour(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                            Long branchId, Long cashierUserId, OrderType orderType) {
        SalesReportDateRange range = SalesReportDateRange.of(dateFrom, dateTo, tenantTimeZoneService.zoneFor(tenantId));

        return orderRepository.aggregateSalesByHour(
                tenantId,
                range.fromInclusive(),
                range.toExclusive(),
                branchId,
                cashierUserId,
                orderType == null ? null : orderType.name())
            .stream()
            .map(aggregate -> SalesByHourRow.builder()
                .salesDate(aggregate.getSalesDate())
                .hourOfDay(aggregate.getHourOfDay())
                .orderCount(aggregate.getOrderCount())
                .subtotal(scaled(aggregate.getSubtotal()))
                .taxAmount(scaled(aggregate.getTaxAmount()))
                .totalAmount(scaled(aggregate.getTotalAmount()))
                .averageOrderValue(scaled(aggregate.getAverageOrderValue()))
                .build())
            .toList();
    }

    private String scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }
}
