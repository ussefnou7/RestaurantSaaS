package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import java.util.Map;

/**
 * UOM conversion failure. Now a structured {@link BusinessException} using
 * {@link InventoryErrorCode#UOM_CONVERSION_FAILED} (400); params carry the from/to UOM codes and
 * (where known) the material code.
 */
public class UomConversionException extends BusinessException {

    private UomConversionException(String debugMessage, Map<String, Object> params) {
        super(InventoryErrorCode.UOM_CONVERSION_FAILED, debugMessage, params);
    }

    /** Convenience for simple guard failures (e.g. null arguments) with no from/to context. */
    public UomConversionException(String debugMessage) {
        super(InventoryErrorCode.UOM_CONVERSION_FAILED, debugMessage);
    }

    public static UomConversionException missingMaterialContext(String fromCode, String toCode) {
        return new UomConversionException(
            "Conversion from '" + fromCode + "' to '" + toCode +
                "' requires material context (different Uom types)",
            ErrorParams.of("fromUom", fromCode, "toUom", toCode, "materialCode", null));
    }

    public static UomConversionException noConversionFound(String fromCode, String toCode, String materialCode) {
        return new UomConversionException(
            "No conversion defined from '" + fromCode + "' to '" + toCode +
                "' for material '" + materialCode + "'",
            ErrorParams.of("fromUom", fromCode, "toUom", toCode, "materialCode", materialCode));
    }

    public static UomConversionException incompatibleTypes(String fromCode, String fromType,
                                                            String toCode, String toType) {
        return new UomConversionException(
            "Cannot convert '" + fromCode + "' (" + fromType + ") to '" + toCode +
                "' (" + toType + ") without a material-specific conversion",
            ErrorParams.of("fromUom", fromCode, "toUom", toCode,
                "fromType", fromType, "toType", toType, "materialCode", null));
    }
}
