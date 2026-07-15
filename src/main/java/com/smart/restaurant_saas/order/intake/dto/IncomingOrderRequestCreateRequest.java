package com.smart.restaurant_saas.order.intake.dto;

import com.smart.restaurant_saas.order.intake.IncomingOrderSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomingOrderRequestCreateRequest {

    @NotNull(message = "source is required")
    private IncomingOrderSource source;

    private String aggregatorName;

    private String externalReferenceId;

    private Long branchId;

    @NotBlank(message = "payload is required")
    private String payload;
}
