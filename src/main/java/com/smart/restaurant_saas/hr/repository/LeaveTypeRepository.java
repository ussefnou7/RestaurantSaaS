package com.smart.restaurant_saas.hr.repository;

import com.smart.restaurant_saas.hr.entity.LeaveType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByActiveTrueOrderByIdAsc();

    Optional<LeaveType> findByIdAndActiveTrue(Long id);
}
