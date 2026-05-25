package com.smart.restaurant_saas.user.controller;

import com.smart.restaurant_saas.user.dto.request.CreateUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateUserRequest;
import com.smart.restaurant_saas.user.dto.request.UpdateUserStatusRequest;
import com.smart.restaurant_saas.user.dto.response.UserResponse;
import com.smart.restaurant_saas.user.service.TenantUserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/users")
public class TenantUserController {

    private final TenantUserService tenantUserService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_VIEW')")
    public List<UserResponse> listUsers() {
        return tenantUserService.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_CREATE')")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return tenantUserService.createUser(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_VIEW')")
    public UserResponse getUser(@PathVariable Long id) {
        return tenantUserService.getUser(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_UPDATE')")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return tenantUserService.updateUser(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_CHANGE_STATUS')")
    public UserResponse updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return tenantUserService.updateUserStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('USERS_DELETE')")
    public void deleteUser(@PathVariable Long id) {
        tenantUserService.deleteUser(id);
    }
}
