package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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

    // D132: total kitchen time across every ticket on this order, in seconds,
    // already summed by the POS — a dine-in table pays once for several
    // tickets, so there is no single send/ready pair the server could total
    // itself. Optional and often absent: a takeaway paid without reaching the
    // kitchen has none. Never defaulted to zero, which reads as an instant
    // kitchen rather than as "not measured".
    @Min(value = 0, message = "kitchenTimeSeconds must not be negative")
    private Integer kitchenTimeSeconds;

    // When the order began — the first ticket's send to the kitchen. Paired
    // with orderDate it gives table occupancy, which is a different figure.
    private LocalDateTime orderStartedAt;

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

    // D129: the money the POS printed, charged and collected. The server stores these
    // three verbatim and derives none of them — the sale is already paid by the time
    // this arrives, possibly hours earlier and offline, so the figure on the customer's
    // receipt is the only true one. The tax rate lives in the POS alone.
    //
    // Satisfying subtotal + taxAmount = totalAmount is the POS's job. The server checks
    // it only to log a divergence — never to reject a paid sale, and never to correct it.

    @NotNull(message = "subtotal is required")
    @DecimalMin(value = "0.00", message = "subtotal must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "subtotal must have at most 2 decimal places")
    private BigDecimal subtotal;

    @NotNull(message = "taxAmount is required")
    @DecimalMin(value = "0.00", message = "taxAmount must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "taxAmount must have at most 2 decimal places")
    private BigDecimal taxAmount;

    @NotNull(message = "totalAmount is required")
    @DecimalMin(value = "0.00", message = "totalAmount must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "totalAmount must have at most 2 decimal places")
    private BigDecimal totalAmount;

    /** Cash physically handed over; null for card payments and legacy orders. */
    @DecimalMin(value = "0.00", message = "cashReceived must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "cashReceived must have at most 2 decimal places")
    private BigDecimal cashReceived;
}
