package com.smart.restaurant_saas.order.intake.dto;

import com.smart.restaurant_saas.order.intake.IncomingOrderRequestStatus;
import com.smart.restaurant_saas.order.intake.IncomingOrderSource;
import java.time.LocalDateTime;

public record IncomingOrderRequestFilters(
    IncomingOrderSource source,
    IncomingOrderRequestStatus status,
    Long branchId,
    LocalDateTime fromDate,
    LocalDateTime toDate
) {}
