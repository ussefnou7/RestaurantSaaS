package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.table.RestaurantTable;
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
    @Column(name = "cancellation_reason", length = 30)
    private OrderCancellationReason cancellationReason;

    @Column(name = "cancellation_reason_note", length = 500)
    private String cancellationReasonNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    // Dine-in table link (D76, replaces the plain tableNo string of D26). Nullable —
    // only DINE_IN orders carry a table. FK is RESTRICT: a referenced table cannot be
    // deleted while any order points at it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 6)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "external_order_reference")
    private String externalOrderReference;

    // Client-generated, resent unchanged on every retry (O16) — lets a POS
    // client safely resend after a lost response without double-booking.
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    // Display number assigned by the POS (e.g. "POS-1036"). Optional, not unique —
    // distinct from idempotencyKey which is for deduplication (O16).
    @Column(name = "order_no", length = 50)
    private String orderNo;

    // Nullable link to the loyalty customer. Null for walk-ins or when loyalty resolution fails.
    @Column(name = "customer_id")
    private Long customerId;

    // Cashier shift this order belongs to. Resolved server-side from the authenticated cashier's
    // current OPEN shift — never accepted from the client request body (mirrors D41 warehouse pattern).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();
}
