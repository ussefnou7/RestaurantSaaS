package com.smart.restaurant_saas.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
}
