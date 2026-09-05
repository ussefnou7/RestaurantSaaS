package com.smart.restaurant_saas.expense.core;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExpenseErrorCode implements ErrorCode {

    EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_INACTIVE(HttpStatus.CONFLICT),
    EXPENSE_INVALID_AMOUNT(HttpStatus.BAD_REQUEST),
    EXPENSE_DATE_IN_FUTURE(HttpStatus.BAD_REQUEST),
    EXPENSE_ALREADY_VOIDED(HttpStatus.CONFLICT),
    EXPENSE_NOT_MANUAL(HttpStatus.CONFLICT),
    EXPENSE_VOID_REASON_REQUIRED(HttpStatus.BAD_REQUEST),
    EXPENSE_CATEGORY_IS_GLOBAL(HttpStatus.CONFLICT),
    EXPENSE_CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
