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

    /**
     * The token's signature is good but its expiry has passed. Separated from
     * {@link #TOKEN_INVALID} because it is the one auth failure that is completely routine — with
     * a 24h lifetime every active user hits it daily — and the frontend should sign them out
     * silently rather than showing an error.
     */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),

    /**
     * Malformed token, bad signature, missing/unparseable claim, or an Authorization header that
     * is not a Bearer token. One code for all of them: a client cannot act differently on any of
     * these, and the distinction is a log concern. Splitting them would also describe the
     * cryptographic failure mode to whoever is probing it.
     */
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
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
