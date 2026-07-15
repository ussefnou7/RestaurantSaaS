package com.smart.restaurant_saas.loyalty.customer;

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

/**
 * A tenant's loyalty customer, identified by phone. V1 captures only the minimum needed for the
 * POS "new customer" popup — no points, tiers, or contact details (see the Loyalty V1 scope).
 * {@code (tenant_id, phone)} is unique; mutating an existing customer is deferred to the future
 * Change Request workflow, so this entity is effectively first-write-wins.
 */
@Getter
@Setter
@Entity
@Table(
        name = "customer",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_tenant_phone", columnNames = {"tenant_id", "phone"})
)
public class Customer extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;
}
