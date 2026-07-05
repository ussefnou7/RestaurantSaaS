package com.smart.restaurant_saas.inventory.material;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.material.dto.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.service.setup.MaterialCatalogService;

@RestController
@RequestMapping("/api/inventory/global-materials")
@RequiredArgsConstructor
@Tag(name = "Inventory Setup - Catalog", description = "Global material catalog browsing and import")
public class MaterialCatalogController {

    private final MaterialCatalogService catalogService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_SETUP_VIEW')")
    @Operation(
        summary = "Browse material catalog",
        description = "Returns the global material catalog managed by SysAdmin. "
                    + "Each item includes alreadyImported flag indicating whether "
                    + "the current tenant has already imported it. "
                    + "Used by the catalog import modal on the materials screen."
    )
    public List<MaterialCatalogResponse> browse(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long uomId) {
        return catalogService.findAll(tenantId, search, categoryId, uomId);
    }
}
