package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A maintenance cost record (D49). Pure insert — it never touches {@code quantity} or
 * {@code remainingQuantity} on the line. Both {@code assetId} and {@code assetLineId} are carried
 * and validated for consistency (D51).
 */
@Getter
@Setter
@Entity
@Table(name = "asset_maintenance")
public class AssetMaintenance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_line_id", nullable = false)
    private Long assetLineId;

    @Column(name = "cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal cost;

    @Column(name = "maintenance_date", nullable = false)
    private LocalDate maintenanceDate;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "vendor")
    private String vendor;
}
