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
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "hr_employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_hr_employees_tenant_code", columnNames = {"tenant_id", "code"})
)
public class Employee extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "full_name_en")
    private String fullNameEn;

    @Column(name = "full_name_ar")
    private String fullNameAr;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "national_id", length = 100)
    private String nationalId;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "address_en", columnDefinition = "text")
    private String addressEn;

    @Column(name = "address_ar", columnDefinition = "text")
    private String addressAr;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal salary;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
