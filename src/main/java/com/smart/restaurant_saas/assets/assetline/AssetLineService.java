package com.smart.restaurant_saas.assets.assetline;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.dto.AssetLineResponse;
import com.smart.restaurant_saas.assets.assetline.dto.CreateAssetLineRequest;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.AssetStatusService;
import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import com.smart.restaurant_saas.assets.disposal.AssetDisposalRepository;
import com.smart.restaurant_saas.assets.maintenance.AssetMaintenanceRepository;
import com.smart.restaurant_saas.assets.mapper.AssetLineMapper;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLineService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final AssetLineRepository assetLineRepository;
    private final AssetRepository assetRepository;
    private final AssetDisposalRepository assetDisposalRepository;
    private final AssetMaintenanceRepository assetMaintenanceRepository;
    private final AssetStatusService statusService;
    private final AssetLineMapper mapper;

    @Transactional(readOnly = true)
    public List<AssetLineResponse> findByAsset(Long assetId, Long tenantId) {
        requireAsset(assetId, tenantId);
        return assetLineRepository.findByTenantIdAndAssetIdOrderByIdAsc(tenantId, assetId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AssetLineResponse findByAssetAndId(Long assetId, Long lineId, Long tenantId) {
        return mapper.toResponse(loadOwnedUnderAsset(assetId, lineId, tenantId));
    }

    @Transactional
    public AssetLineResponse create(Long assetId, CreateAssetLineRequest request, Long tenantId) {
        requireAsset(assetId, tenantId);
        AssetLine line = new AssetLine();
        line.setTenantId(tenantId);
        line.setAssetId(assetId);
        line.setLabel(request.getLabel());
        line.setQuantity(request.getQuantity());
        line.setRemainingQuantity(request.getQuantity());
        line.setUnitCost(request.getUnitCost());
        line.setTotalCost(request.getQuantity().multiply(request.getUnitCost()).setScale(SCALE, ROUNDING));
        line.setPurchaseDate(request.getPurchaseDate());
        line.setStatus(AssetLineStatus.ACTIVE);
        AssetLine saved = assetLineRepository.save(line);
        // A new line changes the header's aggregation set, so recompute the parent asset status.
        statusService.recalculateAsset(tenantId, assetId);
        log.info("Created asset line id={} asset={} tenant={}", saved.getId(), assetId, tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long assetId, Long lineId, Long tenantId) {
        AssetLine line = loadOwnedUnderAsset(assetId, lineId, tenantId);
        long disposals = assetDisposalRepository.countByTenantIdAndAssetLineId(tenantId, lineId);
        long maintenance = assetMaintenanceRepository.countByTenantIdAndAssetLineId(tenantId, lineId);
        if (disposals > 0 || maintenance > 0) {
            // D50: a line may be deleted only if it has zero disposal and zero maintenance records.
            throw new BusinessException(AssetErrorCode.LINE_HAS_CHILD_RECORDS,
                "AssetLine cannot be deleted while it has disposal or maintenance records",
                ErrorParams.of("assetLineId", lineId, "disposalCount", disposals,
                    "maintenanceCount", maintenance));
        }
        assetLineRepository.delete(line);
        // Removing a line changes the header's aggregation set, so recompute the parent asset status.
        statusService.recalculateAsset(tenantId, assetId);
        log.info("Deleted asset line id={} asset={} tenant={}", lineId, assetId, tenantId);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private Asset requireAsset(Long assetId, Long tenantId) {
        return assetRepository.findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "Asset not found: " + assetId,
                ErrorParams.of("entityType", "Asset", "entityId", assetId)));
    }

    private AssetLine loadOwnedUnderAsset(Long assetId, Long lineId, Long tenantId) {
        AssetLine line = assetLineRepository.findByIdAndTenantId(lineId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "AssetLine not found: " + lineId,
                ErrorParams.of("entityType", "AssetLine", "entityId", lineId)));
        if (!line.getAssetId().equals(assetId)) {
            throw new BusinessException(AssetErrorCode.LINE_ASSET_MISMATCH,
                "AssetLine does not belong to the given Asset",
                ErrorParams.of("assetId", assetId, "assetLineId", lineId));
        }
        return line;
    }
}
