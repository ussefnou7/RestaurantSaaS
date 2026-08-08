package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderRequest {

    @NotNull(message = "orderType is required")
    private OrderType orderType;

    @NotNull(message = "orderSource is required")
    private OrderSource orderSource;

    private String aggregatorName;

    @NotNull(message = "status is required")
    private OrderStatus status;

    private CancellationStage cancellationStage;

    private OrderCancellationReason cancellationReason;

    @Size(max = 500, message = "cancellationReasonNote must be at most 500 characters")
    private String cancellationReasonNote;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    // Dine-in table (D76). Optional; only allowed for DINE_IN orders, and must
    // belong to the order's branch. Replaces the old free-text tableNo (D26).
    private Long tableId;

    @NotNull(message = "orderDate is required")
    private LocalDateTime orderDate;

    private String externalOrderReference;

    // Client-generated once, resent unchanged on every retry (O16). Optional
    // for backward compatibility with any caller that predates this field,
    // but the POS client always sends one.
    private String idempotencyKey;

    // POS display number (e.g. "POS-1036"). Optional, not unique. Distinct from
    // idempotencyKey — this is for display/lookup, not deduplication.
    private String orderNo;

    // Optional loyalty customer phone captured by the POS. Absent means walk-in.
    private String customerPhone;

    // Present only for first-time phone capture; ignored when the phone already exists.
    private String customerName;

    @Valid
    @NotEmpty(message = "lines are required")
    private List<OrderLineRequest> lines;
}
