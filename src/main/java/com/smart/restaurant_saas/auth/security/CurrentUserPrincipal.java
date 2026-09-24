package com.smart.restaurant_saas.auth.security;

/**
 * The authenticated caller for one request.
 *
 * <p>{@code roleCode}, {@code branchScoped} and {@code branchId} are stamped from the database by
 * {@code JwtAuthenticationFilter}, not read from the token — see that class. The token carries no
 * branch claim at all, so a branch reassignment takes effect on the next request rather than
 * whenever the token expires (D135).
 *
 * @param branchScoped whether the caller's role confines them to one branch
 * @param branchId     that branch, or null when the role is not branch-scoped
 */
public record CurrentUserPrincipal(
        Long userId,
        Long tenantId,
        String username,
        String roleCode,
        Long deviceId,
        boolean branchScoped,
        Long branchId
) {

    /**
     * Without branch scope, for the token-parsed principal that never reaches the security
     * context. Fails closed — scoped with no branch denies every branch check rather than
     * granting all of them, should one ever leak past the filter.
     */
    public CurrentUserPrincipal(Long userId, Long tenantId, String username, String roleCode, Long deviceId) {
        this(userId, tenantId, username, roleCode, deviceId, true, null);
    }

    public CurrentUserPrincipal(Long userId, Long tenantId, String username, String roleCode) {
        this(userId, tenantId, username, roleCode, null);
    }
}
