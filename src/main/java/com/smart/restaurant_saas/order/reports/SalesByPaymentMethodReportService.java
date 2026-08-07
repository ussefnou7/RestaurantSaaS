package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByPaymentMethodRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What did we take, and in what form?" — the reconciliation view for delivery platform payouts and
 * card processor fees.
 *
 * <p>Aggregates <b>the same orders</b> as {@link SalesOverTimeReportService#salesOverTime} over the
 * same filters, grouped by method instead of by date, so the two reports' {@code totalAmount} sums
 * are identical by construction. That identity is pinned by a test — see
 * {@link com.smart.restaurant_saas.order.reports.dto.SalesByPaymentMethodRow}.
 *
 * <p><b>Read-only.</b>
 */
@Service
@RequiredArgsConstructor
public class SalesByPaymentMethodReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderRepository orderRepository;

    /**
     * Totals per payment method over {@code [dateFrom, dateTo]} (calendar days, both inclusive),
     * sorted by {@code totalAmount} descending. A missing method lands in {@code UNSPECIFIED} rather
     * than being dropped, so the column always reconciles.
     */
    @Transactional(readOnly = true)
    public List<SalesByPaymentMethodRow> salesByPaymentMethod(Long tenantId, LocalDate dateFrom,
                                                              LocalDate dateTo, Long branchId,
                                                              Long cashierUserId,
                                                              OrderType orderType) {
        SalesReportDateRange range = SalesReportDateRange.of(dateFrom, dateTo);

        return orderRepository.aggregateSalesByPaymentMethod(
                tenantId,
                range.fromInclusive(),
                range.toExclusive(),
                branchId,
                cashierUserId,
                orderType == null ? null : orderType.name())
            .stream()
            .map(aggregate -> SalesByPaymentMethodRow.builder()
                .paymentMethod(aggregate.getPaymentMethod())
                .orderCount(aggregate.getOrderCount())
                .subtotal(scaled(aggregate.getSubtotal()))
                .taxAmount(scaled(aggregate.getTaxAmount()))
                .totalAmount(scaled(aggregate.getTotalAmount()))
                .totalSharePercent(scaled(aggregate.getTotalSharePercent()))
                .build())
            .toList();
    }

    private String scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }
}
