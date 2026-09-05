package com.smart.restaurant_saas.user.repository;

import com.smart.restaurant_saas.rbac.enums.RoleCode;
import com.smart.restaurant_saas.user.enums.UserStatus;

/**
 * Everything the JWT filter needs to decide whether a token holder may proceed, fetched in one
 * query.
 *
 * <p>{@code User} has no {@code @ManyToOne} to {@code Role} — it carries a raw
 * {@code role_id} column — so there is no association to fetch eagerly and no lazy proxy to
 * initialise. That is why this is a projection over an ad-hoc join rather than a fetch-strategy
 * change: the filter needs user state and role state together on every request, and this gets
 * both without touching how any other read path loads a user.
 *
 * @param userId       the resolved user
 * @param tenantId     the user's live tenant, used to validate a device claim
 * @param username     the user's live username, used when rotating access tokens
 * @param status       live account status — the token's claim is not consulted
 * @param roleCode     live role code, which replaces the token's {@code roleCode} claim on the
 *                     principal so the role-level helpers stop reading a login-time snapshot
 * @param roleActive   live role state; false is a full lockout, see {@code ROLE_INACTIVE}
 * @param deviceId     the claimed device when it still exists, otherwise null
 * @param deviceTenantId the device's live tenant
 * @param deviceActive the device's live active flag
 */
public record AuthenticatedAccount(
        Long userId,
        Long tenantId,
        String username,
        UserStatus status,
        RoleCode roleCode,
        Boolean roleActive,
        Long deviceId,
        Long deviceTenantId,
        Boolean deviceActive
) {

    public boolean isUserActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isRoleActive() {
        return Boolean.TRUE.equals(roleActive);
    }

    public boolean isDeviceActiveForUserTenant(Long claimedDeviceId) {
        if (claimedDeviceId == null) {
            return true;
        }
        return claimedDeviceId.equals(deviceId)
                && tenantId.equals(deviceTenantId)
                && Boolean.TRUE.equals(deviceActive);
    }
}
