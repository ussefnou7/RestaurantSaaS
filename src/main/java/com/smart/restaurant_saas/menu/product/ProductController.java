package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.menu.product.dto.ProductRequest;
import com.smart.restaurant_saas.menu.product.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu/products")
@RequiredArgsConstructor
@Tag(name = "Menu - Products", description = "Tenant product management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(
        summary = "List products",
        description = "Optionally filters by menu category or products eligible to become a parent."
    )
    public List<ProductResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long menuCategoryId,
            @RequestParam(defaultValue = "false") boolean parentEligible,
            @RequestParam(required = false) Long excludeProductId) {
        return productService.findAll(tenantId, menuCategoryId, parentEligible, excludeProductId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get a product")
    public ProductResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return productService.findById(id, tenantId);
    }

    @GetMapping("/{id}/variants")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "List variant children of a parent product")
    public List<ProductResponse> listVariants(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return productService.findVariants(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_CREATE')")
    @Operation(
        summary = "Create a product",
        description = "Creates an active product in an active menu category."
    )
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(
        summary = "Update a product",
        description = "Updates product details without changing its active state."
    )
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return productService.update(id, request, tenantId, userId);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_CHANGE_STATUS')")
    @Operation(summary = "Toggle product active state")
    public ProductResponse toggleActive(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return productService.toggleActive(id, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(summary = "Delete a product")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        productService.deleteProduct(tenantId, id);
    }
}
