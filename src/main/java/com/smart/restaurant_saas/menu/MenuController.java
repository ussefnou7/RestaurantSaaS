package com.smart.restaurant_saas.menu;

import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@Tag(name = "Menu", description = "Read-only menu projection for ordering surfaces")
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(
        summary = "Get the orderable menu",
        description = "Returns menu-visible roots with variants, derived prices, and add-ons nested."
    )
    public List<MenuItemResponse> getMenu(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return menuService.findMenu(tenantId);
    }
}
