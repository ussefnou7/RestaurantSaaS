package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.common.AuthenticationException;
import com.smart.restaurant_saas.common.ErrorParams;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DevicePairingThrottleService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, FailureWindow> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String clientKey) {
        Instant now = Instant.now();
        failures.computeIfPresent(clientKey, (key, current) -> current.isExpired(now) ? null : current);
        FailureWindow current = failures.get(clientKey);
        if (current != null && current.attempts() >= MAX_FAILED_ATTEMPTS) {
            throw locked(current, now);
        }
    }

    public void recordFailure(String clientKey) {
        Instant now = Instant.now();
        if (failures.size() >= MAX_TRACKED_CLIENTS) {
            failures.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            if (failures.size() >= MAX_TRACKED_CLIENTS && !failures.containsKey(clientKey)) {
                failures.keySet().stream().findFirst().ifPresent(failures::remove);
            }
        }
        FailureWindow current = failures.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new FailureWindow(1, now.plus(WINDOW));
            }
            return new FailureWindow(existing.attempts() + 1, existing.expiresAt());
        });
        if (current.attempts() >= MAX_FAILED_ATTEMPTS) {
            throw locked(current, now);
        }
    }

    public void clear(String clientKey) {
        failures.remove(clientKey);
    }

    private AuthenticationException locked(FailureWindow window, Instant now) {
        long retryAfterSeconds = Math.max(1L, Duration.between(now, window.expiresAt()).getSeconds());
        return new AuthenticationException(
            DeviceErrorCode.DEVICE_PAIRING_TEMPORARILY_LOCKED,
            "Too many failed device pairing attempts",
            ErrorParams.of("retryAfterSeconds", retryAfterSeconds));
    }

    private record FailureWindow(int attempts, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
