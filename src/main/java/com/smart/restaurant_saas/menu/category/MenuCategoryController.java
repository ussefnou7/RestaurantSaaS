package com.smart.restaurant_saas.menu.category;

import com.smart.restaurant_saas.menu.category.dto.MenuCategoryRequest;
import com.smart.restaurant_saas.menu.category.dto.MenuCategoryResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu/categories")
@RequiredArgsConstructor
@Tag(name = "Menu - Categories", description = "Tenant menu category management")
public class MenuCategoryController {

    private final MenuCategoryService categoryService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "List menu categories")
    public List<MenuCategoryResponse> list(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return categoryService.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_VIEW')")
    @Operation(summary = "Get a menu category")
    public MenuCategoryResponse getById(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return categoryService.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_CREATE')")
    @Operation(summary = "Create a menu category")
    public ResponseEntity<MenuCategoryResponse> create(
            @Valid @RequestBody MenuCategoryRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(categoryService.create(request, tenantId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_UPDATE')")
    @Operation(summary = "Update a menu category")
    public MenuCategoryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MenuCategoryRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return categoryService.update(id, request, tenantId, userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('PRODUCTS_DELETE')")
    @Operation(
        summary = "Delete a menu category",
        description = "Deletes a category only when no products reference it."
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        categoryService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
