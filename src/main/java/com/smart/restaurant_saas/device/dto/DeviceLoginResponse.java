package com.smart.restaurant_saas.device.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceLoginResponse {

    private final Long branchId;
    private final String branchName;
    private final Long tenantId;
}
