package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.user.entity.User;
import java.time.LocalDateTime;

public record LoginAttemptResult(
        Status status,
        User user,
        long retryAfterSeconds,
        LocalDateTime lockedUntil
) {

    public enum Status {
        AUTHENTICATED,
        INVALID_CREDENTIALS,
        TEMPORARILY_LOCKED
    }

    static LoginAttemptResult authenticated(User user) {
        return new LoginAttemptResult(Status.AUTHENTICATED, user, 0L, null);
    }

    static LoginAttemptResult invalidCredentials() {
        return new LoginAttemptResult(Status.INVALID_CREDENTIALS, null, 0L, null);
    }

    static LoginAttemptResult temporarilyLocked(User user, long retryAfterSeconds) {
        return new LoginAttemptResult(
                Status.TEMPORARILY_LOCKED,
                user,
                retryAfterSeconds,
                user.getLockedUntil());
    }
}
