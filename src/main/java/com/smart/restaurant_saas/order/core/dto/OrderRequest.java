package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.core.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    private String tableNo;

    @NotNull(message = "orderDate is required")
    private LocalDateTime orderDate;

    private String externalOrderReference;

    // Optional loyalty customer capture. When customerPhone is present, the order is linked to the
    // resolved (or newly created) customer; absent means a walk-in with no capture (a normal case).
    private String customerPhone;

    private String customerName;

    @Valid
    @NotEmpty(message = "lines are required")
    private List<OrderLineRequest> lines;
}
