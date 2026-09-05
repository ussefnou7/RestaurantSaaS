package com.smart.restaurant_saas.pos.shift.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The counted drawer, and nothing else.
 *
 * <p><b>There is deliberately no {@code pendingCount}.</b> D126's empty-queue precondition is
 * enforced by the POS and cannot be verified here -- the queue lives in the device's local
 * storage and exposes no watermark or flush acknowledgement, so a count in this request would
 * only be the caller asserting its own compliance. A field the server cannot check reads as a
 * verification and is not one.
 *
 * <p>There is no {@code forcedClose} either: it is derived from {@code closedBy != openedBy}
 * (D122), never accepted from the client.
 */
public record CloseShiftRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal closingCount
) {
}
