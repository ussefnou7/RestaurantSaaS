package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.order.core.OrderRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dashboard checks sharing the same domain repositories. */
@Component
@RequiredArgsConstructor
public class OrderAlerts {

    private final OrderRepository orderRepository;

    public List<AlertOccurrence> cancelledAfterCooking(AlertContext context) {
        return ScopedAlerts.perBranch(
            AlertCode.CANCELLED_AFTER_COOKING,
            orderRepository.aggregateAfterCookCancellations(
                context.tenantId(),
                context.branchFilter(),

                context.now().toLocalDate().atStartOfDay(context.zone()).toLocalDateTime()),
            row -> Map.of("stockNotDeducted", true));
    }

    public List<AlertOccurrence> silentBranch(AlertContext context) {
        return ScopedAlerts.perBranch(
            AlertCode.BRANCH_SILENT,
            orderRepository.aggregateSilentTradingBranches(
                context.tenantId(),
                context.branchFilter(),
                context.minutesAgo(AlertThresholds.BRANCH_SILENT_MINUTES)),
            row -> Map.of("silentMinutes", AlertThresholds.BRANCH_SILENT_MINUTES));
    }
}
