package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.SalaryAddition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryAdditionRepository extends JpaRepository<SalaryAddition, Long> {

    List<SalaryAddition> findByTenantIdOrderByIdDesc(Long tenantId);

    List<SalaryAddition> findByTenantIdAndBranchIdOrderByIdDesc(Long tenantId, Long branchId);

    Optional<SalaryAddition> findByIdAndTenantId(Long id, Long tenantId);
}
