package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.enums.LeaveRequestStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByTenantIdOrderByIdDesc(Long tenantId);

    List<LeaveRequest> findByTenantIdAndBranchIdOrderByIdDesc(Long tenantId, Long branchId);

    List<LeaveRequest> findByTenantIdAndEmployeeIdOrderByIdDesc(Long tenantId, Long employeeId);

    Optional<LeaveRequest> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndEmployeeIdAndStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            Long tenantId, Long employeeId, LeaveRequestStatus status,
            LocalDate toDate, LocalDate fromDate);
}
