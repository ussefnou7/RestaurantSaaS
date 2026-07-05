package com.smart.restaurant_saas.inventory.uom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.UomType;

@Getter
@Builder
public class UomResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String nameAr;
    private final String symbol;
    private final UomType type;

    private final Long baseUomId;
    private final String baseUomName;

    private final BigDecimal factorToBase;
    private final Boolean active;

    /** Null for global UOMs, the owning tenant id for custom UOMs. */
    private final Long tenantId;

    /** True when this is a global UOM (tenantId is null). */
    @Getter(AccessLevel.NONE)
    private final boolean isGlobal;

    @JsonProperty("isGlobal")
    public boolean isGlobal() {
        return isGlobal;
    }
}
