package com.smart.restaurant_saas.hr.entity;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "employee_leave_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hr_leave_balance_employee_type_year",
                columnNames = {"tenant_id", "employee_id", "leave_type_id", "year"}
        )
)
public class LeaveBalance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "opening_balance", nullable = false, precision = 8, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "assigned_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal assignedDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "remaining_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal remainingDays = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
