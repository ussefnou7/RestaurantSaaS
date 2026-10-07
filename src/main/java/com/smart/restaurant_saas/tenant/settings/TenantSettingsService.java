package com.smart.restaurant_saas.tenant.settings;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.tenant.TenantErrorCode;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.tenant.settings.dto.TenantSettingsRequest;
import com.smart.restaurant_saas.tenant.settings.dto.TenantSettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public TenantSettingsResponse get(Long tenantId) {
        return toResponse(find(tenantId));
    }

    @Transactional
    public TenantSettingsResponse update(Long tenantId, Long actorId, TenantSettingsRequest request) {
        if (!Boolean.TRUE.equals(request.dineInEnabled())
                && !Boolean.TRUE.equals(request.takeawayEnabled())
                && !Boolean.TRUE.equals(request.deliveryEnabled())) {
            throw new ValidationException(TenantErrorCode.TENANT_ORDER_TYPE_REQUIRED,
                    "At least one order type must remain enabled");
        }
        TenantSettings settings = find(tenantId);
        settings.setTaxEnabled(request.taxEnabled());
        settings.setTaxRate(request.taxRate());
        settings.setTaxOnServiceCharge(request.taxOnServiceCharge());
        settings.setServiceChargeEnabled(request.serviceChargeEnabled());
        settings.setServiceChargeRate(request.serviceChargeRate());
        settings.setDineInEnabled(request.dineInEnabled());
        settings.setTakeawayEnabled(request.takeawayEnabled());
        settings.setDeliveryEnabled(request.deliveryEnabled());
        settings.setUpdatedBy(actorId);
        return toResponse(repository.saveAndFlush(settings));
    }

    private TenantSettingsResponse toResponse(TenantSettings settings) {
        String tenantName = tenantRepository.findById(settings.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(TenantErrorCode.TENANT_NOT_FOUND,
                        "Tenant not found", ErrorParams.of("tenantId", settings.getTenantId())))
                .getName();
        return TenantSettingsResponse.from(settings, tenantName);
    }

    private TenantSettings find(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseThrow(() ->
                new ResourceNotFoundException(TenantErrorCode.TENANT_SETTINGS_NOT_FOUND,
                        "Tenant settings not found", ErrorParams.of("tenantId", tenantId)));
    }
}
