package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.OrderLineRepository;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByProductRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What actually sells?" — the menu-trimming and marketing view, and the base the food-cost /
 * menu-engineering report will build on once recipe costing exists.
 *
 * <p><b>Pre-tax, necessarily.</b> Tax is stored on the order, not the line, so this report sums
 * {@code lineTotal} and says so loudly — see {@link SalesByProductRow}. It reconciles with the
 * sales-over-time report's {@code subtotal}, not its {@code totalAmount}.
 *
 * <p><b>Read-only.</b>
 */
@Service
@RequiredArgsConstructor
public class SalesByProductReportService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final OrderLineRepository orderLineRepository;

    /**
     * Revenue per product over {@code [dateFrom, dateTo]} (calendar days, both inclusive), sorted by
     * revenue descending.
     *
     * <p>Share is computed against the summed {@code lineTotal} inside the same filtered scope, so
     * the percentages add to 100 no matter how the filters narrow it.
     */
    @Transactional(readOnly = true)
    public List<SalesByProductRow> salesByProduct(Long tenantId, LocalDate dateFrom, LocalDate dateTo,
                                                  Long branchId, Long cashierUserId,
                                                  OrderType orderType) {
        SalesReportDateRange range = SalesReportDateRange.of(dateFrom, dateTo);

        return orderLineRepository.aggregateSalesByProduct(
                tenantId,
                range.fromInclusive(),
                range.toExclusive(),
                branchId,
                cashierUserId,
                orderType == null ? null : orderType.name())
            .stream()
            .map(aggregate -> SalesByProductRow.builder()
                .productId(aggregate.getProductId())
                .productName(aggregate.getProductName())
                .quantitySold(scaled(aggregate.getQuantitySold()))
                .revenue(scaled(aggregate.getRevenue()))
                .revenueSharePercent(scaled(aggregate.getRevenueSharePercent()))
                .build())
            .toList();
    }

    private String scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, ROUNDING).toPlainString();
    }
}
