package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderSummaryResponse {

    private final Long id;
    private final OrderType orderType;
    private final OrderSource orderSource;
    private final OrderStatus status;
    private final Long branchId;
    private final Long warehouseId;
    private final BigDecimal totalAmount;
    private final LocalDateTime orderDate;
    private final String externalOrderReference;
    private final PaymentMethod paymentMethod;
}
