package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.Employee;
import com.smart.restaurant_saas.hr.entity.LeaveRequest;
import com.smart.restaurant_saas.hr.entity.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveRequestResponse(
        Long id,
        Long branchId,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal daysCount,
        String reason,
        String status,
        String statusNote,
        Long statusChangedBy,
        LocalDateTime statusChangedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static LeaveRequestResponse from(
            LeaveRequest leaveRequest,
            Employee employee,
            LeaveType leaveType
    ) {
        return new LeaveRequestResponse(
                leaveRequest.getId(),
                leaveRequest.getBranchId(),
                leaveRequest.getEmployeeId(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null ? null : employee.getFullName(),
                leaveRequest.getLeaveTypeId(),
                leaveType == null ? null : leaveType.getCode(),
                leaveType == null ? null : leaveType.getName(),
                leaveRequest.getFromDate(),
                leaveRequest.getToDate(),
                leaveRequest.getDaysCount(),
                leaveRequest.getReason(),
                leaveRequest.getStatus().name(),
                leaveRequest.getStatusNote(),
                leaveRequest.getStatusChangedBy(),
                leaveRequest.getStatusChangedAt(),
                leaveRequest.getCreatedAt(),
                leaveRequest.getUpdatedAt()
        );
    }
}
