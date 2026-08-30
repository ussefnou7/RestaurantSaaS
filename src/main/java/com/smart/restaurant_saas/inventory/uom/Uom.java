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

    @Column(name = "symbol_ar", length = 50)
    private String symbolAr;

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
     *
     * Always relative to the ROOT of the chain, never to an intermediate
     * parent — {@code UomService.buildUom} normalizes it on write and
     * {@code ck_uom_root_factor} pins the root case. A 25 kg sack stores
     * 25000 here, not 25.
     */
    @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorToBase;

    /**
     * The factor exactly as the user typed it, against {@link #enteredAgainstUom}
     * rather than against the root. The 25 kg sack stores 25 here and 25000 in
     * {@link #factorToBase}.
     *
     * Kept so the edit form can show what was entered; never used for
     * arithmetic. Written only by {@code UomService.buildUom}, alongside the
     * other three, so the pair can never drift from factorToBase.
     */
    @Column(name = "entered_factor", nullable = false, precision = 18, scale = 6)
    private BigDecimal enteredFactor;

    /**
     * The Uom the user picked as the parent — not necessarily the root.
     * NULL for roots, which were not entered against anything.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_against_uom_id")
    private Uom enteredAgainstUom;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
