package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.LeaveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByTenantIdOrderByIdDesc(Long tenantId);

    List<LeaveType> findByTenantIdAndActiveTrueOrderByIdAsc(Long tenantId);

    Optional<LeaveType> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    Optional<LeaveType> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);
}
