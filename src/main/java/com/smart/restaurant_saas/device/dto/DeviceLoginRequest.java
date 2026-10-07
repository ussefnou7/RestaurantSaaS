package com.smart.restaurant_saas.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceLoginRequest {

    @JsonAlias("secretKey")
    @NotBlank(message = "pairingCode is required")
    private String pairingCode;
}
