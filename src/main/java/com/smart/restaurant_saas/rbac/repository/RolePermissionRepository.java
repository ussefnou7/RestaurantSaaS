package com.smart.restaurant_saas.rbac.repository;

import com.smart.restaurant_saas.rbac.entity.Permission;
import com.smart.restaurant_saas.rbac.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {

    @Query("""
            select p
            from Permission p
            where p.active = true
              and p.id in (
                  select rp.permissionId
                  from RolePermission rp
                  where rp.roleId = :roleId
              )
            order by p.module asc, p.code asc
            """)
    List<Permission> findActivePermissionsByRoleId(@Param("roleId") Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
