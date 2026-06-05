package com.smart.restaurant_saas.inventory.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.enums.UomType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.http.HttpStatus;

final class CatalogInputNormalizer {

    private CatalogInputNormalizer() {
    }

    static String normalizeCode(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    static String trimRequired(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must not be blank");
        }
        return trimmed;
    }

    static String searchPattern(String search) {
        String trimmed = trimToNull(search);
        return trimmed == null ? null : "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
    }

    static UomType parseUomType(String type) {
        String normalized = trimToNull(type);
        if (normalized == null) {
            return null;
        }

        try {
            return UomType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid UOM type: " + type
                    + ". Allowed values: " + Arrays.toString(UomType.values()));
        }
    }

    static void validatePositiveFactor(BigDecimal factorToBase) {
        if (factorToBase == null || factorToBase.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "factorToBase must be greater than 0");
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
