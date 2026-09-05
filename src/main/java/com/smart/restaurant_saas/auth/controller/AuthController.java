package com.smart.restaurant_saas.auth.controller;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import com.smart.restaurant_saas.auth.dto.request.RefreshTokenRequest;
import com.smart.restaurant_saas.auth.dto.response.AuthUserResponse;
import com.smart.restaurant_saas.auth.dto.response.LoginResponse;
import com.smart.restaurant_saas.auth.dto.response.TokenRefreshResponse;
import com.smart.restaurant_saas.auth.refresh.RefreshTokenService;
import com.smart.restaurant_saas.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.rotate(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me();
    }
}
