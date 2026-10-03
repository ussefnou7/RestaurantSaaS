package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginThrottleService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(5);
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantTimeZoneService tenantTimeZoneService;

    // Return the outcome so failed-attempt updates commit before AuthService raises the HTTP error.
    @Transactional
    public LoginAttemptResult authenticate(Long tenantId, String username, String rawPassword) {
        User user = userRepository.findByTenantIdAndUsernameForLogin(tenantId, username)
                .orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return LoginAttemptResult.invalidCredentials();
        }

        LocalDateTime now = LocalDateTime.now(tenantTimeZoneService.zoneFor(user.getTenantId()));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return lockedResult(user, now);
        }
        if (user.getLockedUntil() != null) {
            clearLoginFailures(user);
        }

        if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            clearLoginFailures(user);
            userRepository.saveAndFlush(user);
            return LoginAttemptResult.authenticated(user);
        }

        LocalDateTime windowStart = now.minus(FAILED_LOGIN_WINDOW);
        boolean withinWindow = user.getLastFailedLoginAt() != null
                && user.getLastFailedLoginAt().isAfter(windowStart);
        int attempts = withinWindow ? user.getFailedLoginAttempts() + 1 : 1;
        user.setFailedLoginAttempts(attempts);
        user.setLastFailedLoginAt(now);

        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockedUntil(now.plus(LOGIN_LOCK_DURATION));
            userRepository.saveAndFlush(user);
            return lockedResult(user, now);
        }
        userRepository.saveAndFlush(user);
        return LoginAttemptResult.invalidCredentials();
    }

    private LoginAttemptResult lockedResult(User user, LocalDateTime now) {
        long retryAfterSeconds = Math.max(1L, Duration.between(now, user.getLockedUntil()).getSeconds());
        return LoginAttemptResult.temporarilyLocked(user, retryAfterSeconds);
    }

    private void clearLoginFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLastFailedLoginAt(null);
        user.setLockedUntil(null);
    }
}
