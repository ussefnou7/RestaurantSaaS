package com.smart.restaurant_saas.common;

public final class BilingualFieldUtils {

    private BilingualFieldUtils() {
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    public static String englishOrLegacy(String english, String arabic, String legacy) {
        String normalizedEnglish = trimToNull(english);
        if (normalizedEnglish != null) {
            return normalizedEnglish;
        }
        return trimToNull(arabic) == null ? trimToNull(legacy) : null;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
