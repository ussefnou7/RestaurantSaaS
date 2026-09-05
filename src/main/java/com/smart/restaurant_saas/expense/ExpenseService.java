package com.smart.restaurant_saas.expense;

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
import com.smart.restaurant_saas.expense.mapper.ExpenseMapper;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final CurrentTenantProvider currentTenantProvider;
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
        return expenseRepository.findListItems(
                tenantId,
                branchId,
                unbranchedOnly,
                categoryId,
                dateFrom,
                dateTo,
                paymentSource,
                status,
                blankToNull(search),
                pageable)
            .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadProjection(id, tenantId));
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

        Long actorUserId = currentTenantProvider.getActorUserId();
        Expense expense = new Expense();
        expense.setTenantId(tenantId);
        expense.setBranchId(request.getBranchId());
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
        return mapper.toResponse(loadProjection(saved.getId(), tenantId));
    }

    @Transactional
    public ExpenseResponse voidExpense(Long id, Long tenantId, String reason) {
        Expense expense = loadOwned(id, tenantId);

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

        return mapper.toResponse(loadProjection(id, tenantId));
    }

    private Expense loadOwned(Long id, Long tenantId) {
        return expenseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> expenseNotFound(id));
    }

    private ExpenseListProjection loadProjection(Long id, Long tenantId) {
        return expenseRepository.findListItemById(id, tenantId)
            .orElseThrow(() -> expenseNotFound(id));
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
