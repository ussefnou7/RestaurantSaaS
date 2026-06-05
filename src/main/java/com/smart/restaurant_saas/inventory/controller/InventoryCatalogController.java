package com.smart.restaurant_saas.inventory.controller;

import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreateMaterialRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreateSupplierRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreateWarehouseRequest;
import com.smart.restaurant_saas.inventory.dto.request.ImportMaterialsRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialCategoryRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateMaterialRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateSupplierRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdateWarehouseRequest;
import com.smart.restaurant_saas.inventory.dto.response.ImportMaterialsResponse;
import com.smart.restaurant_saas.inventory.dto.response.MaterialResponse;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCatalogResponse;
import com.smart.restaurant_saas.inventory.dto.response.MaterialCategoryResponse;
import com.smart.restaurant_saas.inventory.dto.response.SupplierResponse;
import com.smart.restaurant_saas.inventory.dto.response.UomResponse;
import com.smart.restaurant_saas.inventory.dto.response.WarehouseResponse;
import com.smart.restaurant_saas.inventory.service.MaterialCatalogService;
import com.smart.restaurant_saas.inventory.service.MaterialCategoryService;
import com.smart.restaurant_saas.inventory.service.MaterialService;
import com.smart.restaurant_saas.inventory.service.SupplierService;
import com.smart.restaurant_saas.inventory.service.UomService;
import com.smart.restaurant_saas.inventory.service.WarehouseService;
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
@RequestMapping("/api/inventory")
@PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_VIEW')")
public class InventoryCatalogController {

    private final UomService uomService;
    private final MaterialCategoryService categoryService;
    private final MaterialCatalogService materialService;
    private final MaterialService tenantMaterialService;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;

    @GetMapping("/uoms")
    public List<UomResponse> listUoms(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active
    ) {
        return uomService.listUoms(type, active);
    }

    @GetMapping("/global-material-categories")
    public List<MaterialCategoryResponse> listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return categoryService.listCategories(search, active);
    }

    @GetMapping("/material-categories")
    public List<MaterialCategoryResponse> listAccessibleCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return categoryService.listAccessibleCategories(search, active);
    }

    @GetMapping("/material-categories/{id}")
    public MaterialCategoryResponse getAccessibleCategory(@PathVariable Long id) {
        return categoryService.getAccessibleCategory(id);
    }

    @PostMapping("/material-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialCategoryResponse createTenantCategory(
            @Valid @RequestBody CreateMaterialCategoryRequest request
    ) {
        return categoryService.createTenantCategory(request);
    }

    @PutMapping("/material-categories/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialCategoryResponse updateTenantCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialCategoryRequest request
    ) {
        return categoryService.updateTenantCategory(id, request);
    }

    @PatchMapping("/material-categories/{id}/activate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialCategoryResponse activateTenantCategory(@PathVariable Long id) {
        return categoryService.activateTenantCategory(id);
    }

    @PatchMapping("/material-categories/{id}/deactivate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialCategoryResponse deactivateTenantCategory(@PathVariable Long id) {
        return categoryService.deactivateTenantCategory(id);
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

    @GetMapping("/materials")
    public List<MaterialResponse> listTenantMaterials(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long defaultUomId,
            @RequestParam(required = false) Long stockUomId,
            @RequestParam(required = false) Long displayUomId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long catalogId
    ) {
        return tenantMaterialService.listMaterials(
                search,
                categoryId,
                stockUomId == null ? defaultUomId : stockUomId,
                displayUomId,
                active,
                catalogId
        );
    }

    @GetMapping("/materials/{id}")
    public MaterialResponse getTenantMaterial(@PathVariable Long id) {
        return tenantMaterialService.getMaterial(id);
    }

    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialResponse createTenantMaterial(@Valid @RequestBody CreateMaterialRequest request) {
        return tenantMaterialService.createMaterial(request);
    }

    @PutMapping("/materials/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialResponse updateTenantMaterial(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialRequest request
    ) {
        return tenantMaterialService.updateMaterial(id, request);
    }

    @PatchMapping("/materials/{id}/activate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialResponse activateTenantMaterial(@PathVariable Long id) {
        return tenantMaterialService.activateMaterial(id);
    }

    @PatchMapping("/materials/{id}/deactivate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public MaterialResponse deactivateTenantMaterial(@PathVariable Long id) {
        return tenantMaterialService.deactivateMaterial(id);
    }

    @PostMapping("/materials/import")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public ImportMaterialsResponse importTenantMaterials(@Valid @RequestBody ImportMaterialsRequest request) {
        return tenantMaterialService.importMaterials(request);
    }

    @GetMapping("/warehouses")
    public List<WarehouseResponse> listWarehouses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean active
    ) {
        return warehouseService.listWarehouses(search, branchId, type, active);
    }

    @GetMapping("/warehouses/{id}")
    public WarehouseResponse getWarehouse(@PathVariable Long id) {
        return warehouseService.getWarehouse(id);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public WarehouseResponse createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.createWarehouse(request);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public WarehouseResponse updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return warehouseService.updateWarehouse(id, request);
    }

    @PatchMapping("/warehouses/{id}/activate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public WarehouseResponse activateWarehouse(@PathVariable Long id) {
        return warehouseService.activateWarehouse(id);
    }

    @PatchMapping("/warehouses/{id}/deactivate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public WarehouseResponse deactivateWarehouse(@PathVariable Long id) {
        return warehouseService.deactivateWarehouse(id);
    }

    @GetMapping("/suppliers")
    public List<SupplierResponse> listSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        return supplierService.listSuppliers(search, active);
    }

    @GetMapping("/suppliers/{id}")
    public SupplierResponse getSupplier(@PathVariable Long id) {
        return supplierService.getSupplier(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public SupplierResponse createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public SupplierResponse updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest request
    ) {
        return supplierService.updateSupplier(id, request);
    }

    @PatchMapping("/suppliers/{id}/activate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public SupplierResponse activateSupplier(@PathVariable Long id) {
        return supplierService.activateSupplier(id);
    }

    @PatchMapping("/suppliers/{id}/deactivate")
    @PreAuthorize("@securityService.hasPermission('INVENTORY_SETUP_MANAGE')")
    public SupplierResponse deactivateSupplier(@PathVariable Long id) {
        return supplierService.deactivateSupplier(id);
    }
}
