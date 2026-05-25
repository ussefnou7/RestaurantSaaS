package com.smart.restaurant_saas.rbac.controller;

import com.smart.restaurant_saas.rbac.dto.request.ReplaceUserPermissionsRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@PreAuthorize("@securityService.isSysAdmin()")
public class PermissionController {

    private final PermissionService permissionService;
    private final UserPermissionService userPermissionService;

    @GetMapping("/permissions")
    public List<PermissionResponse> listPermissions() {
        return permissionService.listActivePermissions();
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}/permissions")
    public UserPermissionsResponse getUserPermissions(
            @PathVariable Long tenantId,
            @PathVariable Long userId
    ) {
        return userPermissionService.getUserPermissions(tenantId, userId);
    }

    @PutMapping("/tenants/{tenantId}/users/{userId}/permissions")
    public UserPermissionsResponse replaceUserPermissions(
            @PathVariable Long tenantId,
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceUserPermissionsRequest request
    ) {
        return userPermissionService.replaceUserPermissions(tenantId, userId, request);
    }
}
