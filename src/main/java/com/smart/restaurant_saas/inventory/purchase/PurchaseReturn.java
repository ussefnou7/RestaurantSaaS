package com.smart.restaurant_saas.inventory.purchase;

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
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.PurchaseReturnReason;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "purchase_return",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_return_tenant_number",
                columnNames = {"tenant_id", "return_number"}
        )
)
public class PurchaseReturn extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private PurchaseInvoice originalInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "return_number", length = 100)
    private String returnNumber;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private PurchaseReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 6)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "posted_to_inventory", nullable = false)
    private Boolean postedToInventory = false;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "unposted_at")
    private LocalDateTime unpostedAt;

    @Column(name = "unposted_by")
    private Long unpostedBy;

    @Column(name = "uncompleted_at")
    private LocalDateTime unCompletedAt;

    @Column(name = "uncompleted_by")
    private Long unCompletedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @OneToMany(mappedBy = "purchaseReturn",
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnLine> lines = new ArrayList<>();
}
