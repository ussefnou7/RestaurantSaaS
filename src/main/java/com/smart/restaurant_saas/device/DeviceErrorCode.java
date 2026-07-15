package com.smart.restaurant_saas.device;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeviceErrorCode implements ErrorCode {

    INVALID_DEVICE_SECRET(HttpStatus.UNAUTHORIZED),
    DEVICE_INACTIVE(HttpStatus.FORBIDDEN),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
