package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderLineType;
import com.smart.restaurant_saas.menu.recipe.Recipe;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
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

@Getter
@Setter
@Entity
@Table(name = "order_line")
public class OrderLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    /**
     * Whether this line was sold or binned (D20).
     *
     * <p>A {@code WASTE} line was cooked and then taken off the order. It still consumes stock —
     * the food was made — but through the waste document rather than the sale one, and its money
     * is the revenue that never arrived rather than revenue collected.
     *
     * <p><b>It keeps its menu price and stays out of every sales figure.</b> The two are not in
     * tension: the price is what the dish was worth at that moment, which is the only way to value
     * the loss once menu prices move, and it must never be added to what a customer paid. The
     * exclusions live in D129's reconciliation and in the sales-by-product report.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private OrderLineType lineType = OrderLineType.SALE;

    /** Why it was binned, in D20's vocabulary. Null on a sale line. */
    @Enumerated(EnumType.STRING)
    @Column(name = "waste_stage", length = 40)
    private CancellationStage wasteStage;
}
