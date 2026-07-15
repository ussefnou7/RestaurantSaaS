package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.CreateAssetMaintenanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets/{assetId}/lines/{lineId}/maintenance")
@RequiredArgsConstructor
@Tag(name = "Asset Maintenance", description = "Maintenance cost records against a line")
public class AssetMaintenanceController {

    private final AssetMaintenanceService assetMaintenanceService;

    @GetMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List maintenance for a line",
        description = "Returns all maintenance cost records against the given asset line.")
    public List<AssetMaintenanceResponse> list(@PathVariable Long assetId,
                                               @PathVariable Long lineId,
                                               @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetMaintenanceService.findByLine(assetId, lineId, tenantId);
    }

    @PostMapping
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Record maintenance",
        description = "Records a maintenance cost against a line. Pure cost entry — it never changes "
            + "the line's quantity or remaining quantity (D49). The body carries assetId and "
            + "assetLineId which must match the URL and the line's parent (D51).")
    public ResponseEntity<AssetMaintenanceResponse> create(
            @PathVariable Long assetId,
            @PathVariable Long lineId,
            @Valid @RequestBody CreateAssetMaintenanceRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(assetMaintenanceService.create(assetId, lineId, request, tenantId, userId));
    }
}
