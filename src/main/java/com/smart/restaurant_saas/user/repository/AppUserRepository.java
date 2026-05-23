package com.smart.restaurant_saas.user.repository;

import com.smart.restaurant_saas.user.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    List<AppUser> findByTenantIdOrderByIdDesc(Long tenantId);

    boolean existsByTenantIdAndUsername(Long tenantId, String username);

    boolean existsByTenantIdAndUsernameAndIdNot(Long tenantId, String username, Long id);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    boolean existsByTenantIdAndEmailAndIdNot(Long tenantId, String email, Long id);
}
