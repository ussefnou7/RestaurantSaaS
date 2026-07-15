package com.smart.restaurant_saas.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceLoginRequest {

    @NotBlank(message = "secretKey is required")
    private String secretKey;
}
