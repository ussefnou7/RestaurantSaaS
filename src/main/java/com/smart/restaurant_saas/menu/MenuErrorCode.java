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
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RECIPE_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_MATERIAL_IN_RECIPE(HttpStatus.CONFLICT),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    UOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    UOM_NOT_AVAILABLE_FOR_TENANT(HttpStatus.BAD_REQUEST),
    INACTIVE_CATEGORY(HttpStatus.CONFLICT),
    VARIANT_CANNOT_BE_MENU_ITEM(HttpStatus.BAD_REQUEST),
    PARENT_PRODUCT_NOT_ORDERABLE(HttpStatus.CONFLICT),
    PARENT_PRODUCT_HAS_NO_RECIPE(HttpStatus.CONFLICT),
    PRODUCT_WITH_RECIPE_CANNOT_BE_PARENT(HttpStatus.CONFLICT),
    PRODUCT_HAS_VARIANTS(HttpStatus.CONFLICT),
    ADDON_HOST_MUST_BE_PARENT_ELIGIBLE(HttpStatus.BAD_REQUEST),
    ADDON_CANNOT_BE_SELF(HttpStatus.BAD_REQUEST),
    DUPLICATE_ADD_ON(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
