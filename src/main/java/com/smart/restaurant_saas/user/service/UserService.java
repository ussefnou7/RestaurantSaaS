package com.smart.restaurant_saas.user.service;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.rbac.dto.request.AssignUserRoleRequest;
import com.smart.restaurant_saas.rbac.enums.PermissionScope;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.rbac.service.UserRoleService;
import com.smart.restaurant_saas.tenant.Tenant;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.dto.request.CreateTenantOwnerRequest;
import com.smart.restaurant_saas.user.dto.request.CreateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.TenantUserResponse;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
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
public class UserService {

    private static final long SYSTEM_TENANT_ID = 0L;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleService userRoleService;

    @Transactional
    public TenantUserResponse createOwner(Long tenantId, CreateTenantOwnerRequest request) {
        Tenant tenant = findTenant(tenantId);
        if (tenant.getId() == SYSTEM_TENANT_ID) {
            throw new ApiException("Cannot create an owner for the system tenant");
        }

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new ApiException("Username already exists for tenant: " + username);
        }
        if (email != null && userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ApiException("Email already exists for tenant: " + email);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        userRoleService.assignUserRole(
                tenantId,
                savedUser.getId(),
                new AssignUserRoleRequest(RoleCode.OWNER.name(), PermissionScope.TENANT.name(), null)
        );

        return TenantUserResponse.from(savedUser);
    }

    @Transactional
    public TenantUserResponse createUser(Long tenantId, CreateTenantUserRequest request) {
        validateTenantExists(tenantId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
            throw new ApiException("Username already exists for tenant: " + username);
        }
        if (email != null && userRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ApiException("Email already exists for tenant: " + email);
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        if (hasRoleAssignmentFields(request)) {
            userRoleService.assignUserRole(
                    tenantId,
                    savedUser.getId(),
                    new AssignUserRoleRequest(request.roleCode(), request.scope(), request.branchId())
            );
        }

        return TenantUserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public List<TenantUserResponse> listUsers(Long tenantId) {
        validateTenantExists(tenantId);
        return userRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
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
        User user = findUser(tenantId, userId);

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (!user.getUsername().equals(username)
                && userRepository.existsByTenantIdAndUsernameAndIdNot(tenantId, username, userId)) {
            throw new ApiException("Username already exists for tenant: " + username);
        }
        if (email != null
                && !Objects.equals(user.getEmail(), email)
                && userRepository.existsByTenantIdAndEmailAndIdNot(tenantId, email, userId)) {
            throw new ApiException("Email already exists for tenant: " + email);
        }

        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(trimToNull(request.phone()));

        return TenantUserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public TenantUserResponse updateUserStatus(Long tenantId, Long userId, UpdateTenantUserStatusRequest request) {
        validateTenantExists(tenantId);
        User user = findUser(tenantId, userId);
        user.setStatus(parseStatus(request.status()));

        return TenantUserResponse.from(userRepository.saveAndFlush(user));
    }

    private void validateTenantExists(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException("Tenant not found: " + tenantId);
        }
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException("Tenant not found: " + tenantId));
    }

    private User findUser(Long tenantId, Long userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
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

    private boolean hasRoleAssignmentFields(CreateTenantUserRequest request) {
        return request.roleCode() != null || request.scope() != null || request.branchId() != null;
    }

    private UserStatus parseStatus(String status) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            return UserStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid user status: " + status
                    + ". Allowed values: " + Arrays.toString(UserStatus.values()));
        }
    }
}
