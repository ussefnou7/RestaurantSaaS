package com.smart.restaurant_saas.inventory.physicalcount;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "physical_count",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_physical_count_tenant_code",
                columnNames = {"tenant_id", "code"}
        ),
        indexes = {
            @Index(name = "idx_physical_count_tenant_warehouse_status",
                   columnList = "tenant_id, warehouse_id, status"),
            @Index(name = "idx_physical_count_scheduled_date",
                   columnList = "scheduled_date")
        }
)
public class PhysicalCount extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PhysicalCountStatus status = PhysicalCountStatus.DRAFT;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "reconciled_by")
    private Long reconciledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    /** Derived from {@link #grossVarianceValue}, not the net total — see the field below. */
    @Column(name = "has_large_variance", nullable = false)
    private Boolean hasLargeVariance = false;

    /**
     * Net: the signed sum of (variance x unitCostAtFreeze) across lines. This is the accounting
     * impact — what the count did to inventory value — and it is the number to show against the
     * books. It is deliberately <em>not</em> what raises the flag: offsetting lines cancel here.
     */
    @Column(name = "large_variance_value", precision = 18, scale = 6)
    private BigDecimal largeVarianceValue;

    /**
     * Gross: the sum of ABS(variance x unitCostAtFreeze) across lines. This is the control
     * exposure — how much stock moved unexplained — and it is what {@link #hasLargeVariance} is
     * derived from. A count whose lines cancel out is more interesting than one that does not,
     * never less. NULL on counts reconciled before V51, which were evaluated on net only.
     */
    @Column(name = "gross_variance_value", precision = 18, scale = 6)
    private BigDecimal grossVarianceValue;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "physicalCount",
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhysicalCountLine> lines = new ArrayList<>();
}
