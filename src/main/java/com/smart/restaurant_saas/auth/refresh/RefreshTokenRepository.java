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
}
