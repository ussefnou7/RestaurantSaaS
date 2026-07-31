package com.smart.restaurant_saas.inventory.batch;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;
import com.smart.restaurant_saas.inventory.stock.StockBalance;

/**
 * An inbound lot of stock, created when goods enter a warehouse (purchase, opening
 * balance, transfer-in). Each batch belongs to a {@link StockBalance}, which is the single
 * source of truth for its warehouse + material; quantities and cost are stored in the
 * material's display UOM, matching the balance.
 *
 * FIFO consumption eats a balance's OPEN batches oldest-first by {@code movementDate}, using the
 * generated {@code id} as a deterministic tiebreaker: see {@code StockBatchService.consumeFifo},
 * which reduces {@code remainingQuantity} and flips {@code status} to CLOSED when a batch empties.
 */
@Getter
@Setter
@Entity
@Table(
        name = "stock_batch",
        indexes = {
            @Index(
                    name = "idx_stock_batch_open_fifo",
                    columnList = "stock_balance_id, status, movement_date, id"
            ),
            @Index(
                    name = "idx_stock_batch_balance_movement_date",
                    columnList = "stock_balance_id, movement_date, id"
            )
        }
)
public class StockBatch extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The balance this batch belongs to — owns its warehouse + material. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_balance_id", nullable = false)
    private StockBalance stockBalance;

    /** Quantity that entered, in the material's display UOM. */
    @Column(name = "original_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal originalQuantity;

    /** Remaining quantity, in the display UOM. Starts equal to originalQuantity. */
    @Column(name = "remaining_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal remainingQuantity;

    /** Batch cost per display UOM. Nullable when the source movement carried no cost. */
    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    /** Business event date, copied from the source transaction's movementDate. */
    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    /** The ledger transaction that created this batch. Mandatory. */
    @Column(name = "source_transaction_id", nullable = false)
    private Long sourceTransactionId;

    /** Set for purchases (referenceType PURCHASE_INVOICE); null otherwise. */
    @Column(name = "source_invoice_id")
    private Long sourceInvoiceId;

    /** Set when the originating invoice line is known; null otherwise. */
    @Column(name = "source_invoice_line_id")
    private Long sourceInvoiceLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StockBatchStatus status = StockBatchStatus.OPEN;
}
