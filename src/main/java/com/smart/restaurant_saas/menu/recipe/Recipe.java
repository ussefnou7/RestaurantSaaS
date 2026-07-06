package com.smart.restaurant_saas.menu.recipe;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.menu.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Immutable recipe version header.
 * Invariant: RecipeService must ensure at most one active recipe per (tenant, product)
 * by serializing version creation inside a single transaction.
 */
@Getter
@Setter
@Entity
@Table(name = "recipe")
public class Recipe extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
