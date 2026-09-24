package com.smart.restaurant_saas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.common.AuthorizationException;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * D135. The rest of the suite runs as an unscoped caller, so it proves the filter was added
 * without breaking anything — not that it restricts anyone. That is what these assert.
 */
class CurrentUserScopeProviderTest {

    private static final Long OWN_BRANCH = 7L;
    private static final Long OTHER_BRANCH = 9L;

    @Nested
    class AnUnscopedCaller {

        private final CurrentUserScopeProvider scope = provider(false, false, null);

        @Test
        void seesEveryBranchWhenNoFilterIsRequested() {
            assertThat(scope.resolveBranchFilter(null)).isNull();
        }

        @Test
        void mayNarrowToAnyBranch() {
            assertThat(scope.resolveBranchFilter(OTHER_BRANCH)).isEqualTo(OTHER_BRANCH);
        }

        @Test
        void mayReadRecordsBelongingToNoBranch() {
            assertThatCode(scope::ensureCanAccessUnbranched).doesNotThrowAnyException();
            assertThatCode(() -> scope.ensureCanAccessBranch(null)).doesNotThrowAnyException();
        }

        @Test
        void reportsItselfAsTenantScoped() {
            assertThat(scope.isTenantScoped()).isTrue();
            assertThat(scope.getCurrentBranchId()).isEmpty();
        }
    }

    @Nested
    class AScopedCaller {

        private final CurrentUserScopeProvider scope = provider(false, true, OWN_BRANCH);

        /** The default flip, and the reason every listing endpoint changes behaviour. */
        @Test
        void getsTheirOwnBranchWhenNoFilterIsRequested() {
            assertThat(scope.resolveBranchFilter(null)).isEqualTo(OWN_BRANCH);
        }

        @Test
        void mayRestateTheirOwnBranch() {
            assertThat(scope.resolveBranchFilter(OWN_BRANCH)).isEqualTo(OWN_BRANCH);
        }

        @Test
        void isRefusedAnotherBranchRatherThanGivenAnEmptyList() {
            assertThatThrownBy(() -> scope.resolveBranchFilter(OTHER_BRANCH))
                    .isInstanceOf(AuthorizationException.class)
                    .isInstanceOfSatisfying(AppException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED);
                        assertThat(ex.getParams()).containsEntry("branchId", OTHER_BRANCH);
                    });
        }

        @Test
        void isRefusedARecordFromAnotherBranch() {
            assertThatThrownBy(() -> scope.ensureCanAccessBranch(OTHER_BRANCH))
                    .isInstanceOf(AuthorizationException.class);
            assertThatCode(() -> scope.ensureCanAccessBranch(OWN_BRANCH)).doesNotThrowAnyException();
        }

        /** An unbranched row is tenant-level data, so it stays out of reach. */
        @Test
        void isRefusedRecordsBelongingToNoBranch() {
            assertThatThrownBy(scope::ensureCanAccessUnbranched)
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> scope.ensureCanAccessBranch(null))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        void reportsItsOwnBranch() {
            assertThat(scope.isTenantScoped()).isFalse();
            assertThat(scope.getCurrentBranchId()).contains(OWN_BRANCH);
        }
    }

    @Nested
    class ABrokenScope {

        /** A scoped role carrying no branch denies; reading it as "unrestricted" would widen. */
        private final CurrentUserScopeProvider scope = provider(false, true, null);

        @Test
        void deniesRatherThanFallingBackToEveryBranch() {
            assertThatThrownBy(() -> scope.resolveBranchFilter(null))
                    .isInstanceOf(AuthorizationException.class);
            assertThatThrownBy(() -> scope.ensureCanAccessBranch(OWN_BRANCH))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    class ASysAdmin {

        /** Scoped flags are ignored: a platform operator has no branch of their own. */
        private final CurrentUserScopeProvider scope = provider(true, true, OWN_BRANCH);

        @Test
        void seesEveryBranch() {
            assertThat(scope.isTenantScoped()).isTrue();
            assertThat(scope.resolveBranchFilter(null)).isNull();
            assertThat(scope.resolveBranchFilter(OTHER_BRANCH)).isEqualTo(OTHER_BRANCH);
        }
    }

    private static CurrentUserScopeProvider provider(boolean sysAdmin, boolean branchScoped, Long branchId) {
        return new CurrentUserScopeProvider(new CurrentTenantProvider(null, null) {
            @Override
            public boolean isSysAdmin() {
                return sysAdmin;
            }

            @Override
            public boolean isBranchScoped() {
                return branchScoped;
            }

            @Override
            public Long getBranchId() {
                return branchId;
            }
        });
    }
}
