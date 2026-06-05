package com.smart.restaurant_saas.inventory.controller;

import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialCatalogRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreateUomRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialCatalogRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateUomRequest;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.dto.response.InventorySeedSummaryResponse;
import com.smart.restaurant_saas.inventory.dto.response.UomResponse;
import com.smart.restaurant_saas.inventory.service.InventorySeedService;
import com.smart.restaurant_saas.inventory.service.MaterialCatalogService;
import com.smart.restaurant_saas.inventory.service.MaterialCategoryService;
import com.smart.restaurant_saas.inventory.service.UomService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inventory")
@PreAuthorize("@securityService.isSysAdmin() and @securityService.hasPermission('SYSTEM_INVENTORY_CATALOG_MANAGE')")
public class AdminInventoryCatalogController {

    private final UomService uomService;
    private final MaterialCategoryService categoryService;
    private final MaterialCatalogService materialService;
    private final InventorySeedService inventorySeedService;

    @PostMapping("/seed-global-catalog")
    public InventorySeedSummaryResponse seedGlobalCatalog() {
        return inventorySeedService.seedGlobalCatalog();
    }

    @PostMapping("/seed-demo-tenant-data/{tenantId}")
    public InventorySeedSummaryResponse seedDemoTenantData(@PathVariable Long tenantId) {
        return inventorySeedService.seedDemoTenantData(tenantId);
    }

    @GetMapping("/uoms")
    public List<UomResponse> listUoms(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active
    ) {
        return uomService.listUoms(type, active);
    }

    @GetMapping("/uoms/{id}")
    public UomResponse getUom(@PathVariable Long id) {
        return uomService.getUom(id);
    }

    @PostMapping("/uoms")
    @ResponseStatus(HttpStatus.CREATED)
    public UomResponse createUom(@Valid @RequestBody CreateUomRequest request) {
        return uomService.createUom(request);
    }

    @PutMapping("/uoms/{id}")
    public UomResponse updateUom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUomRequest request
    ) {
        return uomService.updateUom(id, request);
    }

    @PatchMapping("/uoms/{id}/activate")
    public UomResponse activateUom(@PathVariable Long id) {
        return uomService.activateUom(id);
    }

    @PatchMapping("/uoms/{id}/deactivate")
    public UomResponse deactivateUom(@PathVariable Long id) {
        return uomService.deactivateUom(id);
    }

    @GetMapping("/global-material-categories")
    public List<MaterialCategoryResponse> listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return categoryService.listCategories(search, active);
    }

    @GetMapping("/global-material-categories/{id}")
    public MaterialCategoryResponse getCategory(@PathVariable Long id) {
        return categoryService.getCategory(id);
    }

    @PostMapping("/global-material-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialCategoryResponse createCategory(@Valid @RequestBody CreateMaterialCategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PutMapping("/global-material-categories/{id}")
    public MaterialCategoryResponse updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialCategoryRequest request
    ) {
        return categoryService.updateCategory(id, request);
    }

    @PatchMapping("/global-material-categories/{id}/activate")
    public MaterialCategoryResponse activateCategory(@PathVariable Long id) {
        return categoryService.activateCategory(id);
    }

    @PatchMapping("/global-material-categories/{id}/deactivate")
    public MaterialCategoryResponse deactivateCategory(@PathVariable Long id) {
        return categoryService.deactivateCategory(id);
    }

    @GetMapping("/global-materials")
    public List<MaterialCatalogResponse> listMaterials(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long uomId,
            @RequestParam(required = false) Long stockUomId,
            @RequestParam(required = false) Long displayUomId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return materialService.listMaterials(categoryId, stockUomId == null ? uomId : stockUomId, displayUomId, search, active);
    }

    @GetMapping("/global-materials/{id}")
    public MaterialCatalogResponse getMaterial(@PathVariable Long id) {
        return materialService.getMaterial(id);
    }

    @PostMapping("/global-materials")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialCatalogResponse createMaterial(@Valid @RequestBody CreateMaterialCatalogRequest request) {
        return materialService.createMaterial(request);
    }

    @PutMapping("/global-materials/{id}")
    public MaterialCatalogResponse updateMaterial(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialCatalogRequest request
    ) {
        return materialService.updateMaterial(id, request);
    }

    @PatchMapping("/global-materials/{id}/activate")
    public MaterialCatalogResponse activateMaterial(@PathVariable Long id) {
        return materialService.activateMaterial(id);
    }

    @PatchMapping("/global-materials/{id}/deactivate")
    public MaterialCatalogResponse deactivateMaterial(@PathVariable Long id) {
        return materialService.deactivateMaterial(id);
    }
}
