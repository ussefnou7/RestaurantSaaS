package com.smart.restaurant_saas.assets.disposal;

import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A disposal event that reduces {@code AssetLine.remainingQuantity} (D48). Append-only record;
 * {@code createdBy}/{@code createdAt} come from the audit base. Both {@code assetId} and
 * {@code assetLineId} are carried and validated for consistency (D51).
 */
@Getter
@Setter
@Entity
@Table(name = "asset_disposal")
public class AssetDisposal extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_line_id", nullable = false)
    private Long assetLineId;

    @Column(name = "quantity_disposed", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantityDisposed;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private AssetDisposalReason reason;

    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @Column(name = "notes", length = 1000)
    private String notes;
}
