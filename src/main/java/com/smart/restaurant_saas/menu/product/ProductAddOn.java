package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Menu-side add-on suggestion: links a host product to another tenant product suggested as an
 * add-on. Plain ids (no eager associations), mirroring the codebase preference. No runtime
 * ordering/consumption effect — when ordered, an add-on is just a normal independent order line.
 */
@Getter
@Setter
@Entity
@Table(name = "product_add_on")
public class ProductAddOn extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "add_on_product_id", nullable = false)
    private Long addOnProductId;
}
