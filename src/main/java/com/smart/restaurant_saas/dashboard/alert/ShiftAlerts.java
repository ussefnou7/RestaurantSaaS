package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dashboard checks sharing the same domain repositories. */
@Component
@RequiredArgsConstructor
public class ShiftAlerts {

    private final ShiftRepository shiftRepository;

    public List<AlertOccurrence> shiftOpenTooLong(AlertContext context) {
        return ScopedAlerts.perBranch(
            AlertCode.SHIFT_OPEN_TOO_LONG,
            shiftRepository.aggregateLongOpenShifts(
                context.tenantId(),
                context.branchFilter(),
                context.hoursAgo(AlertThresholds.SHIFT_OPEN_HOURS)),
            row -> Map.of("thresholdHours", AlertThresholds.SHIFT_OPEN_HOURS));
    }

    public List<AlertOccurrence> cashVariance(AlertContext context) {
        return ScopedAlerts.perBranch(
            AlertCode.CASH_VARIANCE,
            shiftRepository.aggregateCashVariances(
                context.tenantId(),
                context.branchFilter(),
                context.daysAgo(AlertThresholds.CASH_VARIANCE_LOOKBACK_DAYS),
                AlertThresholds.CASH_VARIANCE_ABSOLUTE,
                AlertThresholds.CASH_VARIANCE_RELATIVE),
            row -> Map.of("lookbackDays", AlertThresholds.CASH_VARIANCE_LOOKBACK_DAYS));
    }

    public List<AlertOccurrence> cashierShortagePattern(AlertContext context) {
        return shiftRepository.aggregateCashierShortagePatterns(
                context.tenantId(),
                context.branchFilter(),
                context.daysAgo(AlertThresholds.CASHIER_PATTERN_WINDOW_DAYS),
                AlertThresholds.CASH_VARIANCE_ABSOLUTE,
                AlertThresholds.CASHIER_PATTERN_MIN_SHIFTS)
            .stream()
            .map(row -> new AlertOccurrence(
                AlertCode.CASHIER_SHORTAGE_PATTERN,
                row.getBranchId(),
                null,
                row.getShortShiftCount(),
                row.getTotalShortfall(),
                row.getOldestAt(),
                Map.of(
                    "shiftCount", row.getShortShiftCount(),
                    "windowDays", AlertThresholds.CASHIER_PATTERN_WINDOW_DAYS,

                    "cashierName", row.getCashierName() == null ? "" : row.getCashierName(),
                    "cashierUserId", row.getCashierUserId()),
                Map.of(
                    "branchId", row.getBranchId(),
                    "cashierUserId", row.getCashierUserId())))
            .toList();
    }
}
