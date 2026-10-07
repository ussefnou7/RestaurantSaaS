package com.smart.restaurant_saas.tenant.settings;

import com.smart.restaurant_saas.tenant.CurrentTenantId;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import com.smart.restaurant_saas.tenant.settings.dto.TenantSettingsRequest;
import com.smart.restaurant_saas.tenant.settings.dto.TenantSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant-settings")
@Tag(name = "Tenant settings", description = "Tenant-wide tax, service charge and order type settings")
public class TenantSettingsController {

    private final TenantSettingsService service;
    private final CurrentTenantProvider currentTenantProvider;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TENANT_SETTINGS_VIEW') or @securityService.hasPermission('TENANT_SETTINGS_MANAGE')")
    @Operation(summary = "Read tenant operating settings")
    public TenantSettingsResponse get(@CurrentTenantId Long tenantId) {
        return service.get(tenantId);
    }

    @PutMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('TENANT_SETTINGS_MANAGE')")
    @Operation(summary = "Replace tenant operating settings", description = "All eight settings are required. Rates are percentages from 0 to 100; at least one order type must be enabled. taxOnServiceCharge defaults to false (tax on items only); true includes service charge in the tax base.")
    public TenantSettingsResponse update(@CurrentTenantId Long tenantId, @Valid @RequestBody TenantSettingsRequest request) {
        return service.update(tenantId, currentTenantProvider.getActorUserId(), request);
    }
}
