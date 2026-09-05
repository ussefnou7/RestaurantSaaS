package com.smart.restaurant_saas.auth;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the auth module.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /**
     * The token is valid but the account behind it is no longer usable — deactivated, locked or
     * soft-deleted. Deliberately one code for all three: the client must not be able to tell an
     * administratively disabled account from a security lockout, because that distinction is a
     * probing oracle. Which status it actually was goes to the log, not the response.
     *
     * <p>The frontend treats this as "sign out and return to login", not as a retryable error.
     */
    USER_INACTIVE(HttpStatus.UNAUTHORIZED),

    /**
     * The user is ACTIVE but the role they hold has been deactivated. Deactivating a role is a
     * full lockout of everyone holding it, not a reduction of their permissions — permissions are
     * a direct user→permission grant that the role does not participate in. There is no partial
     * state, and that is coherent precisely because the role grants nothing.
     *
     * <p>Distinct from {@link #USER_INACTIVE} because the two describe different situations to
     * whoever fields the support call ("your account was disabled" vs "your job role was
     * disabled"). The client's reaction to both is identical: sign out.
     */
    ROLE_INACTIVE(HttpStatus.UNAUTHORIZED),

    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    POS_LOGIN_NOT_PERMITTED(HttpStatus.FORBIDDEN),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DEVICE_BRANCH_MISMATCH(HttpStatus.FORBIDDEN);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
