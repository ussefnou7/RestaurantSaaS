package com.smart.restaurant_saas.inventory.stock;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Getter
@Setter
@Entity
@Table(
        name = "stock_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_balance_tenant_warehouse_material",
                columnNames = {"tenant_id", "warehouse_id", "material_id"}
        )
)
public class StockBalance extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "opening_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal openingQuantity = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "average_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "minimum_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal minimumQuantity = BigDecimal.ZERO;

    @Column(name = "maximum_quantity", precision = 18, scale = 6)
    private BigDecimal maximumQuantity; // nullable, no default

    @Column(name = "last_purchase_price", precision = 18, scale = 6)
    private BigDecimal lastPurchasePrice;

    @Column(name = "last_purchase_date")
    private LocalDateTime lastPurchaseDate;

    @Column(name = "last_count_date")
    private LocalDateTime lastCountDate;

    @Column(name = "last_count_quantity", precision = 18, scale = 6)
    private BigDecimal lastCountQuantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
