package com.smart.restaurant_saas.menu;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MenuErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_PRODUCT_NAME(HttpStatus.CONFLICT),
    DUPLICATE_MATERIAL_IN_RECIPE(HttpStatus.CONFLICT),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    UOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    UOM_NOT_AVAILABLE_FOR_TENANT(HttpStatus.BAD_REQUEST),
    INACTIVE_CATEGORY(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
