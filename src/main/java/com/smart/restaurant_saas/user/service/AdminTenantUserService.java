package com.smart.restaurant_saas.user.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.dto.request.CreateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.TenantUserResponse;
import com.smart.restaurant_saas.user.entity.AppUser;
import com.smart.restaurant_saas.user.enums.AppUserStatus;
import com.smart.restaurant_saas.user.repository.AppUserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminTenantUserService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantUserResponse createUser(Long tenantId, CreateTenantUserRequest request) {
        validateTenantExists(tenantId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (appUserRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new ApiException("Username already exists for tenant: " + username);
        }
        if (email != null && appUserRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ApiException("Email already exists for tenant: " + email);
        }

        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(AppUserStatus.ACTIVE);

        return TenantUserResponse.from(appUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<TenantUserResponse> listUsers(Long tenantId) {
        validateTenantExists(tenantId);
        return appUserRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(TenantUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantUserResponse getUser(Long tenantId, Long userId) {
        validateTenantExists(tenantId);
        return TenantUserResponse.from(findUser(tenantId, userId));
    }

    @Transactional
    public TenantUserResponse updateUser(Long tenantId, Long userId, UpdateTenantUserRequest request) {
        validateTenantExists(tenantId);
        AppUser user = findUser(tenantId, userId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (!user.getUsername().equals(username)
                && appUserRepository.existsByTenantIdAndUsernameAndIdNot(tenantId, username, userId)) {
            throw new ApiException("Username already exists for tenant: " + username);
        }
        if (email != null
                && !Objects.equals(user.getEmail(), email)
                && appUserRepository.existsByTenantIdAndEmailAndIdNot(tenantId, email, userId)) {
            throw new ApiException("Email already exists for tenant: " + email);
        }

        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));

        return TenantUserResponse.from(appUserRepository.saveAndFlush(user));
    }

    @Transactional
    public TenantUserResponse updateUserStatus(Long tenantId, Long userId, UpdateTenantUserStatusRequest request) {
        validateTenantExists(tenantId);
        AppUser user = findUser(tenantId, userId);
        user.setStatus(parseStatus(request.status()));

        return TenantUserResponse.from(appUserRepository.saveAndFlush(user));
    }

    private void validateTenantExists(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException("Tenant not found: " + tenantId);
        }
    }

    private AppUser findUser(Long tenantId, Long userId) {
        return appUserRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ApiException("User not found for tenant: " + userId));
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AppUserStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return AppUserStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid user status: " + status
                    + ". Allowed values: " + Arrays.toString(AppUserStatus.values()));
        }
    }
}
