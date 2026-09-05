package com.smart.restaurant_saas.auth.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.device.DeviceSecretHasher;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class RefreshTokenConcurrencyIntegrationTest {

    private static final long BASE = 973_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DeviceSecretHasher secretHasher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private CrossTenantFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CrossTenantFixture(jdbcTemplate, jwtService, BASE);
        fixture.reset(1);
        fixture.seedTenantWithUser(0, "REFRESH_LOCK");
    }

    @AfterEach
    void cleanUp() {
        fixture.reset(1);
    }

    @Test
    void refreshWaitsForTheTokenRowLockBeforeDecidingWhetherItWasUsed() throws Exception {
        String rawToken = refreshTokenService.issue(fixture.userId(0), fixture.tenantId(0), null);
        String tokenHash = secretHasher.sha256Hex(rawToken);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstUse = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                                .orElseThrow();
                        token.setRevokedAt(LocalDateTime.now());
                        lockHeld.countDown();
                        await(releaseLock);
                    }));

            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> replayWasRejected = executor.submit(() -> {
                try {
                    refreshTokenService.rotate(rawToken);
                    return false;
                } catch (AppException ex) {
                    return ex.getErrorCode() == AuthErrorCode.TOKEN_INVALID;
                }
            });

            assertThatThrownBy(() -> replayWasRejected.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLock.countDown();
            firstUse.get(5, TimeUnit.SECONDS);
            assertThat(replayWasRejected.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release refresh-token row lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding refresh-token row lock", ex);
        }
    }
}
