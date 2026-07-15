package com.smart.restaurant_saas.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "branchId is required")
    private Long branchId;
}
