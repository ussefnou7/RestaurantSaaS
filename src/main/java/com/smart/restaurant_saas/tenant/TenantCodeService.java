package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.common.ApiException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantCodeService {

    private final CurrentTenantProvider currentTenantProvider;
    private final TenantRepository tenantRepository;

    public String normalizeSuffix(String suffix) {
        if (suffix == null) {
            return "";
        }

        return suffix
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    public String normalizeFullCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public String buildPrefix(String tenantCode, String entityPrefix) {
        String normalizedTenantCode = normalizeTenantCode(tenantCode);
        String normalizedEntityPrefix = normalizeEntityPrefix(entityPrefix);
        if (normalizedTenantCode.isEmpty() || normalizedEntityPrefix.isEmpty()) {
            return "";
        }
        return normalizedTenantCode + "-" + normalizedEntityPrefix + "-";
    }

    public String buildPrefix(String tenantCode, TenantEntityPrefix entityPrefix) {
        return buildPrefix(tenantCode, entityPrefix.name());
    }

    public ValidatedCode validateAndNormalizeCode(String code, TenantEntityPrefix entityPrefix) {
        return validateAndNormalizeCode(code, entityPrefix.name());
    }

    public ValidatedCode validateAndNormalizeCode(String code, String entityPrefix) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid tenant id: " + tenantId));

        String normalizedCode = normalizeFullCode(code);
        if (normalizedCode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code must not be blank");
        }

        String expectedPrefix = buildPrefix(tenant.getCode(), entityPrefix);
        if (!normalizedCode.startsWith(expectedPrefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code must start with " + expectedPrefix);
        }

        return new ValidatedCode(tenantId, normalizedCode);
    }

    private String normalizeTenantCode(String tenantCode) {
        if (tenantCode == null) {
            return "";
        }
        return tenantCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEntityPrefix(String entityPrefix) {
        if (entityPrefix == null) {
            return "";
        }
        return entityPrefix.trim().toUpperCase(Locale.ROOT);
    }

    public record ValidatedCode(Long tenantId, String code) {
    }
}
