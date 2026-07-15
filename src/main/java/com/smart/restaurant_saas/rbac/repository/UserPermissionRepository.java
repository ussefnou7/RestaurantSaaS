package com.smart.restaurant_saas.rbac.repository;

import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.UserPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    @Query("""
            select up.permissionId
            from UserPermission up
            where up.tenantId = :tenantId
              and up.userId = :userId
            """)
    List<Long> findPermissionIdsByTenantIdAndUserId(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId
    );

    @Query("""
            select p
            from Permission p
            where p.active = true
              and p.id in (
                  select up.permissionId
                  from UserPermission up
                  where up.tenantId = :tenantId
                    and up.userId = :userId
              )
            order by p.module asc, p.code asc
            """)
    List<Permission> findActivePermissionsByTenantIdAndUserId(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId
    );

    @Query("""
            select count(up) > 0
            from UserPermission up, Permission p
            where p.id = up.permissionId
              and p.active = true
              and up.tenantId = :tenantId
              and up.userId = :userId
              and p.code = :permissionCode
            """)
    boolean existsPermissionByTenantIdAndUserIdAndCode(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("permissionCode") String permissionCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from UserPermission up
            where up.tenantId = :tenantId
              and up.userId = :userId
            """)
    void deleteByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
