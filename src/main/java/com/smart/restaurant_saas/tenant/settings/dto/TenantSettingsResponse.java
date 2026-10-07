package com.smart.restaurant_saas.tenant.settings.dto;

import com.smart.restaurant_saas.tenant.settings.TenantSettings;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TenantSettingsResponse(
        Long tenantId,
        boolean taxEnabled,
        BigDecimal taxRate,
        boolean taxOnServiceCharge,
        boolean serviceChargeEnabled,
        BigDecimal serviceChargeRate,
        boolean dineInEnabled,
        boolean takeawayEnabled,
        boolean deliveryEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TenantSettingsResponse from(TenantSettings settings) {
        return new TenantSettingsResponse(settings.getTenantId(), settings.isTaxEnabled(), settings.getTaxRate(),
                settings.isTaxOnServiceCharge(), settings.isServiceChargeEnabled(), settings.getServiceChargeRate(), settings.isDineInEnabled(),
                settings.isTakeawayEnabled(), settings.isDeliveryEnabled(), settings.getCreatedAt(), settings.getUpdatedAt());
    }
}
