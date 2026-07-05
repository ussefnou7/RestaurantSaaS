package com.smart.restaurant_saas.inventory.uom;

import com.smart.restaurant_saas.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import com.smart.restaurant_saas.inventory.core.enums.UomType;

@Getter
@Setter
@Entity
@Table(name = "uom")
public class Uom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NULL = global Uom (visible to all tenants).
     * Non-null = custom Uom owned by a specific tenant.
     * Uniqueness enforced via partial unique indexes:
     *   uk_uom_global_code on (code) WHERE tenant_id IS NULL
     *   uk_uom_tenant_code on (tenant_id, code) WHERE tenant_id IS NOT NULL
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private UomType type;

    /**
     * Self-reference to the base Uom of the same physical type.
     * NULL means this Uom IS the base for its type.
     * e.g., GRAM has baseUom = NULL; KILOGRAM has baseUom = GRAM.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_uom_id")
    private Uom baseUom;

    /**
     * Multiplier to convert a value in this Uom to the base Uom.
     * e.g., KILOGRAM.factorToBase = 1000 (1 kg = 1000 g).
     * Base Uoms always have factorToBase = 1.
     */
    @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorToBase;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
