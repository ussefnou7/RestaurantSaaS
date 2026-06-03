package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    List<LeaveBalance> findByTenantIdAndEmployeeIdAndYearOrderByIdAsc(Long tenantId, Long employeeId, Integer year);

    Optional<LeaveBalance> findByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(
            Long tenantId,
            Long employeeId,
            Long leaveTypeId,
            Integer year
    );

    Optional<LeaveBalance> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LeaveBalance> findWithLockByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LeaveBalance> findWithLockByTenantIdAndEmployeeIdAndLeaveTypeIdAndYear(
            Long tenantId,
            Long employeeId,
            Long leaveTypeId,
            Integer year
    );
}
