package com.smart.restaurant_saas.rbac.controller;

import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.UserPermissionsResponse;
import com.smart.restaurant_saas.rbac.service.PermissionService;
import com.smart.restaurant_saas.rbac.service.UserPermissionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PermissionController {

    private final PermissionService permissionService;
    private final UserPermissionService userPermissionService;

    @GetMapping("/permissions")
    @PreAuthorize("@securityService.hasPermission('PERMISSIONS_VIEW')")
    public List<PermissionResponse> listPermissions() {
        return permissionService.listActivePermissions();
    }

    @GetMapping({"/permissions/users/{userId}", "/users/{userId}/permissions", "/rbac/users/{userId}/permissions"})
    @PreAuthorize("@securityService.hasPermission('PERMISSIONS_VIEW')")
    public UserPermissionsResponse getUserPermissions(@PathVariable Long userId) {
        return userPermissionService.getUserPermissions(userId);
    }

    @PutMapping("/permissions/users/{userId}")
    @PreAuthorize("@securityService.hasPermission('USER_PERMISSIONS_UPDATE')")
    public UserPermissionsResponse replaceUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody List<String> permissionCodes
    ) {
        return userPermissionService.replaceUserPermissions(userId, permissionCodes);
    }

    @PostMapping("/permissions/users/{userId}/permissions/reset-to-role-defaults")
    @PreAuthorize("@securityService.hasPermission('USER_PERMISSIONS_UPDATE')")
    public UserPermissionsResponse resetUserPermissionsToRoleDefaults(@PathVariable Long userId) {
        return userPermissionService.resetUserPermissionsToRoleDefaults(userId);
    }
}
