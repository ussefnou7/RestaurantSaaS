package com.smart.restaurant_saas.branch;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantId(Long tenantId);
}
