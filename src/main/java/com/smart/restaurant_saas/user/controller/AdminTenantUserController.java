package com.smart.restaurant_saas.user.controller;

import com.smart.restaurant_saas.user.dto.request.CreateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateTenantUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.TenantUserResponse;
import com.smart.restaurant_saas.user.service.AdminTenantUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tenants/{tenantId}/users")
public class AdminTenantUserController {

    private final AdminTenantUserService adminTenantUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantUserResponse createUser(
            @PathVariable Long tenantId,
            @Valid @RequestBody CreateTenantUserRequest request
    ) {
        return adminTenantUserService.createUser(tenantId, request);
    }

    @GetMapping
    public List<TenantUserResponse> listUsers(@PathVariable Long tenantId) {
        return adminTenantUserService.listUsers(tenantId);
    }

    @GetMapping("/{userId}")
    public TenantUserResponse getUser(
            @PathVariable Long tenantId,
            @PathVariable Long userId
    ) {
        return adminTenantUserService.getUser(tenantId, userId);
    }

    @PutMapping("/{userId}")
    public TenantUserResponse updateUser(
            @PathVariable Long tenantId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateTenantUserRequest request
    ) {
        return adminTenantUserService.updateUser(tenantId, userId, request);
    }

    @PatchMapping("/{userId}/status")
    public TenantUserResponse updateUserStatus(
            @PathVariable Long tenantId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateTenantUserStatusRequest request
    ) {
        return adminTenantUserService.updateUserStatus(tenantId, userId, request);
    }
}
