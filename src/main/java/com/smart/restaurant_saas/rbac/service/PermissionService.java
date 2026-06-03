package com.smart.restaurant_saas.rbac.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.repository.PermissionRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final CurrentTenantProvider currentTenantProvider;

    @Transactional(readOnly = true)
    public List<PermissionResponse> listActivePermissions() {
        currentTenantProvider.getCurrentTenantId();
        return permissionRepository.findByActiveTrueOrderByModuleAscCodeAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Permission> findActivePermissionsByCodes(List<String> permissionCodes) {
        List<String> normalizedCodes = normalizePermissionCodes(permissionCodes);
        if (normalizedCodes.isEmpty()) {
            return List.of();
        }

        List<Permission> permissions = permissionRepository.findByCodeInAndActiveTrue(normalizedCodes);
        Map<String, Permission> permissionsByCode = new LinkedHashMap<>();
        for (Permission permission : permissions) {
            permissionsByCode.put(permission.getCode(), permission);
        }

        List<String> missingCodes = normalizedCodes.stream()
                .filter(code -> !permissionsByCode.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new ApiException("Permissions not found or inactive: " + String.join(", ", missingCodes));
        }

        return normalizedCodes.stream()
                .map(permissionsByCode::get)
                .toList();
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null) {
            throw new ApiException("permissionCodes is required");
        }

        Set<String> seenCodes = new LinkedHashSet<>();
        List<String> duplicateCodes = new ArrayList<>();
        List<String> normalizedCodes = new ArrayList<>();

        for (String permissionCode : permissionCodes) {
            String normalizedCode = normalizePermissionCode(permissionCode);
            if (!seenCodes.add(normalizedCode)) {
                duplicateCodes.add(normalizedCode);
            }
            normalizedCodes.add(normalizedCode);
        }

        if (!duplicateCodes.isEmpty()) {
            throw new ApiException("Duplicate permission codes are not allowed: "
                    + String.join(", ", duplicateCodes));
        }

        return normalizedCodes;
    }

    private String normalizePermissionCode(String permissionCode) {
        if (permissionCode == null) {
            throw new ApiException("Permission code is required");
        }
        String normalizedCode = permissionCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            throw new ApiException("Permission code must not be blank");
        }
        return normalizedCode;
    }
}
