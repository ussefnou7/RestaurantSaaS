package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
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
import com.smart.restaurant_saas.expense.dto.SelectableShiftResponse;
import com.smart.restaurant_saas.expense.mapper.ExpenseMapper;
import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.pos.shift.ShiftRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** D124's default window for the manager's shift picker. Extendable per call. */
    private static final int DEFAULT_SELECTABLE_SHIFT_DAYS = 7;

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final ShiftRepository shiftRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final TenantTimeZoneService timeZoneService;
    private final ExpenseMapper mapper;

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> findAll(
            Long tenantId,
            Long branchId,
            boolean unbranchedOnly,
            Long categoryId,
            LocalDate dateFrom,
            LocalDate dateTo,
            ExpensePaymentSource paymentSource,
            ExpenseStatus status,
            String search,
            Pageable pageable) {
        if (unbranchedOnly) {
            currentUserScopeProvider.ensureCanAccessUnbranched();
        }
        return expenseRepository.findListItems(
                tenantId,
                currentUserScopeProvider.resolveBranchFilter(branchId),
                unbranchedOnly,
                categoryId,
                dateFrom,
                dateTo,
                paymentSource,
                status,
                blankToNull(search),
                pageable)
            .map(expense -> toResponse(expense, tenantId));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse findById(Long id, Long tenantId) {
        ExpenseListProjection expense = loadProjection(id, tenantId);
        // Guards the read entry point, not loadProjection: the create and void paths reload
        // through it to build their response, and a guard there would 403 a write that already
        // committed (D135).
        currentUserScopeProvider.ensureCanAccessBranch(expense.getBranchId());
        return toResponse(expense, tenantId);
    }

    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request, Long tenantId) {
        ExpenseCategory category = categoryRepository
            .findAvailableById(request.getCategoryId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ExpenseErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                "Expense category not found or not available to tenant: " + request.getCategoryId(),
                ErrorParams.of("categoryId", request.getCategoryId())));

        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new BusinessException(
                ExpenseErrorCode.EXPENSE_CATEGORY_INACTIVE,
                "Expense category is inactive: " + category.getId(),
                ErrorParams.of("categoryId", category.getId(), "categoryName", category.getName()));
        }

        // A null branch is a company-wide expense, which only an unscoped caller may record.
        currentUserScopeProvider.ensureCanAccessBranch(request.getBranchId());

        if (request.getBranchId() != null
                && branchRepository.findByIdAndTenantId(request.getBranchId(), tenantId).isEmpty()) {
            throw new ResourceNotFoundException(
                ExpenseErrorCode.BRANCH_NOT_FOUND,
                "Branch not found or not owned by tenant: " + request.getBranchId(),
                ErrorParams.of("branchId", request.getBranchId()));
        }

        BigDecimal amount = normalizeAmount(request.getAmount());
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException(
                ExpenseErrorCode.EXPENSE_INVALID_AMOUNT,
                "Expense amount must be greater than zero",
                ErrorParams.of("amount", request.getAmount()));
        }

        LocalDate today = LocalDate.now(timeZoneService.zoneFor(tenantId, request.getBranchId()));
        if (request.getExpenseDate().isAfter(today)) {
            throw new ValidationException(
                ExpenseErrorCode.EXPENSE_DATE_IN_FUTURE,
                "Expense date must not be in the future",
                ErrorParams.of("expenseDate", request.getExpenseDate(), "today", today));
        }

        validatePaidFromShift(request.getPaidFromShiftId(), request.getBranchId(), tenantId);

        Long actorUserId = currentTenantProvider.getActorUserId();
        Expense expense = new Expense();
        expense.setTenantId(tenantId);
        expense.setBranchId(request.getBranchId());
        expense.setPaidFromShiftId(request.getPaidFromShiftId());
        expense.setCategoryId(category.getId());
        expense.setAmount(amount);
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(trimToNull(request.getDescription()));
        expense.setPayeeName(trimToNull(request.getPayeeName()));
        expense.setPaymentSource(request.getPaymentSource());
        expense.setSourceType(ExpenseSourceType.MANUAL);
        expense.setSourceId(null);
        expense.setStatus(ExpenseStatus.ACTIVE);
        expense.setCreatedBy(actorUserId);

        Expense saved = expenseRepository.save(expense);
        return toResponse(loadProjection(saved.getId(), tenantId), tenantId);
    }

    @Transactional
    public ExpenseResponse voidExpense(Long id, Long tenantId, String reason) {
        Expense expense = loadOwned(id, tenantId);
        currentUserScopeProvider.ensureCanAccessBranch(expense.getBranchId());

        if (expense.getStatus() == ExpenseStatus.VOIDED) {
            throw new BusinessException(
                ExpenseErrorCode.EXPENSE_ALREADY_VOIDED,
                "Expense is already voided: " + id,
                ErrorParams.of("expenseId", id, "voidedAt", expense.getVoidedAt()));
        }
        if (expense.getSourceType() != ExpenseSourceType.MANUAL) {
            throw new BusinessException(
                ExpenseErrorCode.EXPENSE_NOT_MANUAL,
                "System-sourced expense cannot be voided directly: " + id,
                ErrorParams.of("expenseId", id, "sourceType", expense.getSourceType()));
        }

        String normalizedReason = trimToNull(reason);
        if (normalizedReason == null) {
            throw new ValidationException(
                ExpenseErrorCode.EXPENSE_VOID_REASON_REQUIRED,
                "A void reason is required",
                ErrorParams.of("expenseId", id));
        }

        Long actorUserId = currentTenantProvider.getActorUserId();
        LocalDateTime voidedAt = LocalDateTime.now(
            timeZoneService.zoneFor(tenantId, expense.getBranchId()));
        expense.setStatus(ExpenseStatus.VOIDED);
        expense.setVoidedAt(voidedAt);
        expense.setVoidedBy(actorUserId);
        expense.setVoidReason(normalizedReason);
        expense.setUpdatedBy(actorUserId);
        expenseRepository.save(expense);

        return toResponse(loadProjection(id, tenantId), tenantId);
    }

    /**
     * The selectable list a manager picks from (D124).
     *
     * <p>Filtered to the expense's branch and a recent window. The window default is seven days
     * and is extendable by the caller — an unbounded list becomes unreadable within months, and an
     * explicit choice nobody can read is not an explicit choice.
     */
    @Transactional(readOnly = true)
    public List<SelectableShiftResponse> findSelectableShifts(Long tenantId, Long branchId, Integer days) {
        currentUserScopeProvider.ensureCanAccessBranch(branchId);
        if (branchRepository.findByIdAndTenantId(branchId, tenantId).isEmpty()) {
            throw new ResourceNotFoundException(
                ExpenseErrorCode.BRANCH_NOT_FOUND,
                "Branch not found or not owned by tenant: " + branchId,
                ErrorParams.of("branchId", branchId));
        }

        int window = days == null || days <= 0 ? DEFAULT_SELECTABLE_SHIFT_DAYS : days;
        LocalDate from = LocalDate.now(timeZoneService.zoneFor(tenantId, branchId)).minusDays(window);

        return shiftRepository.findSelectableForExpense(tenantId, branchId, from).stream()
            .map(SelectableShiftResponse::from)
            .toList();
    }

    /**
     * A linked shift must be this tenant's and must sit in the expense's own branch.
     *
     * <p><b>A closed shift is deliberately allowed.</b> D124 makes closed shifts selectable — they
     * are stored and linked, and their recorded figures do not move. Rejecting them here would
     * quietly turn "the manager records it late" into an error, and the late expense would go
     * unrecorded rather than recorded and visible.
     */
    private void validatePaidFromShift(Long shiftId, Long branchId, Long tenantId) {
        if (shiftId == null) {
            return;
        }
        Shift shift = shiftRepository.findByIdAndTenantId(shiftId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ExpenseErrorCode.EXPENSE_SHIFT_NOT_FOUND,
                "Shift not found or not owned by tenant: " + shiftId,
                ErrorParams.of("paidFromShiftId", shiftId)));

        Long shiftBranchId = shift.getDevice().getBranch().getId();
        if (!shiftBranchId.equals(branchId)) {
            throw new BusinessException(
                ExpenseErrorCode.EXPENSE_SHIFT_BRANCH_MISMATCH,
                "Shift " + shiftId + " belongs to branch " + shiftBranchId
                    + ", expense to branch " + branchId,
                ErrorParams.of(
                    "paidFromShiftId", shiftId,
                    "shiftBranchId", shiftBranchId,
                    "expenseBranchId", branchId));
        }
    }

    private Expense loadOwned(Long id, Long tenantId) {
        return expenseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> expenseNotFound(id));
    }

    private ExpenseListProjection loadProjection(Long id, Long tenantId) {
        return expenseRepository.findListItemById(id, tenantId)
            .orElseThrow(() -> expenseNotFound(id));
    }

    private ExpenseResponse toResponse(ExpenseListProjection expense, Long tenantId) {
        return mapper.toResponse(expense, recordedAfterShiftClose(expense, tenantId));
    }

    /**
     * Null means this is not a drawer expense; false means it was linked before the drawer closed.
     * Expense audit timestamps are stored in the tenant wall clock, while shift close timestamps
     * use the branch wall clock, so the audit value must be converted before they are compared.
     */
    private Boolean recordedAfterShiftClose(ExpenseListProjection expense, Long tenantId) {
        if (expense.getPaidFromShiftId() == null) {
            return null;
        }
        if (expense.getPaidFromShiftClosedAt() == null || expense.getCreatedAt() == null) {
            return false;
        }
        ZoneId tenantZone = timeZoneService.zoneFor(tenantId);
        ZoneId branchZone = timeZoneService.zoneFor(tenantId, expense.getBranchId());
        LocalDateTime branchRecordedAt = expense.getCreatedAt()
            .atZone(tenantZone)
            .withZoneSameInstant(branchZone)
            .toLocalDateTime();
        return branchRecordedAt.isAfter(expense.getPaidFromShiftClosedAt());
    }

    private ResourceNotFoundException expenseNotFound(Long id) {
        return new ResourceNotFoundException(
            ExpenseErrorCode.EXPENSE_NOT_FOUND,
            "Expense not found: " + id,
            ErrorParams.of("expenseId", id));
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
