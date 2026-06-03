package com.smart.restaurant_saas.hr.dto.response;

import com.smart.restaurant_saas.hr.entity.SalaryAdjustment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryAdjustmentResponse(
        Long id,
        Long tenantId,
        Long employeeId,
        Long branchId,
        String type,
        BigDecimal amount,
        LocalDate adjustmentDate,
        String reason,
        String notes,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SalaryAdjustmentResponse from(SalaryAdjustment adjustment) {
        return new SalaryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getTenantId(),
                adjustment.getEmployeeId(),
                adjustment.getBranchId(),
                adjustment.getType().name(),
                adjustment.getAmount(),
                adjustment.getAdjustmentDate(),
                adjustment.getReason(),
                adjustment.getNotes(),
                adjustment.getActive(),
                adjustment.getCreatedAt(),
                adjustment.getUpdatedAt()
        );
    }
}
