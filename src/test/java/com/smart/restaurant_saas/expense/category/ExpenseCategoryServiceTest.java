package com.smart.restaurant_saas.expense.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryRequest;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryResponse;
import com.smart.restaurant_saas.expense.core.ExpenseErrorCode;
import com.smart.restaurant_saas.expense.mapper.ExpenseCategoryMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long USER_ID = 9L;

    @Mock
    private ExpenseCategoryRepository categoryRepository;

    private ExpenseCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseCategoryService(categoryRepository, new ExpenseCategoryMapper());
    }

    @Test
    void list_includesGlobalTenantAndInactiveRows() {
        ExpenseCategory global = category(1L, null, "Rent", true);
        ExpenseCategory tenant = category(2L, TENANT_ID, "Staff meals", true);
        ExpenseCategory inactive = category(3L, TENANT_ID, "Retired category", false);
        when(categoryRepository.findAvailableForTenant(TENANT_ID))
            .thenReturn(List.of(global, tenant, inactive));

        List<ExpenseCategoryResponse> result = service.findAll(TENANT_ID);

        assertThat(result).extracting(ExpenseCategoryResponse::getName)
            .containsExactly("Rent", "Staff meals", "Retired category");
        assertThat(result).extracting(ExpenseCategoryResponse::isGlobal)
            .containsExactly(true, false, false);
        assertThat(result).extracting(ExpenseCategoryResponse::isActive)
            .containsExactly(true, true, false);
    }

    @Test
    void globalCategoryCannotBeModifiedByTenant() {
        when(categoryRepository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.empty());
        when(categoryRepository.existsByIdAndTenantIdIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("Office rent"), TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((AppException) error).getErrorCode())
            .isEqualTo(ExpenseErrorCode.EXPENSE_CATEGORY_IS_GLOBAL);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deactivateKeepsTenantCategoryAndMarksItInactive() {
        ExpenseCategory category = category(2L, TENANT_ID, "Staff meals", true);
        when(categoryRepository.findByIdAndTenantId(2L, TENANT_ID))
            .thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        ExpenseCategoryResponse response = service.deactivate(2L, TENANT_ID, USER_ID);

        assertThat(response.isActive()).isFalse();
        assertThat(category.getUpdatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void duplicateTenantNameIsRejectedCaseInsensitively() {
        when(categoryRepository.existsByTenantIdAndNameIgnoreCase(TENANT_ID, "Transport"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(" Transport "), TENANT_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((AppException) error).getErrorCode())
            .isEqualTo(ExpenseErrorCode.EXPENSE_CATEGORY_NAME_EXISTS);
        verify(categoryRepository, never()).save(any());
    }

    private static ExpenseCategory category(
            Long id, Long tenantId, String name, boolean active) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(id);
        category.setTenantId(tenantId);
        category.setName(name);
        category.setActive(active);
        return category;
    }

    private static ExpenseCategoryRequest request(String name) {
        ExpenseCategoryRequest request = new ExpenseCategoryRequest();
        request.setName(name);
        return request;
    }
}
