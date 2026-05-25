package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateLeaveRequestStatusRequest(
        @NotBlank String status,
        String statusNote
) {
}
