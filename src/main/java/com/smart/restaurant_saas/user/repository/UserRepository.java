package com.smart.restaurant_saas.user.repository;

import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * The one read the JWT filter performs per request: account status and role state together.
     *
     * <p>An ad-hoc join rather than an association because {@code User.roleId} is a plain column
     * (see {@link AuthenticatedAccount}). The join is inner and safe: {@code users.role_id} is
     * {@code NOT NULL} with an FK to {@code roles(id)} (V14), so every user has exactly one role
     * row. If that ever stops holding this returns empty and the caller denies the request —
     * failing closed is the correct direction for an authentication check.
     *
     * <p>By id alone, not by (id, tenantId): the tenant is itself derived from the principal, and
     * resolving it here would make account validity depend on tenant resolution, which in turn
     * reads the role. Identity is the more primitive question and is answered first.
     */
    @Query("""
            select new com.smart.restaurant_saas.user.repository.AuthenticatedAccount(
                u.id, u.tenantId, u.username, u.status, r.code, r.active,
                d.id, d.tenantId, d.active)
            from User u
            join Role r on r.id = u.roleId
            left join Device d on d.id = :deviceId
            where u.id = :userId
            """)
    Optional<AuthenticatedAccount> findAccountForAuthentication(
            @Param("userId") Long userId,
            @Param("deviceId") Long deviceId);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    Optional<User> findByIdAndTenantIdAndStatusNot(Long id, Long tenantId, UserStatus status);

    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

    List<User> findByTenantIdOrderByIdDesc(Long tenantId);

    List<User> findByTenantIdAndStatusNotOrderByIdDesc(Long tenantId, UserStatus status);

    boolean existsByTenantIdAndUsername(Long tenantId, String username);

    boolean existsByTenantIdAndUsernameAndIdNot(Long tenantId, String username, Long id);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    boolean existsByTenantIdAndEmailAndIdNot(Long tenantId, String email, Long id);

    boolean existsByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, UserStatus status);
}
