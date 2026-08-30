package com.smart.restaurant_saas.inventory.uom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.UomType;

@Getter
@Setter
public class UomRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    @NotBlank(message = "symbol is required")
    private String symbol;

    private String symbolAr;

    @NotNull(message = "type is required")
    private UomType type;

    /**
     * The base UOM of the same physical type. Null means this UOM is itself
     * the base (e.g. GRAM for WEIGHT).
     */
    private Long baseUom;

    @NotNull(message = "factorToBase is required")
    @Positive(message = "factorToBase must be greater than zero")
    private BigDecimal factorToBase;
}
