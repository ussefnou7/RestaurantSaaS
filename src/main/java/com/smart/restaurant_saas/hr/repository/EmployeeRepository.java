package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.Employee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByTenantIdOrderByIdDesc(Long tenantId);

    List<Employee> findByTenantIdAndBranchIdOrderByIdDesc(Long tenantId, Long branchId);

    Optional<Employee> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Employee> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    boolean existsByTenantIdAndJobIdAndActiveTrue(Long tenantId, Long jobId);

    boolean existsByTenantIdAndUserIdAndActiveTrue(Long tenantId, Long userId);

    boolean existsByTenantIdAndUserIdAndActiveTrueAndIdNot(Long tenantId, Long userId, Long id);
}
