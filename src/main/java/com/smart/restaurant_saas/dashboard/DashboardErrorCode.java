package com.smart.restaurant_saas.dashboard;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the dashboard module.
 *
 * <p>Its own enum rather than a borrowed one: each module owns its codes, and reusing
 * {@code InventoryErrorCode} here would make an inventory rename break a dashboard contract the
 * frontend branches on.
 *
 * <p>Short, and that is the point — the dashboard is read-only, so there are no state transitions
 * to reject and no invariants to defend. The only thing a caller can get wrong is the range.
 */
@Getter
@RequiredArgsConstructor
public enum DashboardErrorCode implements ErrorCode {

    /**
     * The requested date range is missing, incomplete or inverted.
     *
     * <p>Rejected rather than coerced into an empty result, for the same reason the date-ranged
     * reports reject it: the answer to this request is a set of money figures, and "no rows" reads
     * as "a quiet month" — which is a materially wrong conclusion to hand an owner silently.
     */
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
