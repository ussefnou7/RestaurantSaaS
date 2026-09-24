package com.smart.restaurant_saas.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.auth.AuthErrorCode;
import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.common.AppException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserServiceTest {

    private final CurrentUserService currentUserService = new CurrentUserService(null);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDeviceRequiringPathRejectsATokenWithoutTheDeviceClaim() {
        authenticate(new CurrentUserPrincipal(10L, 20L, "manager", "OWNER", null));

        assertThatThrownBy(currentUserService::requireCurrentDeviceId)
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    // 403, not 401: the session is valid and a browser having no device is
                    // normal. At 401 admin-web signed the user out for asking.
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.DEVICE_IDENTITY_REQUIRED);
                    assertThat(ex.getParams()).containsEntry("claim", "deviceId");
                });
    }

    @Test
    void aDeviceRequiringPathUsesOnlyTheSignedPrincipalClaim() {
        authenticate(new CurrentUserPrincipal(10L, 20L, "cashier", "CASHIER", 30L));

        assertThat(currentUserService.requireCurrentDeviceId()).isEqualTo(30L);
    }

    private void authenticate(CurrentUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
