package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "RestaurantOrder")
@Table(name = "orders")
public class Order extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 30)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", nullable = false, length = 30)
    private OrderSource orderSource;

    @Column(name = "aggregator_name")
    private String aggregatorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_stage", length = 40)
    private CancellationStage cancellationStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "table_no")
    private String tableNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "external_order_reference")
    private String externalOrderReference;

    // Nullable link to the loyalty customer (Loyalty V1). Null for walk-ins with no capture,
    // or whenever customer resolution failed — a loyalty failure never blocks order creation.
    @Column(name = "customer_id")
    private Long customerId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();
}
