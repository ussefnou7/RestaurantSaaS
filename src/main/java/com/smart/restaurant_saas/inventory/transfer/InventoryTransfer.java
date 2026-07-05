package com.smart.restaurant_saas.inventory.transfer;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.TransferStatus;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "inventory_transfer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_transfer_tenant_code",
                columnNames = {"tenant_id", "code"}
        ),
        indexes = {
            @Index(name = "idx_transfer_tenant_status",
                   columnList = "tenant_id, status"),
            @Index(name = "idx_transfer_source",
                   columnList = "source_warehouse_id"),
            @Index(name = "idx_transfer_destination",
                   columnList = "destination_warehouse_id"),
            @Index(name = "idx_transfer_requested_date",
                   columnList = "requested_date")
        }
)
public class InventoryTransfer extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransferStatus status = TransferStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "dispatched_by")
    private Long dispatchedBy;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
