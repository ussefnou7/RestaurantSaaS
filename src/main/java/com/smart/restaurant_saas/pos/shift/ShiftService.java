package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.auth.service.CurrentUserService;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.expense.ExpenseRepository;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.pos.shift.dto.CloseShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.CurrentShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Open, resume and close a drawer's shift.
 *
 * <p><b>The device is never read from a header, a body or the branch.</b> Every path here takes it
 * from {@link CurrentUserService#requireCurrentDeviceId()}, which reads the signed token claim and
 * rejects its absence. A token without a {@code deviceId} is a web session: it has no drawer, so a
 * manager cannot open, close or force-close from the admin web. That is intended -- these actions
 * require standing at the drawer (D127).
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final ShiftRepository shiftRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final TenantTimeZoneService timeZoneService;
    private final CurrentUserService currentUserService;
    private final CurrentTenantProvider currentTenantProvider;
    private final SecurityService securityService;

    /**
     * Open a shift on the calling device, or resume the caller's own open one.
     *
     * <p>Resuming writes nothing and returns the existing shift. The previous implementation threw
     * {@code SHIFT_ALREADY_OPEN}, and the POS then reconstructed the session from the error's
     * params -- discarding the count the cashier had just typed. A figure the system throws away
     * is never acceptable, so the count is not asked for until the client knows it is needed
     * (D120).
     */
    @Transactional
    public ShiftResponse openShift(OpenShiftRequest request, Long tenantId) {
        Long deviceId = currentUserService.requireCurrentDeviceId();
        Long userId = currentTenantProvider.getActorUserId();

        Optional<Shift> openOnDevice =
                shiftRepository.findByDeviceIdAndTenantIdAndStatus(deviceId, tenantId, ShiftStatus.OPEN);

        if (openOnDevice.isPresent()) {
            Shift existing = openOnDevice.get();
            if (userId.equals(existing.getOpenedByUserId())) {
                return ShiftResponse.of(existing, resolveUserName(userId, tenantId), true);
            }
            // Somebody else's drawer. Distinct from a resume because the client's next move is
            // different: a force close, which is a separate call under a separate permission.
            throw new BusinessException(ShiftErrorCode.SHIFT_OPEN_BY_ANOTHER_USER,
                    "Device " + deviceId + " has an open shift belonging to user "
                            + existing.getOpenedByUserId(),
                    ErrorParams.of(
                            "shiftId", existing.getId(),
                            "deviceId", deviceId,
                            "openedByUserId", existing.getOpenedByUserId(),
                            "openedByUserName", resolveUserName(existing.getOpenedByUserId(), tenantId),
                            "openedAt", existing.getOpenedAt()));
        }

        // Loaded for the branch, which the zone and the response both need -- not to re-check the
        // device. Tenant ownership and active state were already validated for this request by the
        // authentication filter's single account lookup (D127); a second check here would be a
        // second source of truth for the same question.
        Device device = loadDevice(deviceId, tenantId);
        ZoneId zone = timeZoneService.zoneFor(tenantId, device.getBranch().getId());

        BigDecimal openingCount = scaled(request.openingCount());

        Shift shift = new Shift();
        shift.setTenantId(tenantId);
        shift.setCreatedBy(userId);
        shift.setDevice(device);
        shift.setBusinessDate(resolveBusinessDate(zone));
        shift.setOpenedByUserId(userId);
        shift.setForcedClose(false);
        shift.setOpeningCount(openingCount);
        shift.setHandoverVariance(resolveHandoverVariance(deviceId, tenantId, openingCount));
        shift.setStatus(ShiftStatus.OPEN);
        shift.setOpenedAt(LocalDateTime.now(zone));

        Shift saved = shiftRepository.save(shift);
        return ShiftResponse.of(saved, resolveUserName(userId, tenantId), true);
    }

    /**
     * The open shift on the calling device, whoever it belongs to, or an empty result.
     *
     * <p>Returns the shift even when it belongs to another cashier: that is how the client tells
     * a resume from a force close without branching on an error code. The colleague's opening
     * count is withheld -- see {@link ShiftResponse}.
     */
    @Transactional(readOnly = true)
    public CurrentShiftResponse getCurrentShift(Long tenantId) {
        Long deviceId = currentUserService.requireCurrentDeviceId();
        Long userId = currentTenantProvider.getActorUserId();

        return shiftRepository.findByDeviceIdAndTenantIdAndStatus(deviceId, tenantId, ShiftStatus.OPEN)
                .map(shift -> CurrentShiftResponse.of(ShiftResponse.of(
                        shift,
                        resolveUserName(shift.getOpenedByUserId(), tenantId),
                        userId.equals(shift.getOpenedByUserId()))))
                .orElseGet(CurrentShiftResponse::empty);
    }

    /**
     * Count and close. The expected figure and the variance are computed, stored, and not returned
     * (D123) -- they are not members of {@link ShiftResponse} at all.
     *
     * <p><b>The empty-sync-queue precondition (D126) is not verified here and cannot be.</b> The
     * queue lives in the device's local storage and exposes no watermark or flush acknowledgement,
     * so the server cannot distinguish an empty queue from orders a device has not sent. The rule
     * is enforced by the POS. Anyone reading the variance this method stores must not assume the
     * order set was complete when it was computed.
     */
    @Transactional
    public ShiftResponse closeShift(Long shiftId, CloseShiftRequest request, Long tenantId) {
        Long deviceId = currentUserService.requireCurrentDeviceId();
        Long userId = currentTenantProvider.getActorUserId();

        Shift shift = shiftRepository.findByIdAndTenantId(shiftId, tenantId)
                .orElseThrow(() -> shiftNotFound(shiftId));

        // Scoped to the calling drawer. A shift on another device is reported as not found rather
        // than forbidden: closing happens at the drawer being counted, and answering "exists, but
        // not yours" would confirm shifts on devices the caller is not standing at.
        if (!deviceId.equals(shift.getDevice().getId())) {
            throw shiftNotFound(shiftId);
        }

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            throw new BusinessException(ShiftErrorCode.SHIFT_ALREADY_CLOSED,
                    "Shift is already closed: " + shiftId,
                    ErrorParams.of("shiftId", shiftId, "closedAt", shift.getClosedAt()));
        }

        boolean forcedClose = !userId.equals(shift.getOpenedByUserId());
        requireClosePermission(forcedClose, shift, userId);

        ZoneId zone = timeZoneService.zoneFor(tenantId, shift.getDevice().getBranch().getId());

        BigDecimal closingCount = scaled(request.closingCount());
        BigDecimal cashSales = scaled(orderRepository.sumCompletedCashByShift(shiftId, tenantId));
        // Frozen in this transaction. Expenses recorded against the shift after this point are
        // stored and linked, and do not move this figure (D124).
        BigDecimal expenses = scaled(expenseRepository.sumActiveByShift(shiftId, tenantId));

        BigDecimal expectedCash = scaled(shift.getOpeningCount().add(cashSales).subtract(expenses));
        BigDecimal variance = scaled(closingCount.subtract(expectedCash));

        shift.setClosingCount(closingCount);
        shift.setExpectedCash(expectedCash);
        shift.setVariance(variance);
        shift.setExpensesAtClose(expenses);
        shift.setClosedByUserId(userId);
        shift.setForcedClose(forcedClose);
        shift.setClosedAt(LocalDateTime.now(zone));
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setUpdatedBy(userId);

        Shift saved = shiftRepository.save(shift);
        return ShiftResponse.of(
                saved,
                resolveUserName(saved.getOpenedByUserId(), tenantId),
                !forcedClose);
    }

    /**
     * {@code SHIFTS_CLOSE} closes your own shift; {@code SHIFTS_FORCE_CLOSE} closes a colleague's.
     * Neither implies the other, so holding only one does not widen into the other's case.
     *
     * <p>This cannot be a {@code @PreAuthorize} gate: which permission applies depends on who
     * opened the shift being closed, which is only known after it is loaded.
     */
    private void requireClosePermission(boolean forcedClose, Shift shift, Long userId) {
        if (forcedClose) {
            if (!securityService.hasPermission("SHIFTS_FORCE_CLOSE")) {
                throw new AuthorizationException(ShiftErrorCode.SHIFT_FORCE_CLOSE_NOT_PERMITTED,
                        "User " + userId + " cannot force close shift " + shift.getId(),
                        ErrorParams.of(
                                "shiftId", shift.getId(),
                                "openedByUserId", shift.getOpenedByUserId(),
                                "requiredPermission", "SHIFTS_FORCE_CLOSE"));
            }
            return;
        }
        if (!securityService.hasPermission("SHIFTS_CLOSE")) {
            throw new AuthorizationException(ShiftErrorCode.SHIFT_CLOSE_NOT_PERMITTED,
                    "User " + userId + " cannot close shift " + shift.getId(),
                    ErrorParams.of(
                            "shiftId", shift.getId(),
                            "requiredPermission", "SHIFTS_CLOSE"));
        }
    }

    /**
     * D120's rule reads: inherit the business date from an open shift on this device, otherwise
     * take today in the branch zone.
     *
     * <p><b>The inherit branch is unreachable and is deliberately not written.</b> This method is
     * only called when creating a shift, and creation only happens after the caller has
     * established that no open shift exists on the device -- a state the partial unique index
     * {@code uk_shift_open_per_device} also enforces. Coding the branch anyway would be dead code
     * that reads as a live rule. See the Part A report: the overnight-continuity case D120
     * describes is carried by the date being fixed at open, not by inheritance.
     */
    private LocalDate resolveBusinessDate(ZoneId zone) {
        return LocalDate.now(zone);
    }

    /**
     * {@code openingCount - the previous closed shift's closingCount} (D121).
     *
     * <p>Null on a device's first ever shift. Returned as null rather than zero because the two
     * mean opposite things: zero is "the drawer was counted and nothing had moved", null is "there
     * is no prior count to compare against". Defaulting the second to the first fabricates the
     * strongest finding the module produces.
     */
    private BigDecimal resolveHandoverVariance(Long deviceId, Long tenantId, BigDecimal openingCount) {
        return shiftRepository
                .findTopByDeviceIdAndTenantIdAndStatusOrderByClosedAtDesc(
                        deviceId, tenantId, ShiftStatus.CLOSED)
                .map(previous -> scaled(openingCount.subtract(previous.getClosingCount())))
                .orElse(null);
    }

    private Device loadDevice(Long deviceId, Long tenantId) {
        return deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ShiftErrorCode.DEVICE_NOT_FOUND,
                        "Device not found for tenant: " + deviceId,
                        ErrorParams.of("entityType", "Device", "entityId", deviceId)));
    }

    /** Resolved on read, never stored on the shift -- a denormalised name goes stale (D124). */
    private String resolveUserName(Long userId, Long tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .map(user -> user.getFullName())
                .orElse(null);
    }

    private ResourceNotFoundException shiftNotFound(Long shiftId) {
        return new ResourceNotFoundException(ShiftErrorCode.SHIFT_NOT_FOUND,
                "Shift not found: " + shiftId,
                ErrorParams.of("entityType", "Shift", "entityId", shiftId));
    }

    private BigDecimal scaled(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                : value.setScale(SCALE, ROUNDING);
    }
}
