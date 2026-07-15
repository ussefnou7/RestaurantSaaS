package com.smart.restaurant_saas.rbac.controller;

import com.smart.restaurant_saas.rbac.dto.response.RoleResponse;
import com.smart.restaurant_saas.rbac.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rbac")
public class RbacRoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        return roleService.listActiveRoles();
    }
}
