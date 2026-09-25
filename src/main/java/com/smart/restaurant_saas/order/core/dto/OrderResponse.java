package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderCancellationReason;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderResponse {

    private final Long id;
    private final OrderType orderType;
    private final OrderSource orderSource;
    private final String aggregatorName;
    private final OrderStatus status;
    private final CancellationStage cancellationStage;
    private final OrderCancellationReason cancellationReason;
    private final String cancellationReasonNote;
    private final PaymentMethod paymentMethod;
    private final Long tableId;
    private final String tableName;
    private final Long branchId;
    private final String branchName;
    private final Long warehouseId;
    private final String warehouseName;
    private final BigDecimal subtotal;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final BigDecimal cashReceived;
    private final BigDecimal changeAmount;
    private final LocalDateTime orderDate;
    /**
     * Total kitchen time in seconds, summed across this order's tickets by the POS (D132); null
     * when nothing was measured. Minutes are the reader's to render, and
     * {@code orderDate - orderStartedAt} is table occupancy — a different figure, not this one.
     */
    private final Integer kitchenTimeSeconds;
    private final LocalDateTime orderStartedAt;
    private final String externalOrderReference;
    private final String orderNo;
    private final Long customerId;
    private final Long shiftId;
    private final List<OrderLineResponse> lines;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
