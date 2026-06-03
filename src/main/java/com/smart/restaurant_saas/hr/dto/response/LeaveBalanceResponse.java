package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.LeaveBalance;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LeaveBalanceResponse(
        Long id,
        Long tenantId,
        Long employeeId,
        Long branchId,
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeNameEn,
        String leaveTypeNameAr,
        Integer year,
        BigDecimal openingBalance,
        BigDecimal assignedDays,
        BigDecimal usedDays,
        BigDecimal remainingDays,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static LeaveBalanceResponse from(LeaveBalance balance, LeaveType leaveType) {
        return new LeaveBalanceResponse(
                balance.getId(),
                balance.getTenantId(),
                balance.getEmployeeId(),
                balance.getBranchId(),
                balance.getLeaveTypeId(),
                leaveType == null ? null : leaveType.getCode(),
                leaveType == null ? null : leaveType.getNameEn(),
                leaveType == null ? null : leaveType.getNameAr(),
                balance.getYear(),
                balance.getOpeningBalance(),
                balance.getAssignedDays(),
                balance.getUsedDays(),
                balance.getRemainingDays(),
                balance.getActive(),
                balance.getNotes(),
                balance.getCreatedAt(),
                balance.getUpdatedAt()
        );
    }
}
