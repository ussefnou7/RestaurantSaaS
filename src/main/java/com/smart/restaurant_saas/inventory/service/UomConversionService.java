package com.smart.restaurant_saas.inventory.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.entity.Uom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UomConversionService {

    private static final int QUANTITY_SCALE = 6;

    public BigDecimal convert(BigDecimal quantity, Uom fromUom, Uom toUom) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be greater than or equal to 0");
        }
        if (fromUom == null || toUom == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UOM conversion requires source and target UOMs");
        }
        if (fromUom.getType() != toUom.getType()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UOM types are not compatible: " + fromUom.getCode() + " -> " + toUom.getCode()
            );
        }
        if (fromUom.getFactorToBase() == null || fromUom.getFactorToBase().compareTo(BigDecimal.ZERO) <= 0
                || toUom.getFactorToBase() == null || toUom.getFactorToBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UOM factor_to_base must be greater than 0");
        }

        BigDecimal quantityInBase = quantity.multiply(fromUom.getFactorToBase());
        return quantityInBase.divide(toUom.getFactorToBase(), QUANTITY_SCALE, RoundingMode.HALF_UP);
    }
}
