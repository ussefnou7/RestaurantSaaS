package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.LeaveType;

public record LeaveTypeResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String nameEn,
        String nameAr,
        String descriptionEn,
        String descriptionAr,
        java.math.BigDecimal defaultDays,
        Boolean paid,
        Boolean active
) {

    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getTenantId(),
                leaveType.getCode(),
                leaveType.getName(),
                leaveType.getNameEn(),
                leaveType.getNameAr(),
                leaveType.getDescriptionEn(),
                leaveType.getDescriptionAr(),
                leaveType.getDefaultDays(),
                leaveType.getPaid(),
                leaveType.getActive()
        );
    }
}
