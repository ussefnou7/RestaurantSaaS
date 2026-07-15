package com.smart.restaurant_saas.rbac.controller;

import com.smart.restaurant_saas.rbac.dto.request.UpdateRolePermissionsRequest;
import com.smart.restaurant_saas.rbac.dto.response.PermissionResponse;
import com.smart.restaurant_saas.rbac.dto.response.RoleResponse;
import com.smart.restaurant_saas.rbac.service.RoleService;
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
@RequestMapping("/sys-admin/rbac")
@PreAuthorize("@securityService.isSysAdmin()")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        return roleService.listActiveRoles();
    }

    @GetMapping("/roles/{roleCode}/permissions")
    public List<PermissionResponse> getRolePermissions(@PathVariable String roleCode) {
        return roleService.getRolePermissions(roleCode);
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public List<PermissionResponse> updateRolePermissions(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        return roleService.updateRolePermissions(roleCode, request);
    }
}
