package com.smart.restaurant_saas.inventory.physicalcount;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Getter
@Setter
@Entity
@Table(
        name = "physical_count_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_physical_count_line_count_material",
                columnNames = {"physical_count_id", "material_id"}
        ),
        indexes = {
            @Index(name = "idx_pc_line_tenant_count",
                   columnList = "tenant_id, physical_count_id"),
            @Index(name = "idx_pc_line_material",
                   columnList = "material_id"),
            @Index(name = "idx_pc_line_adjustment_tx",
                   columnList = "adjustment_transaction_id")
        }
)
public class PhysicalCountLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "physical_count_id", nullable = false)
    private PhysicalCount physicalCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "expected_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal expectedQuantity;

    @Column(name = "counted_quantity", precision = 18, scale = 6)
    private BigDecimal countedQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "variance", precision = 18, scale = 6)
    private BigDecimal variance;

    @Column(name = "unit_cost_at_freeze", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCostAtFreeze = BigDecimal.ZERO;

    @Column(name = "variance_value", precision = 18, scale = 6)
    private BigDecimal varianceValue;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "adjustment_transaction_id")
    private Long adjustmentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", nullable = false, length = 30)
    private CountLineAction actionTaken = CountLineAction.PENDING;

    /**
     * Expected quantity at {@link #countedAt}: the frozen expectation plus signed movements through
     * that instant, converted into this line's UOM. Frozen when the count is reconciled.
     */
    @Column(name = "adjusted_expected_quantity", precision = 18, scale = 6)
    private BigDecimal adjustedExpectedQuantity;
}
