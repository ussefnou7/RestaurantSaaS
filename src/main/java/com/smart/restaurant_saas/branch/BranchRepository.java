package com.smart.restaurant_saas.branch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantId(Long tenantId);

    List<Branch> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    long countByTenantIdAndActiveTrue(Long tenantId);
}
