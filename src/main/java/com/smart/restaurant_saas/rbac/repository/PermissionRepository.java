package com.smart.restaurant_saas.rbac.repository;

import com.smart.restaurant_saas.rbac.entity.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByActiveTrueOrderByModuleAscCodeAsc();

    List<Permission> findByCodeInAndActiveTrue(Collection<String> codes);

    Optional<Permission> findByCodeAndActiveTrue(String code);
}
