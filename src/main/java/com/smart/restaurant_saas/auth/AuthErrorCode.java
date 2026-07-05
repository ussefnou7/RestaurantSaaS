package com.smart.restaurant_saas.auth;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the auth module. Introduced in this pass only to give the credentials failure
 * a proper structured code + 401 status (replacing the old message-matching hack). The rest of
 * the auth module still uses the legacy {@code ApiException} pending a later migration.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
