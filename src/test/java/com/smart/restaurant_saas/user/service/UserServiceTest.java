package com.smart.restaurant_saas.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.refresh.RefreshTokenService;
import com.smart.restaurant_saas.rbac.repository.RoleRepository;
import com.smart.restaurant_saas.rbac.service.RoleService;
import com.smart.restaurant_saas.rbac.service.UserPermissionService;
import com.smart.restaurant_saas.tenant.TenantRepository;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserRequest;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private TenantRepository tenantRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        userService = new UserService(
                tenantRepository,
                userRepository,
                passwordEncoder,
                mock(RoleRepository.class),
                mock(RoleService.class),
                mock(UserPermissionService.class),
                refreshTokenService
        );
    }

    @Test
    void updateUserReplacesPasswordAndRevokesRefreshTokensWhenPasswordIsProvided() {
        User user = user();
        user.setFailedLoginAttempts(5);
        user.setLastFailedLoginAt(java.time.LocalDateTime.now());
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(5));
        stubUpdate(user);
        when(passwordEncoder.encode("Newpass1")).thenReturn("encoded:Newpass1");

        userService.updateUser(5L, 20L, request("Newpass1"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded:Newpass1");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastFailedLoginAt()).isNull();
        assertThat(user.getLockedUntil()).isNull();
        verify(refreshTokenService).revokeAllForUser(20L, 5L);
    }

    @Test
    void updateUserPreservesPasswordWhenPasswordIsOmitted() {
        User user = user();
        stubUpdate(user);

        userService.updateUser(5L, 20L, request(null));

        assertThat(user.getPasswordHash()).isEqualTo("encoded:secret");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenService, never()).revokeAllForUser(20L, 5L);
    }

    private void stubUpdate(User user) {
        when(tenantRepository.existsById(5L)).thenReturn(true);
        when(userRepository.findByIdAndTenantId(20L, 5L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
    }

    private UpdateTenantUserRequest request(String password) {
        return new UpdateTenantUserRequest("Updated User", "cashier", null, null, password);
    }

    private User user() {
        User user = new User();
        user.setId(20L);
        user.setTenantId(5L);
        user.setFullName("Cashier");
        user.setUsername("cashier");
        user.setPasswordHash("encoded:secret");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
