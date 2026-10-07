package com.smart.restaurant_saas.branch;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantId(Long tenantId);

    /**
     * Batch name lookup for ids already resolved elsewhere — the dashboard's alert strip turns a
     * handful of branch ids into names in one query rather than one per row.
     *
     * <p>Tenant-scoped like every other method here, which is what makes it safe to call with ids
     * that came out of an aggregate query: a foreign id simply fails to match instead of leaking a
     * name across tenants.
     */
    List<Branch> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    List<Branch> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    long countByTenantIdAndActiveTrue(Long tenantId);
}
