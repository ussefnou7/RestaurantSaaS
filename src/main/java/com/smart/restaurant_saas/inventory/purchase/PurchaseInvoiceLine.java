package com.smart.restaurant_saas.inventory.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Getter
@Setter
@Entity
@Table(name = "purchase_invoice_line")
public class PurchaseInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 6)
    private BigDecimal lineTotal;

    @Column(name = "discount_percent", precision = 10, scale = 4)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 18, scale = 6)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_net_total", precision = 18, scale = 6)
    private BigDecimal lineNetTotal = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
