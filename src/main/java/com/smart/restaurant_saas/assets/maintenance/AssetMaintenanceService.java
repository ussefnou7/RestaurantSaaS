package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.CreateAssetMaintenanceRequest;
import com.smart.restaurant_saas.assets.mapper.AssetMaintenanceMapper;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMaintenanceService {

    private final AssetMaintenanceRepository assetMaintenanceRepository;
    private final AssetLineRepository assetLineRepository;
    private final AssetMaintenanceMapper mapper;

    @Transactional(readOnly = true)
    public List<AssetMaintenanceResponse> findByLine(Long assetId, Long lineId, Long tenantId) {
        loadConsistentLine(assetId, lineId, assetId, lineId, tenantId);
        return assetMaintenanceRepository.findByTenantIdAndAssetLineIdOrderByIdDesc(tenantId, lineId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public AssetMaintenanceResponse create(Long assetId, Long lineId, CreateAssetMaintenanceRequest request,
                                           Long tenantId, Long userId) {
        loadConsistentLine(assetId, lineId, request.getAssetId(), request.getAssetLineId(), tenantId);

        // D49: maintenance is a cost record only — it never touches quantity or remainingQuantity.
        AssetMaintenance maintenance = new AssetMaintenance();
        maintenance.setTenantId(tenantId);
        maintenance.setAssetId(assetId);
        maintenance.setAssetLineId(lineId);
        maintenance.setCost(request.getCost());
        maintenance.setMaintenanceDate(request.getMaintenanceDate());
        maintenance.setDescription(request.getDescription());
        maintenance.setVendor(request.getVendor());
        maintenance.setCreatedBy(userId);
        AssetMaintenance saved = assetMaintenanceRepository.save(maintenance);
        log.info("Recorded maintenance id={} line={} asset={} tenant={}", saved.getId(), lineId, assetId, tenantId);
        return mapper.toResponse(saved);
    }

    /**
     * Loads the target line and enforces D51: the URL {@code assetId}/{@code lineId}, the body's
     * {@code assetId}/{@code assetLineId}, and the line's own {@code assetId} must all agree.
     */
    private AssetLine loadConsistentLine(Long pathAssetId, Long pathLineId, Long bodyAssetId,
                                         Long bodyLineId, Long tenantId) {
        if (!pathAssetId.equals(bodyAssetId) || !pathLineId.equals(bodyLineId)) {
            throw new BusinessException(AssetErrorCode.LINE_ASSET_MISMATCH,
                "Path and body asset/line identifiers do not match",
                ErrorParams.of("assetId", pathAssetId, "assetLineId", pathLineId,
                    "bodyAssetId", bodyAssetId, "bodyAssetLineId", bodyLineId));
        }
        AssetLine line = assetLineRepository.findByIdAndTenantId(pathLineId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "AssetLine not found: " + pathLineId,
                ErrorParams.of("entityType", "AssetLine", "entityId", pathLineId)));
        if (!line.getAssetId().equals(pathAssetId)) {
            throw new BusinessException(AssetErrorCode.LINE_ASSET_MISMATCH,
                "AssetLine does not belong to the given Asset",
                ErrorParams.of("assetId", pathAssetId, "assetLineId", pathLineId));
        }
        return line;
    }
}
