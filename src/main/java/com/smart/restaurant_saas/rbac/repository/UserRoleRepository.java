package com.smart.restaurant_saas.rbac.repository;

import com.smart.restaurant_saas.rbac.entity.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByTenantIdAndUserId(Long tenantId, Long userId);

    boolean existsByRoleId(Long roleId);

    @Query("""
            select case when count(ur) > 0 then true else false end
            from UserRole ur, User u
            where ur.tenantId = :tenantId
              and ur.branchId = :branchId
              and u.id = ur.userId
              and u.tenantId = ur.tenantId
              and u.status = com.smart.restaurant_saas.user.enums.UserStatus.ACTIVE
            """)
    boolean existsActiveUserAssignedToBranch(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId
    );
}
