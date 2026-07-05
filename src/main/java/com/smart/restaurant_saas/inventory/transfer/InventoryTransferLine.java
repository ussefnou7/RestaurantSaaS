package com.smart.restaurant_saas.inventory.transfer;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Getter
@Setter
@Entity
@Table(
        name = "inventory_transfer_line",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transfer_line_transfer_material",
                columnNames = {"transfer_id", "material_id"}
        ),
        indexes = {
            @Index(name = "idx_transfer_line_tenant_transfer",
                   columnList = "tenant_id, transfer_id"),
            @Index(name = "idx_transfer_line_material",
                   columnList = "material_id"),
            @Index(name = "idx_transfer_line_dispatch_tx",
                   columnList = "dispatch_transaction_id"),
            @Index(name = "idx_transfer_line_receive_tx",
                   columnList = "receive_transaction_id")
        }
)
public class InventoryTransferLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal requestedQuantity;

    @Column(name = "dispatched_quantity", precision = 18, scale = 6)
    private BigDecimal dispatchedQuantity;

    @Column(name = "received_quantity", precision = 18, scale = 6)
    private BigDecimal receivedQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCostSnapshot = BigDecimal.ZERO;

    @Column(name = "dispatch_transaction_id")
    private Long dispatchTransactionId;

    @Column(name = "receive_transaction_id")
    private Long receiveTransactionId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
