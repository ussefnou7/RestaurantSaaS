package com.smart.restaurant_saas.assets.asset;

import com.smart.restaurant_saas.assets.asset.dto.AssetResponse;
import com.smart.restaurant_saas.assets.asset.dto.CreateAssetRequest;
import com.smart.restaurant_saas.assets.asset.dto.UpdateAssetRequest;
import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.AssetLineRepository;
import com.smart.restaurant_saas.assets.core.AssetErrorCode;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import com.smart.restaurant_saas.assets.mapper.AssetMapper;
import com.smart.restaurant_saas.branch.BranchRepository;
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
public class AssetService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final AssetRepository assetRepository;
    private final AssetLineRepository assetLineRepository;
    private final BranchRepository branchRepository;
    private final AssetMapper mapper;

    @Transactional(readOnly = true)
    public List<AssetResponse> findAll(Long tenantId) {
        return assetRepository.findByTenantIdOrderByIdDesc(tenantId).stream()
            .map(a -> toResponse(a, tenantId))
            .toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse findById(Long id, Long tenantId) {
        return toResponse(loadOwned(id, tenantId), tenantId);
    }

    @Transactional
    public AssetResponse create(CreateAssetRequest request, Long tenantId) {
        validateBranch(request.getBranchId(), tenantId);
        Asset asset = new Asset();
        asset.setTenantId(tenantId);
        asset.setBranchId(request.getBranchId());
        asset.setName(request.getName());
        asset.setNameAr(request.getNameAr());
        asset.setCategory(request.getCategory());
        asset.setStatus(AssetStatus.ACTIVE);
        Asset saved = assetRepository.save(asset);
        log.info("Created asset id={} tenant={} branch={}", saved.getId(), tenantId, request.getBranchId());
        return toResponse(saved, tenantId);
    }

    @Transactional
    public AssetResponse update(Long id, UpdateAssetRequest request, Long tenantId) {
        Asset asset = loadOwned(id, tenantId);
        asset.setName(request.getName());
        asset.setNameAr(request.getNameAr());
        asset.setCategory(request.getCategory());
        return toResponse(assetRepository.save(asset), tenantId);
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        Asset asset = loadOwned(id, tenantId);
        long lineCount = assetLineRepository.countByTenantIdAndAssetId(tenantId, id);
        if (lineCount > 0) {
            // D50: an Asset may be deleted only when it has zero AssetLine rows.
            throw new BusinessException(AssetErrorCode.ASSET_HAS_LINES,
                "Asset cannot be deleted while it has lines",
                ErrorParams.of("assetId", id, "lineCount", lineCount));
        }
        assetRepository.delete(asset);
        log.info("Deleted asset id={} tenant={}", id, tenantId);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private AssetResponse toResponse(Asset asset, Long tenantId) {
        List<AssetLine> lines = assetLineRepository.findByTenantIdAndAssetIdOrderByIdAsc(tenantId, asset.getId());
        BigDecimal totalCurrentValue = lines.stream()
            .map(l -> l.getRemainingQuantity().multiply(l.getUnitCost()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, ROUNDING);
        return mapper.toResponse(asset, lines.size(), totalCurrentValue);
    }

    private void validateBranch(Long branchId, Long tenantId) {
        branchRepository.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "Branch not found: " + branchId,
                ErrorParams.of("entityType", "Branch", "entityId", branchId)));
    }

    private Asset loadOwned(Long id, Long tenantId) {
        return assetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(AssetErrorCode.RESOURCE_NOT_FOUND,
                "Asset not found: " + id,
                ErrorParams.of("entityType", "Asset", "entityId", id)));
    }
}
