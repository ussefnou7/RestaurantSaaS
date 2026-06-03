package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.Salary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryRepository extends JpaRepository<Salary, Long> {

    List<Salary> findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc(Long tenantId, Long employeeId);

    Optional<Salary> findByTenantIdAndEmployeeIdAndActiveTrue(Long tenantId, Long employeeId);
}
