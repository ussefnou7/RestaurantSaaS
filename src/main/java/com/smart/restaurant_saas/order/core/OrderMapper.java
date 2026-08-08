package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.core.dto.OrderLineResponse;
import com.smart.restaurant_saas.order.core.dto.OrderResponse;
import com.smart.restaurant_saas.order.core.dto.OrderSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .orderType(order.getOrderType())
            .orderSource(order.getOrderSource())
            .aggregatorName(order.getAggregatorName())
            .status(order.getStatus())
            .cancellationStage(order.getCancellationStage())
            .cancellationReason(order.getCancellationReason())
            .cancellationReasonNote(order.getCancellationReasonNote())
            .paymentMethod(order.getPaymentMethod())
            .tableId(order.getTable() != null ? order.getTable().getId() : null)
            .tableName(order.getTable() != null ? order.getTable().getName() : null)
            .branchId(order.getBranch().getId())
            .branchName(order.getBranch().getName())
            .warehouseId(order.getWarehouse().getId())
            .warehouseName(order.getWarehouse().getName())
            .subtotal(order.getSubtotal())
            .taxAmount(order.getTaxAmount())
            .totalAmount(order.getTotalAmount())
            .orderDate(order.getOrderDate())
            .externalOrderReference(order.getExternalOrderReference())
            .orderNo(order.getOrderNo())
            .customerId(order.getCustomerId())
            .shiftId(order.getShift() != null ? order.getShift().getId() : null)
            .lines(mapLines(order.getLines()))
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    public OrderSummaryResponse toSummary(Order order) {
        return OrderSummaryResponse.builder()
            .id(order.getId())
            .orderType(order.getOrderType())
            .orderSource(order.getOrderSource())
            .status(order.getStatus())
            .paymentMethod(order.getPaymentMethod())
            .branchId(order.getBranch().getId())
            .warehouseId(order.getWarehouse().getId())
            .subtotal(order.getSubtotal())
            .taxAmount(order.getTaxAmount())
            .totalAmount(order.getTotalAmount())
            .orderDate(order.getOrderDate())
            .externalOrderReference(order.getExternalOrderReference())
            .shiftId(order.getShift() != null ? order.getShift().getId() : null)
            .orderNo(order.getOrderNo())
            .createdBy(order.getCreatedBy())
            .lines(mapLines(order.getLines()))
            .build();
    }

    private List<OrderLineResponse> mapLines(List<OrderLine> lines) {
        return lines.stream().map(this::toLineResponse).toList();
    }

    private OrderLineResponse toLineResponse(OrderLine line) {
        return OrderLineResponse.builder()
            .id(line.getId())
            .productId(line.getProduct().getId())
            .productName(line.getProduct().getName())
            .recipeId(line.getRecipe().getId())
            .quantity(line.getQuantity())
            .unitPrice(line.getUnitPrice())
            .lineTotal(line.getLineTotal())
            .createdAt(line.getCreatedAt())
            .updatedAt(line.getUpdatedAt())
            .build();
    }
}
