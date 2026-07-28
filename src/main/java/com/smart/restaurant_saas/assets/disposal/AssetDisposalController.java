package com.smart.restaurant_saas.assets.disposal;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalListItemResponse;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import com.smart.restaurant_saas.assets.disposal.dto.CreateAssetDisposalRequest;
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
@Tag(name = "Asset Disposals", description = "Disposal events that reduce a line's remaining quantity")
public class AssetDisposalController {

    private final AssetDisposalService assetDisposalService;

    @GetMapping("/{assetId}/lines/{lineId}/disposals")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List disposals for a line",
        description = "Returns all disposal events recorded against the given asset line.")
    public List<AssetDisposalResponse> list(@PathVariable Long assetId,
                                            @PathVariable Long lineId,
                                            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetDisposalService.findByLine(assetId, lineId, tenantId);
    }

    @GetMapping("/disposals")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "List all asset disposals for the tenant",
        description = "Paginated, filterable flat list across all assets and lines.")
    public Page<AssetDisposalListItemResponse> listDisposals(
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
                @SortDefault(sort = "disposalDate", direction = Sort.Direction.DESC),
                @SortDefault(sort = "id", direction = Sort.Direction.DESC)
            })
            Pageable pageable) {
        return assetDisposalService.listDisposals(tenantId, assetId, assetLineId, category,
            branchId, dateFrom, dateTo, pageable);
    }

    @PostMapping("/{assetId}/lines/{lineId}/disposals")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_MANAGE')")
    @Operation(summary = "Record a disposal",
        description = "Disposes part or all of a line's remaining quantity. The body carries assetId "
            + "and assetLineId which must match the URL and the line's parent (D51). Quantity is "
            + "capped at the line's remaining quantity (D48).")
    public ResponseEntity<AssetDisposalResponse> create(
            @PathVariable Long assetId,
            @PathVariable Long lineId,
            @Valid @RequestBody CreateAssetDisposalRequest request,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(assetDisposalService.create(assetId, lineId, request, tenantId, userId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Void> handleMethodArgumentTypeMismatch() {
        return ResponseEntity.badRequest().build();
    }
}
