package com.smart.restaurant_saas.pos.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.CurrentUserService;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.TestZones;
import com.smart.restaurant_saas.device.Device;
import com.smart.restaurant_saas.device.repository.DeviceRepository;
import com.smart.restaurant_saas.expense.ExpenseRepository;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.pos.shift.dto.CloseShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.CurrentShiftResponse;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 11L;
    private static final Long OTHER_USER_ID = 12L;
    private static final Long BRANCH_ID = 101L;
    private static final Long DEVICE_ID = 55L;
    private static final Long SHIFT_ID = 77L;

    @Mock private ShiftRepository shiftRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private CurrentTenantProvider currentTenantProvider;
    @Mock private SecurityService securityService;

    private ShiftService shiftService;

    @BeforeEach
    void setUp() {
        shiftService = new ShiftService(
                shiftRepository,
                deviceRepository,
                userRepository,
                orderRepository,
                expenseRepository,
                TestZones.cairo(),
                currentUserService,
                currentTenantProvider,
                securityService);
    }

    // ---------- open ----------

    /**
     * The count the cashier typed must not be written over the shift they are resuming. The old
     * implementation threw {@code SHIFT_ALREADY_OPEN} here and the POS reconstructed the session
     * from the error, silently discarding that number (D120).
     */
    @Test
    void openShift_sameUserResumes_withoutWritingANewRow() {
        callerIs(USER_ID);
        Shift existing = openShiftOwnedBy(USER_ID, new BigDecimal("500.000000"));
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(user(USER_ID, "Sara")));

        ShiftResponse response = shiftService.openShift(new OpenShiftRequest(new BigDecimal("200.00")), TENANT_ID);

        assertThat(response.id()).isEqualTo(SHIFT_ID);
        assertThat(response.status()).isEqualTo(ShiftStatus.OPEN);
        // The resumed shift keeps its own opening count; the submitted 200 is not written anywhere.
        assertThat(response.openingCount()).isEqualByComparingTo("500.000000");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void openShift_anotherUsersShift_failsWithADistinctCodeAndTheDetailsAForceCloseNeeds() {
        callerIs(USER_ID);
        Shift existing = openShiftOwnedBy(OTHER_USER_ID, new BigDecimal("500.000000"));
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByIdAndTenantId(OTHER_USER_ID, TENANT_ID))
                .thenReturn(Optional.of(user(OTHER_USER_ID, "Omar")));

        assertThatThrownBy(() -> shiftService.openShift(new OpenShiftRequest(new BigDecimal("200.00")), TENANT_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_OPEN_BY_ANOTHER_USER);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getParams()).containsEntry("shiftId", SHIFT_ID);
                    assertThat(ex.getParams()).containsEntry("openedByUserId", OTHER_USER_ID);
                    assertThat(ex.getParams()).containsEntry("openedByUserName", "Omar");
                });

        verify(shiftRepository, never()).save(any());
    }

    /** D121: null, never zero — the two mean opposite things. */
    @Test
    void openShift_firstShiftOnADevice_leavesHandoverVarianceNull() {
        callerIs(USER_ID);
        noOpenShiftOnDevice();
        when(shiftRepository.findTopByDeviceIdAndTenantIdAndStatusOrderByClosedAtDesc(
                DEVICE_ID, TENANT_ID, ShiftStatus.CLOSED)).thenReturn(Optional.empty());
        stubDeviceAndSave();

        shiftService.openShift(new OpenShiftRequest(new BigDecimal("200.00")), TENANT_ID);

        assertThat(savedShift().getHandoverVariance()).isNull();
    }

    @Test
    void openShift_afterAPreviousClose_measuresHandoverAgainstThatClosingCount() {
        callerIs(USER_ID);
        noOpenShiftOnDevice();
        Shift previous = new Shift();
        previous.setClosingCount(new BigDecimal("180.000000"));
        when(shiftRepository.findTopByDeviceIdAndTenantIdAndStatusOrderByClosedAtDesc(
                DEVICE_ID, TENANT_ID, ShiftStatus.CLOSED)).thenReturn(Optional.of(previous));
        stubDeviceAndSave();

        shiftService.openShift(new OpenShiftRequest(new BigDecimal("200.00")), TENANT_ID);

        assertThat(savedShift().getHandoverVariance()).isEqualByComparingTo("20.000000");
    }

    // ---------- current ----------

    /**
     * No open shift is the first login of the day, not an error. The client's three-way branch
     * reads this body; branching on a thrown code would make the normal path an exception.
     */
    @Test
    void getCurrentShift_withNoOpenShift_returnsAnEmptyResultRatherThanThrowing() {
        callerIs(USER_ID);
        noOpenShiftOnDevice();

        CurrentShiftResponse response = shiftService.getCurrentShift(TENANT_ID);

        assertThat(response.shift()).isNull();
    }

    /** D122/D123: the force-closer counts blind, so the colleague's opening float is withheld. */
    @Test
    void getCurrentShift_ofAnotherUser_returnsIdentityButNotTheOpeningCount() {
        callerIs(USER_ID);
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(openShiftOwnedBy(OTHER_USER_ID, new BigDecimal("500.000000"))));
        when(userRepository.findByIdAndTenantId(OTHER_USER_ID, TENANT_ID))
                .thenReturn(Optional.of(user(OTHER_USER_ID, "Omar")));

        CurrentShiftResponse response = shiftService.getCurrentShift(TENANT_ID);

        assertThat(response.shift().openedByUserId()).isEqualTo(OTHER_USER_ID);
        assertThat(response.shift().openedByUserName()).isEqualTo("Omar");
        assertThat(response.shift().openingCount()).isNull();
    }

    // ---------- close ----------

    @Test
    void closeShift_storesExpectedCashAndVarianceButReturnsNeither() {
        callerIs(USER_ID);
        stubCloseOf(openShiftOwnedBy(USER_ID, new BigDecimal("100.000000")));
        when(securityService.hasPermission("SHIFTS_CLOSE")).thenReturn(true);
        when(orderRepository.sumCompletedCashByShift(SHIFT_ID, TENANT_ID)).thenReturn(new BigDecimal("400.00"));
        when(expenseRepository.sumActiveByShift(SHIFT_ID, TENANT_ID)).thenReturn(new BigDecimal("50.00"));

        ShiftResponse response = shiftService.closeShift(
                SHIFT_ID, new CloseShiftRequest(new BigDecimal("430.00")), TENANT_ID);

        // 100 + 400 - 50 = 450 expected; counted 430 is 20 short.
        Shift saved = savedShift();
        assertThat(saved.getExpectedCash()).isEqualByComparingTo("450.000000");
        assertThat(saved.getVariance()).isEqualByComparingTo("-20.000000");
        assertThat(saved.getExpensesAtClose()).isEqualByComparingTo("50.000000");
        assertThat(saved.getStatus()).isEqualTo(ShiftStatus.CLOSED);

        // D123 — the response type has no member that could carry either figure.
        assertThat(ShiftResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("expectedCash", "variance", "handoverVariance", "expensesAtClose");
        assertThat(response.closingCount()).isEqualByComparingTo("430.000000");
    }

    @Test
    void closeShift_byAnotherUserWithoutForceClosePermission_isRejected() {
        callerIs(USER_ID);
        stubLoadOf(openShiftOwnedBy(OTHER_USER_ID, new BigDecimal("100.000000")));
        when(securityService.hasPermission("SHIFTS_FORCE_CLOSE")).thenReturn(false);

        assertThatThrownBy(() -> shiftService.closeShift(
                SHIFT_ID, new CloseShiftRequest(new BigDecimal("430.00")), TENANT_ID))
                .isInstanceOfSatisfying(AuthorizationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_FORCE_CLOSE_NOT_PERMITTED);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getParams()).containsEntry("requiredPermission", "SHIFTS_FORCE_CLOSE");
                });

        verify(shiftRepository, never()).save(any());
    }

    @Test
    void closeShift_byAnotherUserWithForceClosePermission_setsForcedClose() {
        callerIs(USER_ID);
        stubCloseOf(openShiftOwnedBy(OTHER_USER_ID, new BigDecimal("100.000000")));
        when(securityService.hasPermission("SHIFTS_FORCE_CLOSE")).thenReturn(true);
        when(orderRepository.sumCompletedCashByShift(SHIFT_ID, TENANT_ID)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumActiveByShift(SHIFT_ID, TENANT_ID)).thenReturn(BigDecimal.ZERO);

        shiftService.closeShift(SHIFT_ID, new CloseShiftRequest(new BigDecimal("100.00")), TENANT_ID);

        Shift saved = savedShift();
        assertThat(saved.getForcedClose()).isTrue();
        assertThat(saved.getClosedByUserId()).isEqualTo(USER_ID);
        assertThat(saved.getOpenedByUserId()).isEqualTo(OTHER_USER_ID);
    }

    /** Own shift, so {@code SHIFTS_CLOSE} is the permission that applies — not force close. */
    @Test
    void closeShift_ownShiftWithoutClosePermission_isRejectedEvenWithForceClose() {
        callerIs(USER_ID);
        stubLoadOf(openShiftOwnedBy(USER_ID, new BigDecimal("100.000000")));
        when(securityService.hasPermission("SHIFTS_CLOSE")).thenReturn(false);

        assertThatThrownBy(() -> shiftService.closeShift(
                SHIFT_ID, new CloseShiftRequest(new BigDecimal("100.00")), TENANT_ID))
                .isInstanceOfSatisfying(AuthorizationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_CLOSE_NOT_PERMITTED));
    }

    @Test
    void closeShift_alreadyClosed_isRejected() {
        callerIs(USER_ID);
        Shift closed = openShiftOwnedBy(USER_ID, new BigDecimal("100.000000"));
        closed.setStatus(ShiftStatus.CLOSED);
        closed.setClosedAt(LocalDateTime.of(2026, 9, 1, 2, 0));
        stubLoadOf(closed);

        assertThatThrownBy(() -> shiftService.closeShift(
                SHIFT_ID, new CloseShiftRequest(new BigDecimal("100.00")), TENANT_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_ALREADY_CLOSED));

        verify(shiftRepository, never()).save(any());
    }

    /** Closing happens at the drawer being counted; another device's shift is not visible here. */
    @Test
    void closeShift_shiftOnAnotherDevice_isNotFound() {
        callerIs(USER_ID);
        Shift elsewhere = openShiftOwnedBy(USER_ID, new BigDecimal("100.000000"));
        elsewhere.getDevice().setId(999L);
        stubLoadOf(elsewhere);

        assertThatThrownBy(() -> shiftService.closeShift(
                SHIFT_ID, new CloseShiftRequest(new BigDecimal("100.00")), TENANT_ID))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_NOT_FOUND));

        verify(shiftRepository, never()).save(any());
    }

    // ---------- helpers ----------

    private void callerIs(Long userId) {
        when(currentUserService.requireCurrentDeviceId()).thenReturn(DEVICE_ID);
        when(currentTenantProvider.getActorUserId()).thenReturn(userId);
    }

    private void noOpenShiftOnDevice() {
        when(shiftRepository.findByDeviceIdAndTenantIdAndStatus(DEVICE_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());
    }

    private void stubDeviceAndSave() {
        when(deviceRepository.findByIdAndTenantId(DEVICE_ID, TENANT_ID)).thenReturn(Optional.of(device()));
        when(userRepository.findByIdAndTenantId(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(user(USER_ID, "Sara")));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> {
            Shift s = inv.getArgument(0);
            s.setId(SHIFT_ID);
            return s;
        });
    }

    private void stubLoadOf(Shift shift) {
        when(shiftRepository.findByIdAndTenantId(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(shift));
    }

    private void stubCloseOf(Shift shift) {
        stubLoadOf(shift);
        when(userRepository.findByIdAndTenantId(eq(shift.getOpenedByUserId()), eq(TENANT_ID)))
                .thenReturn(Optional.of(user(shift.getOpenedByUserId(), "Sara")));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Shift savedShift() {
        org.mockito.ArgumentCaptor<Shift> captor = org.mockito.ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        return captor.getValue();
    }

    private Shift openShiftOwnedBy(Long userId, BigDecimal openingCount) {
        Shift s = new Shift();
        s.setId(SHIFT_ID);
        s.setTenantId(TENANT_ID);
        s.setDevice(device());
        s.setBusinessDate(LocalDate.of(2026, 8, 31));
        s.setOpenedByUserId(userId);
        s.setForcedClose(false);
        s.setOpeningCount(openingCount);
        s.setStatus(ShiftStatus.OPEN);
        s.setOpenedAt(LocalDateTime.of(2026, 8, 31, 22, 0));
        return s;
    }

    private Device device() {
        Device d = new Device();
        d.setId(DEVICE_ID);
        d.setName("Till 1");
        d.setBranch(branch());
        return d;
    }

    private Branch branch() {
        Branch b = new Branch();
        b.setId(BRANCH_ID);
        b.setName("Main Branch");
        return b;
    }

    private User user(Long id, String fullName) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        return u;
    }
}
