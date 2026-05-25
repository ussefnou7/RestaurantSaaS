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

    boolean existsByTenantIdAndEmployeeCode(Long tenantId, String employeeCode);

    boolean existsByTenantIdAndEmployeeCodeAndIdNot(Long tenantId, String employeeCode, Long id);

    boolean existsByTenantIdAndJobTitleIdAndActiveTrue(Long tenantId, Long jobTitleId);

    boolean existsByTenantIdAndAppUserIdAndActiveTrue(Long tenantId, Long appUserId);

    boolean existsByTenantIdAndAppUserIdAndActiveTrueAndIdNot(Long tenantId, Long appUserId, Long id);
}
