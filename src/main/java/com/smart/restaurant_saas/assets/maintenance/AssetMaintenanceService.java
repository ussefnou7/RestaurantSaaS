package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.auth.service.CurrentUserScopeProvider;
import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.AssetRepository;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceListItemResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import com.smart.restaurant_saas.assets.maintenance.dto.CreateAssetMaintenanceRequest;
import com.smart.restaurant_saas.assets.mapper.AssetMaintenanceMapper;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMaintenanceService {

    private final AssetMaintenanceRepository assetMaintenanceRepository;
    private final AssetLineRepository assetLineRepository;
    private final AssetRepository assetRepository;
    private final AssetMaintenanceMapper mapper;
    private final CurrentUserScopeProvider currentUserScopeProvider;
    private final TenantTimeZoneService timeZoneService;

    @Transactional(readOnly = true)
    public List<AssetMaintenanceResponse> findByLine(Long assetId, Long lineId, Long tenantId) {
        loadConsistentLine(assetId, lineId, assetId, lineId, tenantId);
        return assetMaintenanceRepository.findByTenantIdAndAssetLineIdOrderByIdDesc(tenantId, lineId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<AssetMaintenanceListItemResponse> listMaintenance(
            Long tenantId, Long assetId, Long assetLineId, AssetCategory category, Long branchId,
            LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        validateDateRange(dateFrom, dateTo);
        return assetMaintenanceRepository.findListItems(tenantId, assetId, assetLineId, category,
            currentUserScopeProvider.resolveBranchFilter(branchId), dateFrom, dateTo, pageable);
    }

    @Transactional
    public AssetMaintenanceResponse create(Long assetId, Long lineId, CreateAssetMaintenanceRequest request,
                                           Long tenantId, Long userId) {
        AssetLine line = loadConsistentLine(assetId, lineId, request.getAssetId(),
            request.getAssetLineId(), tenantId);
        Asset asset = requireAsset(assetId, tenantId);
        validateOperationDate(request.getMaintenanceDate(), "maintenanceDate", line, asset, tenantId);

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

    private Asset requireAsset(Long assetId, Long tenantId) {
        return assetRepository.findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "Asset not found: " + assetId,
                ErrorParams.of("entityType", "Asset", "entityId", assetId)));
    }

    private void validateOperationDate(LocalDate date, String field, AssetLine line, Asset asset,
                                       Long tenantId) {
        LocalDate today = LocalDate.now(timeZoneService.zoneFor(tenantId, asset.getBranchId()));
        if (date.isAfter(today)) {
            throw new ValidationException(AssetErrorCode.ASSET_DATE_IN_FUTURE,
                field + " must not be in the future",
                ErrorParams.of("field", field, "date", date, "maxDate", today));
        }
        if (date.isBefore(line.getPurchaseDate())) {
            throw new ValidationException(AssetErrorCode.ASSET_OPERATION_BEFORE_PURCHASE_DATE,
                field + " must not be before the asset line purchase date",
                ErrorParams.of("field", field, "date", date,
                    "purchaseDate", line.getPurchaseDate(), "assetLineId", line.getId()));
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ValidationException(AssetErrorCode.INVALID_DATE_RANGE,
                "dateFrom must not be after dateTo",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
    }
}
