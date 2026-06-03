package com.smart.restaurant_saas.hr.dto.response;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.englishOrLegacy;
import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

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
        String employeeNameEn,
        String employeeNameAr,
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        String leaveTypeNameEn,
        String leaveTypeNameAr,
        Long leaveBalanceId,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal daysCount,
        String reason,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static LeaveRequestResponse from(
            LeaveRequest leaveRequest,
            Employee employee,
            LeaveType leaveType
    ) {
        String employeeNameEn = employee == null
                ? null
                : englishOrLegacy(employee.getFullNameEn(), employee.getFullNameAr(), employee.getFullName());
        return new LeaveRequestResponse(
                leaveRequest.getId(),
                leaveRequest.getBranchId(),
                leaveRequest.getEmployeeId(),
                employee == null ? null : employee.getCode(),
                employee == null ? null : firstNonBlank(employee.getFullName(), employeeNameEn, employee.getFullNameAr()),
                employeeNameEn,
                employee == null ? null : employee.getFullNameAr(),
                leaveRequest.getLeaveTypeId(),
                leaveType == null ? null : leaveType.getCode(),
                leaveType == null ? null : leaveType.getName(),
                leaveType == null ? null : leaveType.getNameEn(),
                leaveType == null ? null : leaveType.getNameAr(),
                leaveRequest.getLeaveBalanceId(),
                leaveRequest.getFromDate(),
                leaveRequest.getToDate(),
                leaveRequest.getDaysCount(),
                leaveRequest.getReason(),
                leaveRequest.getStatus().name(),
                leaveRequest.getNotes(),
                leaveRequest.getCreatedAt(),
                leaveRequest.getUpdatedAt()
        );
    }
}
