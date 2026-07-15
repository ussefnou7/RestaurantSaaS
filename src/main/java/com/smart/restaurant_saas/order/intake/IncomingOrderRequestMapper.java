package com.smart.restaurant_saas.order.intake;

import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import com.smart.restaurant_saas.order.intake.dto.IncomingOrderRequestResponse;
import org.springframework.stereotype.Component;

@Component
public class IncomingOrderRequestMapper {

    public IncomingOrderRequestResponse toResponse(IncomingOrderRequest request,
                                                   OrderSummaryResponse completedOrder) {
        return IncomingOrderRequestResponse.builder()
            .id(request.getId())
            .source(request.getSource())
            .aggregatorName(request.getAggregatorName())
            .externalReferenceId(request.getExternalReferenceId())
            .branchId(request.getBranchId())
            .payload(request.getPayload())
            .status(request.getStatus())
            .completedOrderId(request.getCompletedOrderId())
            .completedOrder(completedOrder)
            .sentToPosAt(request.getSentToPosAt())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }
}
