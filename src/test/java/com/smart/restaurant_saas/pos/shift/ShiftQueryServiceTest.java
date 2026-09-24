package com.smart.restaurant_saas.pos.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.support.TestScopes;
import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.expense.ExpenseRepository;
import com.smart.restaurant_saas.expense.ShiftExpenseProjection;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import com.smart.restaurant_saas.order.core.OrderRepository;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftQueryServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long BRANCH_ID = 3L;
    private static final Long SHIFT_ID = 55L;

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private SecurityService securityService;
    @Mock
    private TenantTimeZoneService timeZoneService;

    private ShiftQueryService service;

    @BeforeEach
    void setUp() {
        service = new ShiftQueryService(
            shiftRepository,
            orderRepository,
            expenseRepository,
            securityService,
            timeZoneService,
            TestScopes.tenantWide());
    }

    @Test
    void findById_convertsTenantAuditTimeToBranchTimeBeforeLateExpenseClassification() {
        ShiftListProjection header = mock(ShiftListProjection.class);
        when(header.getId()).thenReturn(SHIFT_ID);
        when(header.getBranchId()).thenReturn(BRANCH_ID);
        when(header.getClosedAt()).thenReturn(LocalDateTime.of(2026, 1, 15, 10, 0));
        when(header.getVariance()).thenReturn(new BigDecimal("-30.000000"));
        when(shiftRepository.findListItemById(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(header));

        ShiftExpenseProjection expense = mock(ShiftExpenseProjection.class);
        when(expense.getId()).thenReturn(100L);
        when(expense.getAmount()).thenReturn(new BigDecimal("25.000000"));
        when(expense.getStatus()).thenReturn(ExpenseStatus.ACTIVE);
        when(expense.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 15, 8, 0));
        when(expenseRepository.findByShift(SHIFT_ID, TENANT_ID)).thenReturn(List.of(expense));

        when(securityService.hasPermission("SHIFTS_VIEW_VARIANCE")).thenReturn(true);
        when(timeZoneService.zoneFor(TENANT_ID)).thenReturn(ZoneId.of("UTC"));
        when(timeZoneService.zoneFor(TENANT_ID, BRANCH_ID)).thenReturn(ZoneId.of("Asia/Dubai"));

        var response = service.findById(SHIFT_ID, TENANT_ID);

        assertThat(response.expenses()).singleElement().satisfies(line -> {
            assertThat(line.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 12, 0));
            assertThat(line.recordedAfterClose()).isTrue();
        });
        assertThat(response.lateExpenses()).isEqualByComparingTo("25.000000");
        assertThat(response.explainedVariance()).isEqualByComparingTo("-5.000000");
    }

    /**
     * D123. The regression this pins is not "a variance field leaked" — those were already gated.
     * It is that the rows the variance is computed <em>from</em> were not: the order list carries
     * paymentMethod and totalAmount per order, so
     * {@code openingCount + sum(CASH totals) = expectedCash} exactly. A caller without the
     * permission was handed both terms and could finish the arithmetic themselves, which is the
     * whole of what the blind count withholds.
     */
    @Test
    void findById_withoutVariancePermission_returnsNoOrdersNoExpensesAndNoCounts() {
        ShiftListProjection header = mock(ShiftListProjection.class);
        when(header.getId()).thenReturn(SHIFT_ID);
        when(header.getBranchId()).thenReturn(BRANCH_ID);
        when(shiftRepository.findListItemById(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(header));

        when(securityService.hasPermission("SHIFTS_VIEW_VARIANCE")).thenReturn(false);

        var response = service.findById(SHIFT_ID, TENANT_ID);

        assertThat(response.orders()).isEmpty();
        assertThat(response.expenses()).isEmpty();
        assertThat(response.cashSales()).isNull();
        assertThat(response.salesByPaymentMethod()).isEmpty();
        assertThat(response.expensesAtClose()).isNull();
        assertThat(response.lateExpenses()).isNull();
        assertThat(response.explainedVariance()).isNull();

        // The opening float is the largest single term of expectedCash, and a cashier about to
        // force-close a colleague's drawer would be counting against it (D122).
        assertThat(response.shift().openingCount()).isNull();
        assertThat(response.shift().closingCount()).isNull();
        assertThat(response.shift().expectedCash()).isNull();
        assertThat(response.shift().variance()).isNull();
        assertThat(response.shift().handoverVariance()).isNull();

        // Stronger than the nulls above: the mapper never reads the figures off the projection at
        // all, so there is no populated field for a later edit to forget to clear.
        verify(header, never()).getOpeningCount();
        verify(header, never()).getClosingCount();
        verify(header, never()).getExpectedCash();
        verify(header, never()).getVariance();
        verify(header, never()).getHandoverVariance();

        // Identity still renders: the row is useless as a list entry without it, and none of it
        // is money.
        assertThat(response.shift().id()).isEqualTo(SHIFT_ID);
    }

    /**
     * The queries are skipped, not filtered after the fact. A later edit that fetches the rows
     * "just for the count" and drops them at the DTO would pass the assertion above while putting
     * the data back within one refactor's reach.
     */
    @Test
    void findById_withoutVariancePermission_doesNotEvenQueryTheOrderAndExpenseRows() {
        ShiftListProjection header = mock(ShiftListProjection.class);
        when(header.getId()).thenReturn(SHIFT_ID);
        when(header.getBranchId()).thenReturn(BRANCH_ID);
        when(shiftRepository.findListItemById(SHIFT_ID, TENANT_ID)).thenReturn(Optional.of(header));
        when(securityService.hasPermission("SHIFTS_VIEW_VARIANCE")).thenReturn(false);

        service.findById(SHIFT_ID, TENANT_ID);

        verify(orderRepository, never()).findByShift(SHIFT_ID, TENANT_ID);
        verify(expenseRepository, never()).findByShift(SHIFT_ID, TENANT_ID);
    }
}
