package com.smart.restaurant_saas.menu.product;

import com.smart.restaurant_saas.menu.product.dto.ProductAddOnRequest;
import com.smart.restaurant_saas.menu.product.dto.ProductAddOnResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu/products/{productId}/add-ons")
@RequiredArgsConstructor
@Tag(name = "Menu - Product Add-Ons", description = "Menu-side add-on suggestion links")
public class ProductAddOnController {

    private final ProductAddOnService addOnService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "List add-on links for a product")
    public List<ProductAddOnResponse> list(
            @PathVariable Long productId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return addOnService.findByProduct(productId, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(
        summary = "Link an add-on product",
        description = "Links a suggested add-on product to a parent-eligible host product."
    )
    public ResponseEntity<ProductAddOnResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody ProductAddOnRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(addOnService.create(productId, request.getAddOnProductId(), tenantId, userId));
    }

    @DeleteMapping("/{addOnProductId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(summary = "Remove an add-on link")
    public ResponseEntity<Void> delete(
            @PathVariable Long productId,
            @PathVariable Long addOnProductId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        addOnService.delete(productId, addOnProductId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
