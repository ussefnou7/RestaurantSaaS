package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.LeaveType;

public record LeaveTypeResponse(
        Long id,
        String code,
        String name,
        Boolean paid,
        Boolean active
) {

    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getCode(),
                leaveType.getName(),
                leaveType.getPaid(),
                leaveType.getActive()
        );
    }
}
