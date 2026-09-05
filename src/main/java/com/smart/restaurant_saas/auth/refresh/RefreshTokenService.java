package com.smart.restaurant_saas.auth.refresh;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.dto.response.TokenRefreshResponse;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.common.AuthenticationException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.device.DeviceSecretHasher;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.user.repository.AuthenticatedAccount;
import com.smart.restaurant_saas.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final DeviceSecretHasher secretHasher;
    private final TenantTimeZoneService tenantTimeZoneService;

    @Value("${app.jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Transactional
    public String issue(Long userId, Long tenantId, Long deviceId) {
        String rawToken = secretHasher.generateSecret();
        LocalDateTime now = nowFor(tenantId);

        RefreshToken token = new RefreshToken();
        token.setTenantId(tenantId);
        token.setUserId(userId);
        token.setDeviceId(deviceId);
        token.setTokenHash(secretHasher.sha256Hex(rawToken));
        token.setExpiresAt(now.plusDays(refreshExpirationDays));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public TokenRefreshResponse rotate(String rawToken) {
        RefreshToken current = findForUpdate(rawToken);
        LocalDateTime now = nowFor(current.getTenantId());

        if (current.isRevoked()) {
            log.warn("Rejected replay of refresh token {}", current.getId());
            throw invalidRefreshToken();
        }
        if (current.isExpiredAt(now)) {
            throw new AuthenticationException(AuthErrorCode.TOKEN_EXPIRED,
                    "Refresh token has expired",
                    ErrorParams.of("tokenType", "refresh"));
        }

        AuthenticatedAccount account = userRepository.findAccountForAuthentication(
                        current.getUserId(), current.getDeviceId())
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.USER_INACTIVE,
                        "Refresh token user no longer exists"));
        ensureAccountCanRefresh(account, current.getDeviceId());

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);

        String replacement = issue(account.userId(), account.tenantId(), current.getDeviceId());
        String accessToken = jwtService.generateAccessToken(
                account.userId(),
                account.tenantId(),
                account.username(),
                account.roleCode().name(),
                current.getDeviceId());
        return new TokenRefreshResponse(accessToken, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHashForUpdate(secretHasher.sha256Hex(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevokedAt(nowFor(token.getTenantId()));
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId, Long tenantId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, nowFor(tenantId));
    }

    private RefreshToken findForUpdate(String rawToken) {
        return refreshTokenRepository.findByTokenHashForUpdate(secretHasher.sha256Hex(rawToken))
                .orElseThrow(this::invalidRefreshToken);
    }

    private void ensureAccountCanRefresh(AuthenticatedAccount account, Long deviceId) {
        if (!account.isUserActive()) {
            throw new AuthenticationException(AuthErrorCode.USER_INACTIVE,
                    "Refresh token user status is " + account.status());
        }
        if (!account.isRoleActive()) {
            throw new AuthenticationException(AuthErrorCode.ROLE_INACTIVE,
                    "Refresh token role is inactive: " + account.roleCode());
        }
        if (!account.isDeviceActiveForUserTenant(deviceId)) {
            throw new AuthenticationException(AuthErrorCode.DEVICE_INACTIVE,
                    "Refresh token device is missing, inactive or belongs to another tenant",
                    ErrorParams.of("deviceId", deviceId));
        }
    }

    private LocalDateTime nowFor(Long tenantId) {
        return LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId));
    }

    private AuthenticationException invalidRefreshToken() {
        return new AuthenticationException(AuthErrorCode.TOKEN_INVALID,
                "Refresh token is unknown or has already been used",
                ErrorParams.of("tokenType", "refresh"));
    }
}
