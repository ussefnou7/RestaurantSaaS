package com.smart.restaurant_saas.inventory.material;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Getter
@Setter
@Entity
@Table(
        name = "material",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_tenant_code", columnNames = {"tenant_id", "code"})
)
public class Material extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id")
    private MaterialCatalog catalog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private MaterialCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_uom_id", nullable = false)
    private Uom stockUom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "display_uom_id", nullable = false)
    private Uom displayUom;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
