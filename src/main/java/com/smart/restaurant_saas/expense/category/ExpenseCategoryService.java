package com.smart.restaurant_saas.expense.category;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryRequest;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryResponse;
import com.smart.restaurant_saas.expense.core.ExpenseErrorCode;
import com.smart.restaurant_saas.expense.mapper.ExpenseCategoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseCategoryMapper mapper;

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> findAll(Long tenantId) {
        return categoryRepository.findAvailableForTenant(tenantId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public ExpenseCategoryResponse create(
            ExpenseCategoryRequest request, Long tenantId, Long userId) {
        String name = request.getName().trim();
        assertUniqueName(tenantId, name, null);

        ExpenseCategory category = new ExpenseCategory();
        category.setTenantId(tenantId);
        category.setName(name);
        category.setNameAr(trimToNull(request.getNameAr()));
        category.setActive(true);
        category.setCreatedBy(userId);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public ExpenseCategoryResponse update(
            Long id, ExpenseCategoryRequest request, Long tenantId, Long userId) {
        ExpenseCategory category = loadTenantOwned(id, tenantId);
        String name = request.getName().trim();
        assertUniqueName(tenantId, name, id);

        category.setName(name);
        category.setNameAr(trimToNull(request.getNameAr()));
        category.setUpdatedBy(userId);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public ExpenseCategoryResponse activate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, true);
    }

    @Transactional
    public ExpenseCategoryResponse deactivate(Long id, Long tenantId, Long userId) {
        return setActive(id, tenantId, userId, false);
    }

    private ExpenseCategoryResponse setActive(
            Long id, Long tenantId, Long userId, boolean active) {
        ExpenseCategory category = loadTenantOwned(id, tenantId);
        category.setActive(active);
        category.setUpdatedBy(userId);
        return mapper.toResponse(categoryRepository.save(category));
    }

    private ExpenseCategory loadTenantOwned(Long id, Long tenantId) {
        return categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> {
                if (categoryRepository.existsByIdAndTenantIdIsNull(id)) {
                    return new BusinessException(
                        ExpenseErrorCode.EXPENSE_CATEGORY_IS_GLOBAL,
                        "Global expense categories are read-only: " + id,
                        ErrorParams.of("categoryId", id));
                }
                return categoryNotFound(id);
            });
    }

    private void assertUniqueName(Long tenantId, String name, Long excludedId) {
        boolean exists = excludedId == null
            ? categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)
            : categoryRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(
                tenantId, name, excludedId);
        if (exists) {
            throw new BusinessException(
                ExpenseErrorCode.EXPENSE_CATEGORY_NAME_EXISTS,
                "Expense category name already exists for tenant: " + name,
                ErrorParams.of("name", name));
        }
    }

    private ResourceNotFoundException categoryNotFound(Long id) {
        return new ResourceNotFoundException(
            ExpenseErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
            "Expense category not found: " + id,
            ErrorParams.of("categoryId", id));
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
