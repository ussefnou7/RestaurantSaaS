package com.smart.restaurant_saas.assets.assetline;

import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
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
 * A single purchase batch of an {@link com.smart.restaurant_saas.assets.asset.Asset} (D46).
 * {@code label} is optional free text with no tie to {@code quantity}. Disposals decrement
 * {@code remainingQuantity} (D48); {@code status} is derived and recomputed after each mutation.
 */
@Getter
@Setter
@Entity
@Table(name = "asset_line")
public class AssetLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "label")
    private String label;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "remaining_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal remainingQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalCost;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssetLineStatus status = AssetLineStatus.ACTIVE;
}
