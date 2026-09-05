package com.smart.restaurant_saas.pos.shift.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The whole of it. No {@code deviceId}: the drawer comes from the signed token
 * ({@code requireCurrentDeviceId()}), so it cannot be misreported (D119). No cashier either --
 * that is the JWT principal.
 */
public record OpenShiftRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal openingCount
) {
}
