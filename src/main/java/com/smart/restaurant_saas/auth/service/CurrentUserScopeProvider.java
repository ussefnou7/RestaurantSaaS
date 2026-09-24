package com.smart.restaurant_saas.auth.service;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single answer to "which branch's data may this request see" (D135).
 *
 * <p>A user sees one branch or every branch. The gate is the role's {@code branchScoped} flag, not
 * the role's name: the owner sees everything because the owner role is not branch-scoped, and a
 * tenant can mark an accountant or an area manager unscoped without a code change.
 *
 * <p><strong>The branch is never read from the request.</strong> It is resolved from the
 * authenticated principal, which {@code JwtAuthenticationFilter} stamps from the database on every
 * request — the same reasoning, and the same failure it prevents, as
 * {@code CurrentTenantProvider.getCurrentTenantId()}: an input that is never trusted cannot be
 * forged, and cannot be forgotten on one path out of many. A {@code branchId} query parameter is
 * therefore a *narrowing request*, never a grant; see {@link #resolveBranchFilter(Long)}.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserScopeProvider {

    private final CurrentTenantProvider currentTenantProvider;

    /** True when the caller sees every branch in the tenant. */
    public boolean isTenantScoped() {
        return currentTenantProvider.isSysAdmin() || !currentTenantProvider.isBranchScoped();
    }

    /** The caller's own branch, empty when they see every branch. */
    public Optional<Long> getCurrentBranchId() {
        if (isTenantScoped()) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentTenantProvider.getBranchId());
    }

    /**
     * The branch id a list query should filter on, or null for every branch.
     *
     * <p>The rule that makes this load-bearing is the <em>default</em>, not the comparison: for a
     * branch-scoped caller an omitted parameter means <strong>their own branch</strong>, where it
     * used to mean all of them. Passing a foreign branch id is a 403 rather than an empty list —
     * an empty list teaches the caller that the branch is empty, and hides the guard from tests.
     *
     * @param requestedBranchId the caller's optional narrowing request, may be null
     */
    public Long resolveBranchFilter(Long requestedBranchId) {
        if (isTenantScoped()) {
            return requestedBranchId;
        }
        Long ownBranchId = requireOwnBranch();
        if (requestedBranchId == null || ownBranchId.equals(requestedBranchId)) {
            return ownBranchId;
        }
        throw forbidden(requestedBranchId);
    }

    /** Guards a single record whose branch is already known. */
    public void ensureCanAccessBranch(Long branchId) {
        if (isTenantScoped()) {
            return;
        }
        if (!requireOwnBranch().equals(branchId)) {
            throw forbidden(branchId);
        }
    }

    /**
     * Guards a write that can move a record between branches — both sides of it.
     *
     * <p>A move has two branches and checking one is not checking the move. Guarding only the
     * target lets a scoped caller pull a record they cannot see *into* their own branch; guarding
     * only the source lets them push one out of it. The update is addressed by id, so neither
     * check is implied by the other.
     */
    public void ensureCanMoveBetweenBranches(Long currentBranchId, Long targetBranchId) {
        ensureCanAccessBranch(currentBranchId);
        ensureCanAccessBranch(targetBranchId);
    }

    /**
     * Refuses a branch-scoped caller's explicit request for rows that belong to no branch.
     *
     * <p>Three entities allow a null branch — {@code Expense}, {@code Warehouse} and
     * {@code IncomingOrderRequest} — and such a row is tenant-level data, not branch data. A
     * scoped caller's ordinary list already excludes them, because filtering on
     * {@code branch_id = :own} cannot match a null. This guards the case where they ask for them
     * *by name*: silently returning nothing would satisfy the letter of the filter while telling
     * the caller the tenant has no such records.
     */
    public void ensureCanAccessUnbranched() {
        if (isTenantScoped()) {
            return;
        }
        throw new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                "Branch-scoped caller requested records belonging to no branch",
                ErrorParams.of("field", "unbranchedOnly"));
    }

    /**
     * A scoped role with no branch is a data fault, and it denies rather than widens: the
     * alternative reading — "no branch means no restriction" — turns a broken row into full
     * visibility.
     */
    private Long requireOwnBranch() {
        Long branchId = currentTenantProvider.getBranchId();
        if (branchId == null) {
            throw new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                    "Branch-scoped role carries no branch",
                    ErrorParams.of("field", "branchId"));
        }
        return branchId;
    }

    private AuthorizationException forbidden(Long branchId) {
        return new AuthorizationException(AuthErrorCode.ACCESS_DENIED,
                "Access to this branch is forbidden",
                ErrorParams.of("branchId", branchId));
    }
}
