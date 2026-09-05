package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import com.smart.restaurant_saas.expense.dto.CreateExpenseRequest;
import com.smart.restaurant_saas.expense.dto.ExpenseResponse;
import com.smart.restaurant_saas.expense.dto.VoidExpenseRequest;
import com.smart.restaurant_saas.tenant.CurrentTenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Money paid without receiving warehouse stock")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_VIEW')")
    @Operation(summary = "List expenses",
        description = "Returns active and voided expense rows by default, denormalized for display.")
    public Page<ExpenseResponse> list(
            @CurrentTenantId Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "false") boolean unbranchedOnly,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) ExpensePaymentSource paymentSource,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                @SortDefault(sort = "expenseDate", direction = Sort.Direction.DESC),
                @SortDefault(sort = "id", direction = Sort.Direction.DESC)
            })
            Pageable pageable) {
        return expenseService.findAll(
            tenantId, branchId, unbranchedOnly, categoryId, dateFrom, dateTo,
            paymentSource, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_VIEW')")
    @Operation(summary = "Get expense", description = "Returns one tenant-owned expense by ID.")
    public ExpenseResponse getById(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId) {
        return expenseService.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_CREATE')")
    @Operation(summary = "Create expense",
        description = "Creates an immediately active expense. Anything that enters a warehouse has "
            + "a purchase document, not an expense; an expense is money that left with no stock behind it.")
    public ResponseEntity<ExpenseResponse> create(
            @Valid @RequestBody CreateExpenseRequest request,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(expenseService.create(request, tenantId, userId));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_VOID')")
    @Operation(summary = "Void expense",
        description = "Voids an active manual expense while preserving the original row and reason.")
    public ExpenseResponse voidExpense(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) VoidExpenseRequest request,
            @CurrentTenantId Long tenantId,
            @RequestHeader("X-User-Id") Long userId) {
        return expenseService.voidExpense(
            id, tenantId, userId, request == null ? null : request.getReason());
    }
}
