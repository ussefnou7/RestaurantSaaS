package com.smart.restaurant_saas.tenant.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Complete replacement: omitted booleans must not silently disable a charge or order type. */
public record TenantSettingsRequest(
        @NotNull Boolean taxEnabled,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal taxRate,
        @NotNull Boolean taxOnServiceCharge,
        @NotNull Boolean serviceChargeEnabled,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal serviceChargeRate,
        @NotNull Boolean dineInEnabled,
        @NotNull Boolean takeawayEnabled,
        @NotNull Boolean deliveryEnabled
) {}
