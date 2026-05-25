package com.smart.restaurant_saas.auth.controller;

import com.smart.restaurant_saas.auth.dto.request.LoginRequest;
import com.smart.restaurant_saas.auth.dto.response.AuthUserResponse;
import com.smart.restaurant_saas.auth.dto.response.LoginResponse;
import com.smart.restaurant_saas.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.me();
    }
}
