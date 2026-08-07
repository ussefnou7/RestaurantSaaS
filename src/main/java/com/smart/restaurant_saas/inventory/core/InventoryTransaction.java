package com.smart.restaurant_saas.inventory.core;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "inventory_transaction",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_inventory_transaction_tenant_idempotency",
                    columnNames = {"tenant_id", "idempotency_key"}
            )
        },
        indexes = {
            @Index(name = "idx_inv_tx_tenant_wh_material_date",
                   columnList = "tenant_id, warehouse_id, material_id, movement_date"),
            @Index(name = "idx_inv_tx_tenant_wh_created_at",
                   columnList = "tenant_id, warehouse_id, created_at"),
            @Index(name = "idx_inv_tx_tenant_type_date",
                   columnList = "tenant_id, transaction_type, transaction_date"),
            @Index(name = "idx_inv_tx_reference",
                   columnList = "reference_type, reference_id"),
            // Serves the date-ranged, reference-scoped ledger reports (shrinkage, waste
            // analysis). movement_date is the business date the writers stamp; see V41.
            @Index(name = "idx_inv_tx_tenant_reference_movement_date",
                   columnList = "tenant_id, reference_type, movement_date"),
            @Index(name = "idx_inv_tx_reverses",
                   columnList = "reverses_transaction_id")
        }
)
public class InventoryTransaction extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private InventoryTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private InventoryTransactionDirection direction;

    @Column(name = "entered_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal enteredQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_uom_id", nullable = false)
    private Uom enteredUom;

    @Column(name = "stock_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_uom_id", nullable = false)
    private Uom stockUom;

    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 18, scale = 6)
    private BigDecimal totalCost;

    @Column(name = "reference_type", length = 100)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /** The originating purchase invoice line, when known; null otherwise. Carried so an
     * inbound batch can be traced back to its exact source line (e.g. for a future return). */
    @Column(name = "source_invoice_line_id")
    private Long sourceInvoiceLineId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "reverses_transaction_id")
    private Long reversesTransactionId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "shift_id")
    private Long shiftId;

    @PrePersist
    protected void onCreate() {
        if (getCreatedAt() == null) {
            setCreatedAt(LocalDateTime.now());
        }
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        // movementDate is the business date of the event; default it to the record
        // timestamp when the caller did not supply one.
        if (movementDate == null) {
            movementDate = transactionDate;
        }
    }
}
