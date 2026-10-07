package com.smart.restaurant_saas.device.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DevicePairingCodeResponse {

    private final Long deviceId;
    private final String pairingCode;
    private final LocalDateTime expiresAt;
}
