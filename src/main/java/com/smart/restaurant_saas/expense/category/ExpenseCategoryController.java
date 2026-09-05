package com.smart.restaurant_saas.expense.category;

import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryRequest;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
@Tag(name = "Expense Categories", description = "Global and tenant-owned expense categories")
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_VIEW')")
    @Operation(summary = "List expense categories",
        description = "Returns global and tenant-owned categories, including inactive rows.")
    public List<ExpenseCategoryResponse> list(@CurrentTenantId Long tenantId) {
        return categoryService.findAll(tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_CATEGORY_MANAGE')")
    @Operation(summary = "Create expense category",
        description = "Creates an active category owned by the current tenant.")
    public ResponseEntity<ExpenseCategoryResponse> create(
            @Valid @RequestBody ExpenseCategoryRequest request,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(categoryService.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_CATEGORY_MANAGE')")
    @Operation(summary = "Update expense category",
        description = "Updates the names of a tenant-owned category; global categories are read-only.")
    public ExpenseCategoryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseCategoryRequest request,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return categoryService.update(id, request, tenantId, userId);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_CATEGORY_MANAGE')")
    @Operation(summary = "Activate expense category",
        description = "Activates a tenant-owned category; global categories are read-only.")
    public ExpenseCategoryResponse activate(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return categoryService.activate(id, tenantId, userId);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('EXPENSES_CATEGORY_MANAGE')")
    @Operation(summary = "Deactivate expense category",
        description = "Deactivates a tenant-owned category without removing historical references.")
    public ExpenseCategoryResponse deactivate(
            @PathVariable Long id,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return categoryService.deactivate(id, tenantId, userId);
    }
}
