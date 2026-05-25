package com.smart.restaurant_saas.rbac.repository;

import com.smart.restaurant_saas.rbac.entity.Role;
import com.smart.restaurant_saas.rbac.enums.RoleCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByActiveTrueOrderByIdAsc();

    Optional<Role> findByCode(RoleCode code);

    Optional<Role> findByCodeAndActiveTrue(RoleCode code);
}
