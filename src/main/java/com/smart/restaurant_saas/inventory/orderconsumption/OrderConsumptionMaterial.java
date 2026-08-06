package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.common.BaseEntity;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per (doc, material) — the grain consumption executes at, after the D29 aggregation folds
 * every line's recipe into materials. This is where the consumed/not-consumed outcome is recorded;
 * the line carries no flag, because a single line requires several materials and cannot represent
 * a run in which some of them consumed and others did not.
 *
 * <p><b>UOM layers (D87).</b> {@code requiredQuantity} and {@code availableQuantity} are in
 * {@code requiredUom} — the material's display UOM, layer 2, the same layer as
 * {@code stock_balance.quantity} and {@code stock_batch.quantity}. They are directly comparable to
 * a balance and directly subtractable from one, which is what availability does.
 * {@code enteredQuantity} is in {@code enteredUom} — the recipe item's own UOM, the raw entered
 * layer {@code InventoryLedgerService.record()} converts to stock UOM (D3). It is persisted so
 * recalculate can rebuild the ledger command without re-running the aggregation, and so the ledger
 * still sees exactly one conversion boundary.
 */
@Getter
@Setter
@Entity
@Table(
    name = "order_consumption_material",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_order_consumption_material_doc_material",
        columnNames = {"doc_id", "material_id"})
)
public class OrderConsumptionMaterial extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OrderConsumption doc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    /** Display UOM (D87 layer 2). */
    @Column(name = "required_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal requiredQuantity;

    /** The unit {@link #requiredQuantity} and {@link #availableQuantity} are expressed in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "required_uom_id", nullable = false)
    private Uom requiredUom;

    /** Recipe-item UOM — the ledger's entered layer, converted to stock UOM on record (D3). */
    @Column(name = "entered_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal enteredQuantity;

    /** The unit {@link #enteredQuantity} is expressed in. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_uom_id", nullable = false)
    private Uom enteredUom;

    @Column(name = "is_consumed", nullable = false)
    private boolean consumed = false;

    /** Display UOM, same unit as {@link #requiredQuantity}. Set only on an INSUFFICIENT_STOCK row. */
    @Column(name = "available_quantity", precision = 18, scale = 6)
    private BigDecimal availableQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 20)
    private OrderConsumptionFailureReason failureReason;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "exception_message", columnDefinition = "text")
    private String exceptionMessage;

    /** Records a successful consumption, clearing any outcome left by an earlier attempt. */
    public void markConsumed() {
        this.consumed = true;
        this.availableQuantity = null;
        this.failureReason = null;
        this.exceptionClass = null;
        this.exceptionMessage = null;
    }

    /** Records that open batches could not cover {@link #requiredQuantity} (D94). */
    public void markInsufficient(BigDecimal availableQuantity) {
        this.consumed = false;
        this.availableQuantity = availableQuantity;
        this.failureReason = OrderConsumptionFailureReason.INSUFFICIENT_STOCK;
        this.exceptionClass = null;
        this.exceptionMessage = null;
    }

    /** Records that the material's consumption transaction threw (D30). */
    public void markTechnicalFailure(Exception ex) {
        this.consumed = false;
        this.availableQuantity = null;
        this.failureReason = OrderConsumptionFailureReason.TECHNICAL_FAILURE;
        this.exceptionClass = ex.getClass().getName();
        this.exceptionMessage = ex.getMessage();
    }
}
