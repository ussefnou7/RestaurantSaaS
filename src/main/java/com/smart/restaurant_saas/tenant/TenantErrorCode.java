package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TenantErrorCode implements ErrorCode {

    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** The supplied string is not an IANA zone id that this JVM's tz database knows. */
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),

    /**
     * A tenant row exists but carries no zone. Unreachable after V44 made the column NOT NULL;
     * kept because D101 decision 3 forbids a runtime fallback, so the only other option is a throw.
     */
    TENANT_TIMEZONE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * An entity reached {@code @PrePersist} with no {@code tenantId}. The audit timestamp cannot be
     * stamped in the right zone, and guessing one would reintroduce exactly the defect D101 fixes.
     */
    TENANT_CONTEXT_MISSING(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
