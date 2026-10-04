package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.Salary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryRepository extends JpaRepository<Salary, Long> {

    List<Salary> findByTenantIdAndEmployeeIdOrderByEffectiveFromDescIdDesc(Long tenantId, Long employeeId);

    Optional<Salary> findByTenantIdAndEmployeeIdAndActiveTrue(Long tenantId, Long employeeId);

    @Query("""
            select s from Salary s
            where s.tenantId = :tenantId and s.employeeId = :employeeId
              and s.effectiveFrom <= :date
              and (s.effectiveTo is null or s.effectiveTo >= :date)
            """)
    Optional<Salary> findEffectiveOn(@Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId, @Param("date") LocalDate date);
}
