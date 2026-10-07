package com.smart.restaurant_saas.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.AuthenticationException;
import org.junit.jupiter.api.Test;

class DevicePairingThrottleServiceTest {

    @Test
    void fifthFailureLocksClientForOneMinute() {
        DevicePairingThrottleService service = new DevicePairingThrottleService();

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatCode(() -> service.recordFailure("client-1")).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> service.recordFailure("client-1"))
            .isInstanceOfSatisfying(AuthenticationException.class, ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_PAIRING_TEMPORARILY_LOCKED);
                assertThat(ex.getParams()).containsKey("retryAfterSeconds");
            });
        assertThatThrownBy(() -> service.checkAllowed("client-1"))
            .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void successfulPairingClearsFailureWindow() {
        DevicePairingThrottleService service = new DevicePairingThrottleService();
        for (int attempt = 1; attempt < 5; attempt++) {
            service.recordFailure("client-1");
        }

        service.clear("client-1");

        assertThatCode(() -> service.checkAllowed("client-1")).doesNotThrowAnyException();
        assertThatCode(() -> service.recordFailure("client-1")).doesNotThrowAnyException();
    }
}
