package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "order_consumption_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oce_tenant_idempotency",
                columnNames = {"tenant_id", "idempotency_key"}
        ),
        indexes = {
            @Index(name = "idx_oce_aggregation",
                   columnList = "tenant_id, posted_to_ledger, warehouse_id, material_id, business_date"),
            @Index(name = "idx_oce_material_consumed",
                   columnList = "tenant_id, material_id, consumed_at"),
            @Index(name = "idx_oce_order",
                   columnList = "order_id"),
            @Index(name = "idx_oce_posted_tx",
                   columnList = "posted_transaction_id"),
            @Index(name = "idx_oce_reverses",
                   columnList = "reverses_event_id")
        }
)
public class OrderConsumptionEvent extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_line_id")
    private Long orderLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCostSnapshot = BigDecimal.ZERO;

    @Column(name = "total_cost_snapshot", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalCostSnapshot = BigDecimal.ZERO;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "consumed_at", nullable = false)
    private LocalDateTime consumedAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "posted_to_ledger", nullable = false)
    private Boolean postedToLedger = false;

    @Column(name = "posted_transaction_id")
    private Long postedTransactionId;

    @Column(name = "reverses_event_id")
    private Long reversesEventId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (consumedAt == null) {
            consumedAt = LocalDateTime.now();
        }
        if (businessDate == null) {
            businessDate = consumedAt.toLocalDate();
        }
    }
}
