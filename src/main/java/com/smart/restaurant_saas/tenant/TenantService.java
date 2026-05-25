package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.tenant.dto.CreateTenantRequest;
import com.smart.restaurant_saas.tenant.dto.UpdateTenantRequest;
import com.smart.restaurant_saas.tenant.dto.UpdateTenantStatusRequest;
import com.smart.restaurant_saas.tenant.dto.TenantResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final long SYSTEM_TENANT_ID = 0L;

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String code = normalizeCode(request.code());

        if (tenantRepository.existsByCode(code)) {
            throw new ApiException("Tenant code already exists: " + code);
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
        tenant.setCode(code);
        tenant.setStatus(TenantStatus.ACTIVE);

        return TenantResponse.toResponse(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream()
                .map(TenantResponse::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long id) {
        return TenantResponse.toResponse(findTenant(id));
    }

    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = findTenant(id);
        ensureNotSystemTenant(tenant);
        String code = normalizeCode(request.code());

        if (!tenant.getCode().equals(code) && tenantRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException("Tenant code already exists: " + code);
        }

        tenant.setName(request.name().trim());
        tenant.setCode(code);

        return TenantResponse.toResponse(tenantRepository.saveAndFlush(tenant));
    }

    @Transactional
    public TenantResponse updateTenantStatus(Long id, UpdateTenantStatusRequest request) {
        Tenant tenant = findTenant(id);
        ensureNotSystemTenant(tenant);
        tenant.setStatus(parseStatus(request.status()));

        return TenantResponse.toResponse(tenantRepository.saveAndFlush(tenant));
    }

    private void ensureNotSystemTenant(Tenant tenant) {
        if (tenant.getId() != null && tenant.getId() == SYSTEM_TENANT_ID) {
            throw new ApiException("System tenant cannot be modified");
        }
    }

    private Tenant findTenant(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tenant not found: " + id));
    }

    private String normalizeCode(String code) {
        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new ApiException("Tenant code must be a lowercase slug");
        }
        return normalizedCode;
    }

    private TenantStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return TenantStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid tenant status: " + status
                    + ". Allowed values: " + Arrays.toString(TenantStatus.values()));
        }
    }
}
