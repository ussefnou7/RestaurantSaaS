package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceListItemResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.CreateAssetMaintenanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Asset Maintenance", description = "Maintenance cost records against a line")
public class AssetMaintenanceController {

    private final AssetMaintenanceService assetMaintenanceService;

    @GetMapping("/{assetId}/lines/{lineId}/maintenance")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List maintenance for a line",
        description = "Returns all maintenance cost records against the given asset line.")
    public List<AssetMaintenanceResponse> list(@PathVariable Long assetId,
                                               @PathVariable Long lineId,
                                               @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetMaintenanceService.findByLine(assetId, lineId, tenantId);
    }

    @GetMapping("/maintenance")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List all asset maintenance for the tenant",
        description = "Paginated, filterable flat list across all assets and lines.")
    public Page<AssetMaintenanceListItemResponse> listMaintenance(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Long assetLineId,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                @SortDefault(sort = "maintenanceDate", direction = Sort.Direction.DESC),
                @SortDefault(sort = "id", direction = Sort.Direction.DESC)
            })
            Pageable pageable) {
        return assetMaintenanceService.listMaintenance(tenantId, assetId, assetLineId, category,
            branchId, dateFrom, dateTo, pageable);
    }

    @PostMapping("/{assetId}/lines/{lineId}/maintenance")
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleMethodArgumentTypeMismatch() {
        return ResponseEntity.badRequest().build();
    }
}
