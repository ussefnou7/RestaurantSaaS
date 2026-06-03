package com.smart.restaurant_saas.tenant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TenantEntityPrefix {
    BR("Branch"),
    JOB("Job"),
    EMP("Employee"),
    USR("App User"),
    UNT("Unit"),
    CAT("Material Category"),
    MAT("Material"),
    PROD("Product");

    private final String entityName;
}
