package com.smart.restaurant_saas.device.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceResponse {

    private final Long id;
    private final String name;
    private final Long branchId;
    private final String branchName;
    private final Boolean active;
    private final LocalDateTime lastLoginAt;
    private final String secretKey;
}
