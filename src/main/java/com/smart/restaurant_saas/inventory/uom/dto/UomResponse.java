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

    /** Always relative to the root of the chain. The engine uses this one. */
    private final BigDecimal factorToBase;

    /**
     * The factor as the user typed it, against {@link #enteredAgainstUomId}.
     * Display only — 25 for a 25 kg sack whose factorToBase is 25000.
     */
    private final BigDecimal enteredFactor;

    /** The parent the user picked. Null for roots. */
    private final Long enteredAgainstUomId;

    /**
     * Symbol and active flag of the entered-against parent, carried here rather
     * than resolved client-side: a deactivated parent is filtered out of the
     * frontend's UOM list, and the edit form must still render it (with an
     * inactive marker). Without these the entered pair becomes unreadable the
     * moment a parent is deactivated. Null for roots.
     */
    private final String enteredAgainstUomSymbol;
    private final Boolean enteredAgainstUomActive;

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
