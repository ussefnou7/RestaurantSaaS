package com.smart.restaurant_saas.order.intake.dto;

import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.intake.IncomingOrderRequestStatus;
import com.smart.restaurant_saas.order.intake.IncomingOrderSource;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IncomingOrderRequestResponse {

    private final Long id;
    private final IncomingOrderSource source;
    private final String aggregatorName;
    private final String externalReferenceId;
    private final Long branchId;
    private final String payload;
    private final IncomingOrderRequestStatus status;
    private final Long completedOrderId;
    private final OrderSummaryResponse completedOrder;
    private final LocalDateTime sentToPosAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
