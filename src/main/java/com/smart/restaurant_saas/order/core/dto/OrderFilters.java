package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import java.time.LocalDateTime;

public record OrderFilters(
    OrderType orderType,
    OrderSource orderSource,
    OrderStatus status,
    Long branchId,
    LocalDateTime fromDate,
    LocalDateTime toDate,
    String orderNo,
    Long createdBy,
    Long customerId
) {}
