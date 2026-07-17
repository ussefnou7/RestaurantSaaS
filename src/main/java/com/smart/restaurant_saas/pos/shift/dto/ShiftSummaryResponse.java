package com.smart.restaurant_saas.pos.shift.dto;

import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * X report (OPEN shift): closedAt, closingCashCounted, and cashVariance are null.
 * Z report (after close): all fields populated.
 */
public record ShiftSummaryResponse(
        Long shiftId,
        ShiftStatus status,
        Long branchId,
        String branchName,
        Long cashierUserId,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal openingCash,
        BigDecimal closingCashCounted,
        BigDecimal expectedCash,
        BigDecimal cashVariance,
        long orderCount,
        BigDecimal totalAmount,
        BigDecimal averageOrderValue,
        Map<String, BigDecimal> totalByPaymentMethod
) {
}
