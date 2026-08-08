package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.menu.category.MenuCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "description_ar", columnDefinition = "text")
    private String descriptionAr;

    // Self-reference kept as a plain id (not a @ManyToOne), mirroring how this codebase prefers
    // concrete ids over eager associations. Parenthood is derived, never stored.
    @Column(name = "parent_product_id")
    private Long parentProductId;

    @Column(name = "variant_label")
    private String variantLabel;

    @Column(name = "variant_label_ar")
    private String variantLabelAr;

    @Column(name = "is_menu", nullable = false)
    private Boolean isMenu = true;

    @Column(name = "selling_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_category_id", nullable = false)
    private MenuCategory menuCategory;
}
