package com.smart.restaurant_saas.auth.refresh;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.userId = :userId and token.revokedAt is null
            """)
    int revokeAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * Retires the live refresh tokens a user already holds on one station, so a fresh login
     * replaces the previous session there instead of stacking onto it.
     *
     * <p><b>Why this is narrower than {@link #revokeAllActiveByUserId}.</b> One person can hold a
     * web session and a till session at once — different devices, and on the web no device at
     * all. Revoking every token on login would sign a manager out of admin-web the moment they
     * signed in at a drawer. Matching on the device, null-for-web included, retires only the
     * session actually being replaced.
     *
     * <p>Without this, every app relaunch mints another 7-day credential and leaves the previous
     * one live. Closing a shift revokes one token, so D127's promise — that a cashier's effective
     * session lifetime is the length of their shift — quietly stops holding once a device has
     * been restarted a few times, which on a tablet is a daily event.
     */
    @Modifying
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.userId = :userId
              and token.revokedAt is null
              and ((:deviceId is null and token.deviceId is null)
                   or token.deviceId = :deviceId)
            """)
    int revokeActiveByUserIdAndDeviceId(
            @Param("userId") Long userId,
            @Param("deviceId") Long deviceId,
            @Param("revokedAt") LocalDateTime revokedAt);
}
