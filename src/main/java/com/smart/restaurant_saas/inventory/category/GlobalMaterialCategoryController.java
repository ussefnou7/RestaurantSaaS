package com.smart.restaurant_saas.inventory.category;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.category.dto.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.service.setup.MaterialCategoryService;

@RestController
@RequestMapping("/api/inventory/global-material-categories")
@RequiredArgsConstructor
@Tag(name = "Inventory Setup - Catalog", description = "Global material catalog browsing and import")
public class GlobalMaterialCategoryController {

    private final MaterialCategoryService categoryService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_VIEW')")
    @Operation(
        summary = "List global material categories",
        description = "Returns system-level material categories for use in the "
                    + "catalog import modal filter dropdown. These are separate from "
                    + "tenant categories returned by /api/inventory/material-categories."
    )
    public List<MaterialCategoryResponse> list(
            @RequestParam(required = false, defaultValue = "true") Boolean active) {
        return categoryService.findAllGlobal(active);
    }
}
