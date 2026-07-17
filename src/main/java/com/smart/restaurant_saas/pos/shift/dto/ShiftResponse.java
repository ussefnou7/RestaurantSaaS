package com.smart.restaurant_saas.pos.shift.dto;

import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftResponse(
        Long id,
        Long branchId,
        String branchName,
        Long cashierUserId,
        String cashierUserName,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal openingCash,
        BigDecimal closingCashCounted,
        ShiftStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getBranch().getId(),
                shift.getBranch().getName(),
                shift.getCashierUser().getId(),
                shift.getCashierUser().getFullName(),
                shift.getOpenedAt(),
                shift.getClosedAt(),
                shift.getOpeningCash(),
                shift.getClosingCashCounted(),
                shift.getStatus(),
                shift.getCreatedAt(),
                shift.getUpdatedAt()
        );
    }
}
