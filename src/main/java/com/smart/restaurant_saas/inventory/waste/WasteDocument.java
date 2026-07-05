package com.smart.restaurant_saas.inventory.waste;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

/**
 * A stock write-off document. Records inventory removed from a warehouse because it was
 * spoiled, expired, damaged, etc. Follows the standard inventory document lifecycle
 * DRAFT → COMPLETE → POSTED → CANCELLED.
 *
 * On POST the document issues a WASTE / direction OUT ledger transaction per line. Those
 * transactions FIFO-deplete the material's stock batches and the ledger computes the actual
 * cost of issue — the waste document never supplies a cost. The {@link WasteReasonCode} is
 * descriptive metadata only; every line posts as a WASTE transaction regardless of reason.
 */
@Getter
@Setter
@Entity
@Table(
        name = "waste_document",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_waste_document_tenant_code",
                columnNames = {"tenant_id", "code"}
        )
)
public class WasteDocument extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "waste_date", nullable = false)
    private LocalDate wasteDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 50)
    private WasteReasonCode reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "posted_to_inventory", nullable = false)
    private Boolean postedToInventory = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "uncompleted_at")
    private LocalDateTime unCompletedAt;

    @Column(name = "uncompleted_by")
    private Long unCompletedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @OneToMany(mappedBy = "wasteDocument",
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WasteLine> lines = new ArrayList<>();

    /**
     * Advisory shortfalls computed once at COMPLETE time, stored as JSON.
     * Null / empty when the document has no shortfalls or is not yet completed.
     * Travels with the waste_document row — zero extra queries on read.
     *
     * First (and intentionally only) JSON column in the schema; see V9 migration comment.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stock_warnings", columnDefinition = "jsonb")
    private List<MaterialShortfall> stockWarnings = new ArrayList<>();
}
