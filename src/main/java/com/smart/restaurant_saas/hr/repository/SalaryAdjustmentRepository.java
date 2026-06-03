package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.SalaryAdjustment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryAdjustmentRepository extends JpaRepository<SalaryAdjustment, Long> {

    List<SalaryAdjustment> findByTenantIdAndEmployeeIdOrderByAdjustmentDateDescIdDesc(Long tenantId, Long employeeId);

    Optional<SalaryAdjustment> findByIdAndTenantId(Long id, Long tenantId);
}
