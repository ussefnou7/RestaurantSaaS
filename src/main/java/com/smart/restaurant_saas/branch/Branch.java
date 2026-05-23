package com.smart.restaurant_saas.branch;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "branches",
        uniqueConstraints = @UniqueConstraint(name = "uk_branches_tenant_code", columnNames = {"tenant_id", "code"})
)
public class Branch extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
