package com.smart.restaurant_saas.user.repository;

import com.smart.restaurant_saas.user.entity.User;
import com.smart.restaurant_saas.user.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    Optional<User> findByIdAndTenantIdAndStatusNot(Long id, Long tenantId, UserStatus status);

    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

    Optional<User> findByUsername(String username);

    List<User> findByTenantIdOrderByIdDesc(Long tenantId);

    List<User> findByTenantIdAndStatusNotOrderByIdDesc(Long tenantId, UserStatus status);

    boolean existsByTenantIdAndUsername(Long tenantId, String username);

    boolean existsByTenantIdAndUsernameAndIdNot(Long tenantId, String username, Long id);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    boolean existsByTenantIdAndEmailAndIdNot(Long tenantId, String email, Long id);
}
