package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
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
    private final PaymentMethod paymentMethod;
    private final String tableNo;
    private final Long branchId;
    private final String branchName;
    private final Long warehouseId;
    private final String warehouseName;
    private final BigDecimal totalAmount;
    private final LocalDateTime orderDate;
    private final String externalOrderReference;
    private final Long customerId;
    private final List<OrderLineResponse> lines;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
