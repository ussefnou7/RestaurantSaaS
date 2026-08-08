package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.common.TestZones;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.pos.shift.dto.OpenShiftRequest;
import com.smart.restaurant_saas.pos.shift.dto.ShiftResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.math.BigDecimal;
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
    private static final Long BRANCH_ID = 101L;
    private static final Long SHIFT_ID = 77L;

    @Mock private CurrentTenantProvider currentTenantProvider;
    @Mock private ShiftRepository shiftRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private ShiftService shiftService;

    @BeforeEach
    void setUp() {
        shiftService = new ShiftService(
                currentTenantProvider,
                shiftRepository,
                branchRepository,
                userRepository,
                orderRepository,
                TestZones.cairo()
        );
    }

    @Test
    void openShift_rejectsWhenShiftAlreadyOpen_carriesFullShiftParams() {
        Shift existing = existingOpenShift();
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> shiftService.openShift(openShiftRequest(), TENANT_ID, BRANCH_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShiftErrorCode.SHIFT_ALREADY_OPEN);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getParams()).containsEntry("shiftId", SHIFT_ID);
                    assertThat(ex.getParams()).containsEntry("branchId", BRANCH_ID);
                    assertThat(ex.getParams()).containsEntry("branchName", "Main Branch");
                    assertThat(ex.getParams()).containsEntry("cashierUserId", USER_ID);
                    assertThat(ex.getParams()).containsEntry("openedAt", existing.getOpenedAt());
                    assertThat(ex.getParams()).containsEntry("openingCash", existing.getOpeningCash());
                    assertThat(ex.getParams()).containsEntry("status", "OPEN");
                });

        verify(shiftRepository, never()).save(any());
    }

    @Test
    void openShift_savesNewShiftWhenNoneOpen() {
        when(shiftRepository.findByCashierUserIdAndTenantIdAndStatus(USER_ID, TENANT_ID, ShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID)).thenReturn(Optional.of(branch()));
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.of(cashier()));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> {
            Shift s = inv.getArgument(0);
            s.setId(SHIFT_ID);
            return s;
        });

        ShiftResponse response = shiftService.openShift(openShiftRequest(), TENANT_ID, BRANCH_ID, USER_ID);

        assertThat(response.status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(response.openingCash()).isEqualByComparingTo("200.000000");
        verify(shiftRepository).save(any(Shift.class));
    }

    // --- helpers ---

    private OpenShiftRequest openShiftRequest() {
        return new OpenShiftRequest(new BigDecimal("200.00"));
    }

    private Shift existingOpenShift() {
        Shift s = new Shift();
        s.setId(SHIFT_ID);
        s.setBranch(branch());
        s.setCashierUser(cashier());
        s.setOpenedAt(LocalDateTime.of(2026, 7, 18, 8, 0));
        s.setOpeningCash(new BigDecimal("500.000000"));
        s.setStatus(ShiftStatus.OPEN);
        return s;
    }

    private Branch branch() {
        Branch b = new Branch();
        b.setId(BRANCH_ID);
        b.setName("Main Branch");
        return b;
    }

    private User cashier() {
        User u = new User();
        u.setId(USER_ID);
        return u;
    }
}
