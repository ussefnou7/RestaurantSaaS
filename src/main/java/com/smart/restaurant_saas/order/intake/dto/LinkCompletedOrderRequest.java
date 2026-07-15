package com.smart.restaurant_saas.order.intake.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkCompletedOrderRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;
}
