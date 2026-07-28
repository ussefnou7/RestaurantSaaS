package com.smart.restaurant_saas.assets.disposal;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.AssetStatusService;
import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalListItemResponse;
import com.smart.restaurant_saas.assets.disposal.dto.CreateAssetDisposalRequest;
import com.smart.restaurant_saas.assets.mapper.AssetDisposalMapper;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
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
public class AssetDisposalService {

    private final AssetDisposalRepository assetDisposalRepository;
    private final AssetLineRepository assetLineRepository;
    private final AssetStatusService statusService;
    private final AssetDisposalMapper mapper;

    @Transactional(readOnly = true)
    public List<AssetDisposalResponse> findByLine(Long assetId, Long lineId, Long tenantId) {
        loadConsistentLine(assetId, lineId, assetId, lineId, tenantId);
        return assetDisposalRepository.findByTenantIdAndAssetLineIdOrderByIdDesc(tenantId, lineId).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<AssetDisposalListItemResponse> listDisposals(
            Long tenantId, Long assetId, Long assetLineId, AssetCategory category, Long branchId,
            LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        validateDateRange(dateFrom, dateTo);
        return assetDisposalRepository.findListItems(tenantId, assetId, assetLineId, category,
            branchId, dateFrom, dateTo, pageable);
    }

    @Transactional
    public AssetDisposalResponse create(Long assetId, Long lineId, CreateAssetDisposalRequest request,
                                        Long tenantId, Long userId) {
        AssetLine line = loadConsistentLine(assetId, lineId, request.getAssetId(),
            request.getAssetLineId(), tenantId);

        // D48: quantityDisposed is capped at the line's current remaining; never go negative.
        if (request.getQuantityDisposed().compareTo(line.getRemainingQuantity()) > 0) {
            throw new BusinessException(AssetErrorCode.DISPOSAL_EXCEEDS_REMAINING,
                "quantityDisposed exceeds the line's remaining quantity",
                ErrorParams.of("assetLineId", lineId,
                    "quantityDisposed", request.getQuantityDisposed(),
                    "remainingQuantity", line.getRemainingQuantity()));
        }

        line.setRemainingQuantity(line.getRemainingQuantity().subtract(request.getQuantityDisposed()));
        statusService.recalculateLineAndAsset(line);

        AssetDisposal disposal = new AssetDisposal();
        disposal.setTenantId(tenantId);
        disposal.setAssetId(assetId);
        disposal.setAssetLineId(lineId);
        disposal.setQuantityDisposed(request.getQuantityDisposed());
        disposal.setReason(request.getReason());
        disposal.setDisposalDate(request.getDisposalDate());
        disposal.setNotes(request.getNotes());
        disposal.setCreatedBy(userId);
        AssetDisposal saved = assetDisposalRepository.save(disposal);
        log.info("Recorded disposal id={} line={} asset={} tenant={}", saved.getId(), lineId, assetId, tenantId);
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

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ValidationException(AssetErrorCode.INVALID_DATE_RANGE,
                "dateFrom must not be after dateTo",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
    }
}
