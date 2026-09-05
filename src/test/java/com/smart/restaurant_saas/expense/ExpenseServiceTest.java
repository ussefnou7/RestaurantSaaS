package com.smart.restaurant_saas.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.expense.category.ExpenseCategory;
import com.smart.restaurant_saas.expense.category.ExpenseCategoryRepository;
import com.smart.restaurant_saas.expense.core.ExpenseErrorCode;
import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseSourceType;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import com.smart.restaurant_saas.expense.dto.CreateExpenseRequest;
import com.smart.restaurant_saas.expense.dto.ExpenseResponse;
import com.smart.restaurant_saas.expense.mapper.ExpenseMapper;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long OTHER_TENANT_ID = 8L;
    private static final Long EXPENSE_ID = 100L;
    private static final Long CATEGORY_ID = 20L;
    private static final Long BRANCH_ID = 3L;
    private static final Long USER_ID = 9L;

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseCategoryRepository categoryRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private CurrentTenantProvider currentTenantProvider;
    @Mock
    private TenantTimeZoneService timeZoneService;

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(
            expenseRepository,
            categoryRepository,
            branchRepository,
            shiftRepository,
            currentTenantProvider,
            timeZoneService,
            new ExpenseMapper());
    }

    @Test
    void create_withNullBranch_succeedsAsCompanyExpense() {
        CreateExpenseRequest request = request(null);
        stubAvailableCategory(true);
        when(timeZoneService.zoneFor(TENANT_ID, null)).thenReturn(ZoneId.of("Africa/Cairo"));
        when(currentTenantProvider.getActorUserId()).thenReturn(USER_ID);
        stubSaveAndProjection(ExpenseStatus.ACTIVE);

        ExpenseResponse response = service.create(request, TENANT_ID);

        assertThat(response.getId()).isEqualTo(EXPENSE_ID);
        assertThat(response.getBranchId()).isNull();
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo(ExpenseSourceType.MANUAL);
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpenseStatus.ACTIVE);
        assertThat(captor.getValue().getSourceId()).isNull();
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(USER_ID);
        verify(branchRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void create_withAnotherTenantsCategory_isRejectedWithoutLeakingIt() {
        CreateExpenseRequest request = request(null);
        when(categoryRepository.findAvailableById(CATEGORY_ID, TENANT_ID))
            .thenReturn(Optional.empty());

        assertError(
            () -> service.create(request, TENANT_ID),
            ResourceNotFoundException.class,
            ExpenseErrorCode.EXPENSE_CATEGORY_NOT_FOUND);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void create_withAnotherTenantsBranch_isRejected() {
        CreateExpenseRequest request = request(BRANCH_ID);
        stubAvailableCategory(true);
        when(branchRepository.findByIdAndTenantId(BRANCH_ID, TENANT_ID))
            .thenReturn(Optional.empty());

        assertError(
            () -> service.create(request, TENANT_ID),
            ResourceNotFoundException.class,
            ExpenseErrorCode.BRANCH_NOT_FOUND);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void create_withInactiveCategory_isRejectedBeforeBranchLookup() {
        CreateExpenseRequest request = request(BRANCH_ID);
        stubAvailableCategory(false);

        assertError(
            () -> service.create(request, TENANT_ID),
            BusinessException.class,
            ExpenseErrorCode.EXPENSE_CATEGORY_INACTIVE);
        verify(branchRepository, never()).findByIdAndTenantId(any(), any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void create_usesNonUtcTenantDateForTodayAndFutureValidation() {
        ZoneId serverZone = ZoneId.systemDefault();
        LocalDate serverToday = LocalDate.now(serverZone);
        ZoneId tenantZone = List.of(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Etc/GMT+12"))
            .stream()
            .filter(zone -> !LocalDate.now(zone).equals(serverToday))
            .findFirst()
            .orElseThrow();
        LocalDate tenantToday = LocalDate.now(tenantZone);

        stubAvailableCategory(true);
        when(timeZoneService.zoneFor(TENANT_ID, null)).thenReturn(tenantZone);
        when(currentTenantProvider.getActorUserId()).thenReturn(USER_ID);
        stubSaveAndProjection(ExpenseStatus.ACTIVE);

        CreateExpenseRequest todayRequest = request(null);
        todayRequest.setExpenseDate(tenantToday);
        assertThat(service.create(todayRequest, TENANT_ID).getId()).isEqualTo(EXPENSE_ID);

        CreateExpenseRequest futureRequest = request(null);
        futureRequest.setExpenseDate(tenantToday.plusDays(1));
        assertError(
            () -> service.create(futureRequest, TENANT_ID),
            ValidationException.class,
            ExpenseErrorCode.EXPENSE_DATE_IN_FUTURE);

        assertThat(tenantZone).isNotEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void voidActiveManualExpense_stampsTraceInBranchZone() {
        Expense expense = activeExpense();
        expense.setBranchId(BRANCH_ID);
        when(expenseRepository.findByIdAndTenantId(EXPENSE_ID, TENANT_ID))
            .thenReturn(Optional.of(expense));
        when(timeZoneService.zoneFor(TENANT_ID, BRANCH_ID)).thenReturn(ZoneId.of("Asia/Tokyo"));
        when(expenseRepository.save(expense)).thenReturn(expense);
        ExpenseListProjection projection = projection(ExpenseStatus.VOIDED);
        when(expenseRepository.findListItemById(EXPENSE_ID, TENANT_ID))
            .thenReturn(Optional.of(projection));
        when(currentTenantProvider.getActorUserId()).thenReturn(USER_ID);
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));

        ExpenseResponse response = service.voidExpense(
            EXPENSE_ID, TENANT_ID, "Duplicate receipt");

        LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.VOIDED);
        assertThat(expense.getVoidedBy()).isEqualTo(USER_ID);
        assertThat(expense.getVoidReason()).isEqualTo("Duplicate receipt");
        assertThat(expense.getVoidedAt()).isBetween(before, after);
        assertThat(response.getStatus()).isEqualTo(ExpenseStatus.VOIDED);
    }

    @Test
    void voidAlreadyVoidedExpense_isRejected() {
        Expense expense = activeExpense();
        expense.setStatus(ExpenseStatus.VOIDED);
        expense.setVoidedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(expenseRepository.findByIdAndTenantId(EXPENSE_ID, TENANT_ID))
            .thenReturn(Optional.of(expense));

        assertError(
            () -> service.voidExpense(EXPENSE_ID, TENANT_ID, "Again"),
            BusinessException.class,
            ExpenseErrorCode.EXPENSE_ALREADY_VOIDED);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void voidWithBlankOrAbsentReason_isRejected() {
        when(expenseRepository.findByIdAndTenantId(EXPENSE_ID, TENANT_ID))
            .thenReturn(Optional.of(activeExpense()));

        assertError(
            () -> service.voidExpense(EXPENSE_ID, TENANT_ID, null),
            ValidationException.class,
            ExpenseErrorCode.EXPENSE_VOID_REASON_REQUIRED);
        assertError(
            () -> service.voidExpense(EXPENSE_ID, TENANT_ID, "   "),
            ValidationException.class,
            ExpenseErrorCode.EXPENSE_VOID_REASON_REQUIRED);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void tenantIsolation_appliesToExpenseReadsAndVoids() {
        when(expenseRepository.findByIdAndTenantId(EXPENSE_ID, OTHER_TENANT_ID))
            .thenReturn(Optional.empty());
        when(expenseRepository.findListItemById(EXPENSE_ID, OTHER_TENANT_ID))
            .thenReturn(Optional.empty());

        assertError(
            () -> service.findById(EXPENSE_ID, OTHER_TENANT_ID),
            ResourceNotFoundException.class,
            ExpenseErrorCode.EXPENSE_NOT_FOUND);
        assertError(
            () -> service.voidExpense(EXPENSE_ID, OTHER_TENANT_ID, "Wrong tenant"),
            ResourceNotFoundException.class,
            ExpenseErrorCode.EXPENSE_NOT_FOUND);
    }

    private void stubAvailableCategory(boolean active) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(CATEGORY_ID);
        category.setName("Rent");
        category.setActive(active);
        when(categoryRepository.findAvailableById(CATEGORY_ID, TENANT_ID))
            .thenReturn(Optional.of(category));
    }

    private void stubSaveAndProjection(ExpenseStatus status) {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(EXPENSE_ID);
            return expense;
        });
        ExpenseListProjection projection = projection(status);
        when(expenseRepository.findListItemById(EXPENSE_ID, TENANT_ID))
            .thenReturn(Optional.of(projection));
    }

    private static CreateExpenseRequest request(Long branchId) {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setBranchId(branchId);
        request.setCategoryId(CATEGORY_ID);
        request.setAmount(new BigDecimal("125.500000"));
        request.setExpenseDate(LocalDate.of(2026, 1, 15));
        request.setDescription("January rent");
        request.setPayeeName("Landlord");
        request.setPaymentSource(ExpensePaymentSource.BANK);
        return request;
    }

    private static Expense activeExpense() {
        Expense expense = new Expense();
        expense.setId(EXPENSE_ID);
        expense.setTenantId(TENANT_ID);
        expense.setCategoryId(CATEGORY_ID);
        expense.setAmount(new BigDecimal("125.500000"));
        expense.setExpenseDate(LocalDate.of(2026, 1, 15));
        expense.setPaymentSource(ExpensePaymentSource.BANK);
        expense.setSourceType(ExpenseSourceType.MANUAL);
        expense.setStatus(ExpenseStatus.ACTIVE);
        return expense;
    }

    private static ExpenseListProjection projection(ExpenseStatus status) {
        ExpenseListProjection projection = org.mockito.Mockito.mock(ExpenseListProjection.class);
        when(projection.getId()).thenReturn(EXPENSE_ID);
        when(projection.getBranchId()).thenReturn(null);
        when(projection.getCategoryId()).thenReturn(CATEGORY_ID);
        when(projection.getCategoryName()).thenReturn("Rent");
        when(projection.getAmount()).thenReturn(new BigDecimal("125.500000"));
        when(projection.getExpenseDate()).thenReturn(LocalDate.of(2026, 1, 15));
        when(projection.getPaymentSource()).thenReturn(ExpensePaymentSource.BANK);
        when(projection.getSourceType()).thenReturn(ExpenseSourceType.MANUAL);
        when(projection.getSourceId()).thenReturn(null);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 15, 9, 30));
        return projection;
    }

    private static void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            Class<? extends AppException> exceptionType,
            ExpenseErrorCode code) {
        assertThatThrownBy(callable)
            .isInstanceOf(exceptionType)
            .extracting(error -> ((AppException) error).getErrorCode())
            .isEqualTo(code);
    }
}
