package com.smart.restaurant_saas.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small helper for building the structured {@code params} map carried by exceptions.
 * Unlike {@link Map#of}, this tolerates null values (e.g. an optional materialCode) and
 * preserves insertion order for stable, readable error payloads.
 */
public final class ErrorParams {

    private ErrorParams() {}

    /** Builds an ordered param map from alternating key/value arguments. */
    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            params.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return params;
    }
}
