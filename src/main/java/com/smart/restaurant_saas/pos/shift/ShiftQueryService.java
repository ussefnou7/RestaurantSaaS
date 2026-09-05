package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.expense.ExpenseRepository;
import com.smart.restaurant_saas.expense.ShiftExpenseProjection;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.order.core.PaymentMethodSummaryProjection;
import com.smart.restaurant_saas.order.core.ShiftOrderProjection;
import com.smart.restaurant_saas.pos.shift.dto.ShiftDetailResponse;
import com.smart.restaurant_saas.pos.shift.dto.ShiftListItemResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two manager-facing read surfaces (D125). Separate from {@link ShiftService} because nothing
 * here writes and nothing here needs a device: these are answered for web sessions, whose tokens
 * carry no {@code deviceId}.
 *
 * <p><b>Variance visibility is decided once, here.</b> {@code SHIFTS_VIEW_VARIANCE} is read at the
 * top of each method and threaded down; the fields are dropped before the DTO is built, so they
 * never reach the wire for a caller who may not see them (D123).
 */
@Service
@RequiredArgsConstructor
public class ShiftQueryService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final String VIEW_VARIANCE = "SHIFTS_VIEW_VARIANCE";

    private final ShiftRepository shiftRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final SecurityService securityService;

    @Transactional(readOnly = true)
    public Page<ShiftListItemResponse> findAll(
            Long tenantId,
            Long branchId,
            Long deviceId,
            Long cashierUserId,
            LocalDate dateFrom,
            LocalDate dateTo,
            ShiftStatus status,
            Boolean forcedClose,
            Pageable pageable) {
        boolean canViewVariance = securityService.hasPermission(VIEW_VARIANCE);
        return shiftRepository
                .findListItems(tenantId, branchId, deviceId, cashierUserId,
                        dateFrom, dateTo, status, forcedClose, pageable)
                .map(projection -> ShiftListItemResponse.from(projection, canViewVariance));
    }

    @Transactional(readOnly = true)
    public ShiftDetailResponse findById(Long id, Long tenantId) {
        boolean canViewVariance = securityService.hasPermission(VIEW_VARIANCE);

        ShiftListProjection header = shiftRepository.findListItemById(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ShiftErrorCode.SHIFT_NOT_FOUND,
                        "Shift not found: " + id,
                        ErrorParams.of("entityType", "Shift", "entityId", id)));

        List<ShiftOrderProjection> orders = orderRepository.findByShift(id, tenantId);
        List<ShiftExpenseProjection> expenses = expenseRepository.findByShift(id, tenantId);

        LocalDateTime closedAt = header.getClosedAt();
        BigDecimal lateExpenses = closedAt == null
                ? null
                : scaled(expenseRepository.sumActiveByShiftRecordedAfter(id, tenantId, closedAt));

        return new ShiftDetailResponse(
                ShiftListItemResponse.from(header, canViewVariance),
                canViewVariance ? salesByPaymentMethod(id, tenantId) : Map.of(),
                canViewVariance ? scaled(orderRepository.sumCompletedCashByShift(id, tenantId)) : null,
                canViewVariance ? header.getExpensesAtClose() : null,
                canViewVariance ? lateExpenses : null,
                canViewVariance ? explainedVariance(header.getVariance(), lateExpenses) : null,
                orders.stream().map(ShiftQueryService::toOrderLine).toList(),
                expenses.stream().map(e -> toExpenseLine(e, closedAt)).toList());
    }

    /**
     * {@code variance + lateExpenses}, rendered beside them and never in place of them (D124).
     *
     * <p>A shift 300 short with 300 of late expenses explains to zero — but the original -300 stays
     * on the row, because the explanation is itself evidence that has to remain reviewable.
     */
    private BigDecimal explainedVariance(BigDecimal variance, BigDecimal lateExpenses) {
        if (variance == null) {
            return null;
        }
        return scaled(variance.add(lateExpenses == null ? BigDecimal.ZERO : lateExpenses));
    }

    private Map<String, BigDecimal> salesByPaymentMethod(Long shiftId, Long tenantId) {
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        for (PaymentMethodSummaryProjection row : orderRepository.aggregateByShift(shiftId, tenantId)) {
            byMethod.put(row.getPaymentMethod(), scaled(row.getTotal()));
        }
        return byMethod;
    }

    private static ShiftDetailResponse.ShiftOrderLine toOrderLine(ShiftOrderProjection o) {
        return new ShiftDetailResponse.ShiftOrderLine(
                o.getId(), o.getOrderNo(), o.getOrderDate(), o.getStatus(),
                o.getPaymentMethod(), o.getTotalAmount(), o.getCreatedBy(), o.getCreatedByName());
    }

    private static ShiftDetailResponse.ShiftExpenseLine toExpenseLine(
            ShiftExpenseProjection e, LocalDateTime shiftClosedAt) {
        boolean late = shiftClosedAt != null
                && e.getCreatedAt() != null
                && e.getCreatedAt().isAfter(shiftClosedAt);
        return new ShiftDetailResponse.ShiftExpenseLine(
                e.getId(), e.getAmount(), e.getExpenseDate(), e.getDescription(), e.getPayeeName(),
                e.getCategoryId(), e.getCategoryName(), e.getStatus(),
                e.getRecordedByUserId(), e.getRecordedByName(), e.getCreatedAt(), late);
    }

    private BigDecimal scaled(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                : value.setScale(SCALE, ROUNDING);
    }
}
