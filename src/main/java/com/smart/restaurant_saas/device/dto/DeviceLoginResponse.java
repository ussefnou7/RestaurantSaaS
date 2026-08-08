package com.smart.restaurant_saas.device.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceLoginResponse {

    private final Long id;
    private final Long branchId;
    private final String branchName;
    private final Long tenantId;
    private final String tenantCode;

    /**
     * Resolved IANA zone for this device's branch (D101) — the branch override when set, else the
     * tenant's. The device caches it and stamps nothing from its own clock: a tablet's clock is not
     * authoritative and is routinely wrong.
     */
    private final String timezone;
}
