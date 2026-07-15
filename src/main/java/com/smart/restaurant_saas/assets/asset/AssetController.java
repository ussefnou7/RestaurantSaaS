package com.smart.restaurant_saas.assets.asset;

import com.smart.restaurant_saas.assets.asset.dto.AssetResponse;
import com.smart.restaurant_saas.assets.asset.dto.CreateAssetRequest;
import com.smart.restaurant_saas.assets.asset.dto.UpdateAssetRequest;
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
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Fixed asset headers (item types)")
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List assets",
        description = "Returns all fixed-asset headers for the current tenant, newest first, with "
            + "derived line count and current value.")
    public List<AssetResponse> list(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetService.findAll(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "Get asset by ID",
        description = "Returns a single asset header with its derived status, line count, and "
            + "current value.")
    public AssetResponse getById(@PathVariable Long id,
                                 @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetService.findById(id, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Create asset",
        description = "Creates a new asset header for a branch. Status starts ACTIVE; purchase "
            + "batches are added as asset lines.")
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody CreateAssetRequest request,
                                                 @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.create(request, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Update asset",
        description = "Updates the asset name, Arabic name, and category. Branch and status are not "
            + "editable here (status is derived).")
    public AssetResponse update(@PathVariable Long id,
                                @Valid @RequestBody UpdateAssetRequest request,
                                @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetService.update(id, request, tenantId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Delete asset",
        description = "Deletes an asset header. Allowed only when it has zero asset lines (D50).")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader("X-Tenant-Id") Long tenantId) {
        assetService.delete(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
