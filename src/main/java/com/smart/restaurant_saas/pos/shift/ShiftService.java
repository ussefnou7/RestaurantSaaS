package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.order.core.PaymentMethodSummaryProjection;
import com.smart.restaurant_saas.pos.shift.dto.CloseShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.ShiftSummaryResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final int CASH_SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final CurrentTenantProvider currentTenantProvider;
    private final ShiftRepository shiftRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TenantTimeZoneService tenantTimeZoneService;

    @Transactional
    public ShiftResponse openShift(OpenShiftRequest request, Long tenantId, Long branchId, Long userId) {
        shiftRepository.findByCashierUserIdAndTenantIdAndStatus(userId, tenantId, ShiftStatus.OPEN)
                .ifPresent(existing -> {
                    throw new BusinessException(ShiftErrorCode.SHIFT_ALREADY_OPEN,
                            "Cashier already has an open shift",
                            ErrorParams.of(
                                    "shiftId",       existing.getId(),
                                    "branchId",      existing.getBranch().getId(),
                                    "branchName",    existing.getBranch().getName(),
                                    "cashierUserId", existing.getCashierUser().getId(),
                                    "openedAt",      existing.getOpenedAt(),
                                    "openingCash",   existing.getOpeningCash(),
                                    "status",        existing.getStatus().name()
                            ));
                });

        Branch branch = loadBranch(branchId, tenantId);
        User cashier = loadUser(userId, tenantId);

        Shift shift = new Shift();
        shift.setTenantId(tenantId);
        shift.setCreatedBy(userId);
        shift.setBranch(branch);
        shift.setCashierUser(cashier);
        shift.setOpenedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId, branchId)));
        shift.setOpeningCash(request.openingCash().setScale(CASH_SCALE, ROUNDING));
        shift.setStatus(ShiftStatus.OPEN);

        return ShiftResponse.from(shiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public ShiftSummaryResponse getCurrentShiftSummary(Long tenantId, Long userId) {
        Shift shift = shiftRepository.findByCashierUserIdAndTenantIdAndStatus(userId, tenantId, ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessException(ShiftErrorCode.NO_OPEN_SHIFT_FOR_CASHIER,
                        "No open shift for cashier: " + userId,
                        ErrorParams.of("userId", userId)));

        List<PaymentMethodSummaryProjection> rows = orderRepository.aggregateByShift(shift.getId(), tenantId);
        return buildSummary(shift, rows, null);
    }

    @Transactional
    public ShiftSummaryResponse closeShift(Long shiftId, CloseShiftRequest request, Long tenantId, Long userId) {
        Shift shift = shiftRepository.findByIdAndTenantId(shiftId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ShiftErrorCode.SHIFT_NOT_FOUND,
                        "Shift not found: " + shiftId,
                        ErrorParams.of("entityType", "Shift", "entityId", shiftId)));

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            throw new BusinessException(ShiftErrorCode.SHIFT_ALREADY_CLOSED,
                    "Shift is already closed: " + shiftId,
                    ErrorParams.of("shiftId", shiftId));
        }

        BigDecimal counted = request.closingCashCounted().setScale(CASH_SCALE, ROUNDING);
        shift.setClosingCashCounted(counted);
        shift.setClosedAt(LocalDateTime.now(
                tenantTimeZoneService.zoneFor(tenantId, shift.getBranch().getId())));
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setUpdatedBy(userId);
        Shift saved = shiftRepository.save(shift);

        List<PaymentMethodSummaryProjection> rows = orderRepository.aggregateByShift(saved.getId(), tenantId);
        return buildSummary(saved, rows, counted);
    }

    private ShiftSummaryResponse buildSummary(Shift shift, List<PaymentMethodSummaryProjection> rows,
                                               BigDecimal closingCashCounted) {
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(CASH_SCALE, ROUNDING);
        long orderCount = 0;
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();

        for (PaymentMethodSummaryProjection row : rows) {
            BigDecimal rowTotal = row.getTotal().setScale(CASH_SCALE, ROUNDING);
            byMethod.put(row.getPaymentMethod(), rowTotal);
            totalAmount = totalAmount.add(rowTotal).setScale(CASH_SCALE, ROUNDING);
            orderCount += row.getOrderCount();
        }

        BigDecimal avg = orderCount == 0
                ? BigDecimal.ZERO.setScale(CASH_SCALE, ROUNDING)
                : totalAmount.divide(BigDecimal.valueOf(orderCount), CASH_SCALE, ROUNDING);

        BigDecimal cashOrders = byMethod.getOrDefault("CASH", BigDecimal.ZERO.setScale(CASH_SCALE, ROUNDING));
        BigDecimal expectedCash = shift.getOpeningCash().add(cashOrders).setScale(CASH_SCALE, ROUNDING);
        BigDecimal cashVariance = closingCashCounted != null
                ? closingCashCounted.subtract(expectedCash).setScale(CASH_SCALE, ROUNDING)
                : null;

        return new ShiftSummaryResponse(
                shift.getId(),
                shift.getStatus(),
                shift.getBranch().getId(),
                shift.getBranch().getName(),
                shift.getCashierUser().getId(),
                shift.getOpenedAt(),
                shift.getClosedAt(),
                shift.getOpeningCash(),
                closingCashCounted,
                expectedCash,
                cashVariance,
                orderCount,
                totalAmount,
                avg,
                byMethod
        );
    }

    private Branch loadBranch(Long branchId, Long tenantId) {
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ShiftErrorCode.SHIFT_NOT_FOUND,
                        "Branch not found: " + branchId,
                        ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private User loadUser(Long userId, Long tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ShiftErrorCode.SHIFT_NOT_FOUND,
                        "User not found: " + userId,
                        ErrorParams.of("entityType", "User", "entityId", userId)));
    }
}
