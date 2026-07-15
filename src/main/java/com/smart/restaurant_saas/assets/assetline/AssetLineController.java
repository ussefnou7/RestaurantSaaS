package com.smart.restaurant_saas.assets.assetline;

import com.smart.restaurant_saas.assets.assetline.dto.AssetLineResponse;
import com.smart.restaurant_saas.assets.assetline.dto.CreateAssetLineRequest;
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
@RequestMapping("/api/assets/{assetId}/lines")
@RequiredArgsConstructor
@Tag(name = "Asset Lines", description = "Per-purchase-batch lines under an asset")
public class AssetLineController {

    private final AssetLineService assetLineService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List asset lines",
        description = "Returns all purchase-batch lines under the given asset.")
    public List<AssetLineResponse> list(@PathVariable Long assetId,
                                        @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetLineService.findByAsset(assetId, tenantId);
    }

    @GetMapping("/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "Get asset line by ID",
        description = "Returns a single line under the given asset. Fails if the line does not "
            + "belong to the asset.")
    public AssetLineResponse getById(@PathVariable Long assetId,
                                     @PathVariable Long lineId,
                                     @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetLineService.findByAssetAndId(assetId, lineId, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Create asset line",
        description = "Adds a purchase batch under the asset. Total cost is computed as "
            + "quantity * unitCost; remaining quantity starts equal to quantity.")
    public ResponseEntity<AssetLineResponse> create(@PathVariable Long assetId,
                                                     @Valid @RequestBody CreateAssetLineRequest request,
                                                     @RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(assetLineService.create(assetId, request, tenantId));
    }

    @DeleteMapping("/{lineId}")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Delete asset line",
        description = "Deletes a line. Allowed only when it has zero disposal and zero maintenance "
            + "records (D50).")
    public ResponseEntity<Void> delete(@PathVariable Long assetId,
                                       @PathVariable Long lineId,
                                       @RequestHeader("X-Tenant-Id") Long tenantId) {
        assetLineService.delete(assetId, lineId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
