package com.smart.restaurant_saas.hr.entity;

import static com.smart.restaurant_saas.common.BilingualFieldUtils.firstNonBlank;

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
        name = "hr_leave_type",
        uniqueConstraints = @UniqueConstraint(name = "uk_hr_leave_type_tenant_code", columnNames = {"tenant_id", "code"})
)
public class LeaveType extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "text")
    private String descriptionAr;

    @Column(name = "default_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultDays = BigDecimal.ZERO;

    @Column(name = "paid", nullable = false)
    private Boolean paid = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public String getName() {
        return firstNonBlank(nameEn, nameAr);
    }

    public void setName(String name) {
        this.nameEn = name;
    }
}
